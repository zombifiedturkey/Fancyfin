import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { getLibraryApi } from "@jellyfin/sdk/lib/utils/api/library-api";
import {
	type QueryClient,
	useQuery,
	useQueryClient,
} from "@tanstack/react-query";
import { useSession } from "../auth/AuthProvider";
import { useArrAvailability } from "./arr-queries";
import {
	buildFeatured,
	type FeaturedEntry,
	interleave,
	type PopularMatch,
} from "./featured";
import { api } from "./jellyfin";
import type { LibraryKind } from "./library";
import { fetchRecentFeatured } from "./queries";
import { useSettings } from "./settings";
import {
	fetchGenres,
	fetchPopularInGenres,
	fetchTrending,
	matchGenreIds,
	type TmdbKind,
	type TmdbTitle,
	useRuntimeConfig,
} from "./tmdb";

const POPULAR_CANDIDATES = 14;

/** TMDB id to library item id, for every title of one type in the library. */
async function fetchTmdbIndex(
	userId: string,
	itemType: "Movie" | "Series",
	signal?: AbortSignal,
): Promise<Map<number, string>> {
	const { data } = await getLibraryApi(api).getItems(
		{
			userId,
			includeItemTypes: [itemType],
			recursive: true,
			fields: ["ProviderIds"],
			enableUserData: false,
			imageTypeLimit: 1,
			enableImageTypes: ["Primary"],
		},
		{ signal },
	);
	const index = new Map<number, string>();
	for (const item of data.Items ?? []) {
		const tmdbId = Number.parseInt(item.ProviderIds?.Tmdb ?? "", 10);
		if (item.Id && !Number.isNaN(tmdbId)) index.set(tmdbId, item.Id);
	}
	return index;
}

/** Library items with everything the banner shows. Keyed by id. */
async function fetchOwned(
	userId: string,
	ids: string[],
	signal?: AbortSignal,
): Promise<Map<string, BaseItemDto>> {
	if (ids.length === 0) return new Map();
	const { data } = await getLibraryApi(api).getItems(
		{
			userId,
			ids,
			fields: ["Overview", "Genres", "ChildCount"],
			enableImageTypes: ["Backdrop", "Logo", "Primary"],
			imageTypeLimit: 1,
			enableUserData: true,
		},
		{ signal },
	);
	const items = new Map<string, BaseItemDto>();
	for (const item of data.Items ?? []) {
		if (item.Id && item.Overview && (item.BackdropImageTags?.length ?? 0) > 0) {
			items.set(item.Id, item);
		}
	}
	return items;
}

/** This week's trending titles, one list per kind. Cached, so the banner and Discover share them. */
function loadTrending(
	queryClient: QueryClient,
	kinds: TmdbKind[],
): Promise<TmdbTitle[][]> {
	return Promise.all(
		kinds.map((tmdbKind) =>
			queryClient.fetchQuery({
				queryKey: ["tmdb", "trending", tmdbKind],
				staleTime: 30 * 60_000,
				queryFn: ({ signal: s }) => fetchTrending(tmdbKind, s),
			}),
		),
	);
}

/** What the library has, as a TMDB id index per kind. */
export async function loadIndexes(
	queryClient: QueryClient,
	userId: string,
	kinds: TmdbKind[],
): Promise<Map<TmdbKind, Map<number, string>>> {
	const indexes = new Map<TmdbKind, Map<number, string>>();
	await Promise.all(
		kinds.map(async (tmdbKind) => {
			const itemType = tmdbKind === "movie" ? "Movie" : "Series";
			indexes.set(
				tmdbKind,
				await queryClient.fetchQuery({
					queryKey: ["tmdb-index", userId, itemType],
					staleTime: 10 * 60_000,
					queryFn: ({ signal: s }) => fetchTmdbIndex(userId, itemType, s),
				}),
			);
		}),
	);
	return indexes;
}

/** The most popular titles in a genre, one list per kind. A genre TMDB has no match for gives []. */
function loadGenrePopular(
	queryClient: QueryClient,
	kinds: TmdbKind[],
	genre: string,
): Promise<TmdbTitle[][]> {
	return Promise.all(
		kinds.map(async (tmdbKind) => {
			const genres = await queryClient.fetchQuery({
				queryKey: ["tmdb", "genres", tmdbKind],
				staleTime: 24 * 60 * 60_000,
				queryFn: ({ signal: s }) => fetchGenres(tmdbKind, s),
			});
			const ids = matchGenreIds(genre, genres);
			if (ids.length === 0) return [];
			return queryClient.fetchQuery({
				queryKey: ["tmdb", "genre-popular", tmdbKind, ids.join(",")],
				staleTime: 30 * 60_000,
				queryFn: ({ signal: s }) => fetchPopularInGenres(tmdbKind, ids, s),
			});
		}),
	);
}

/**
 * Popular titles from TMDB, each paired with the library's copy when there is one: this week's
 * trending, or, with a genre, the most popular in that genre.
 */
async function fetchPopular(
	queryClient: QueryClient,
	userId: string,
	kind: LibraryKind | undefined,
	genre: string | undefined,
	signal?: AbortSignal,
): Promise<PopularMatch[]> {
	const kinds: TmdbKind[] =
		kind === "movies" ? ["movie"] : kind === "shows" ? ["tv"] : ["movie", "tv"];

	const lists = genre
		? await loadGenrePopular(queryClient, kinds, genre)
		: await loadTrending(queryClient, kinds);
	const titles: TmdbTitle[] = (
		lists.length === 2
			? interleave(lists[0] ?? [], lists[1] ?? [])
			: (lists[0] ?? [])
	).slice(0, POPULAR_CANDIDATES);

	const indexes = await loadIndexes(queryClient, userId, kinds);

	const itemIds = titles.flatMap((title) => {
		const id = indexes.get(title.kind)?.get(title.id);
		return id ? [id] : [];
	});
	const owned = await fetchOwned(userId, itemIds, signal);

	return titles.flatMap((title): PopularMatch[] => {
		const id = indexes.get(title.kind)?.get(title.id);
		if (!id) return [{ title }];
		const item = owned.get(id);
		// In the library but without a picture or description: nothing good to show, so skip it.
		return item ? [{ title, owned: item }] : [];
	});
}

/**
 * The slides for the featured banner, following the viewer's settings: recently added, popular
 * (from TMDB, if the server has a key), or both. On a genre page, "popular" means the most popular
 * titles in that genre, whether or not the library has them.
 */
export function useFeatured(
	options: { kind?: LibraryKind; genre?: string } = {},
) {
	const { userId } = useSession();
	const queryClient = useQueryClient();
	const config = useRuntimeConfig();
	const settings = useSettings();
	const { kind, genre } = options;

	const tmdbOn = config.data?.tmdb ?? false;
	const source = tmdbOn ? settings.featuredSource : "recent";
	const discover = settings.featuredDiscover;

	return useQuery({
		queryKey: [
			"home",
			userId,
			"featured",
			kind ?? "all",
			genre ?? "",
			source,
			discover,
		],
		// Wait to hear whether TMDB is set up, so the banner doesn't change after it appears.
		enabled: !config.isPending,
		queryFn: async ({ signal }): Promise<FeaturedEntry[]> => {
			const recent = await fetchRecentFeatured(userId, { kind, genre }, signal);
			let popular: PopularMatch[] = [];
			if (source !== "recent") {
				try {
					popular = await fetchPopular(queryClient, userId, kind, genre, signal);
				} catch {
					// TMDB unreachable or the key was refused: fall back to recently added.
				}
			}
			return buildFeatured({ source, discover, recent, popular });
		},
	});
}

const DISCOVER_LIMIT = 20;

/**
 * Popular titles the library doesn't have, for the Discover row. Movies only count when Radarr can
 * add them, shows only when Sonarr can.
 */
export function useDiscover() {
	const { userId } = useSession();
	const queryClient = useQueryClient();
	const available = useArrAvailability();

	const kinds: TmdbKind[] = [];
	if (available.radarr) kinds.push("movie");
	if (available.sonarr) kinds.push("tv");

	return useQuery({
		queryKey: ["home", userId, "discover", kinds.join(",")],
		enabled: kinds.length > 0,
		queryFn: async (): Promise<TmdbTitle[]> => {
			const [lists, indexes] = await Promise.all([
				loadTrending(queryClient, kinds),
				loadIndexes(queryClient, userId, kinds),
			]);
			const titles =
				lists.length === 2
					? interleave(lists[0] ?? [], lists[1] ?? [])
					: (lists[0] ?? []);
			return titles
				.filter(
					(title) => title.poster && !indexes.get(title.kind)?.has(title.id),
				)
				.slice(0, DISCOVER_LIMIT);
		},
	});
}
