import { getGenreApi } from "@jellyfin/sdk/lib/utils/api/genre-api";
import { getLibraryApi } from "@jellyfin/sdk/lib/utils/api/library-api";
import { getShowApi } from "@jellyfin/sdk/lib/utils/api/show-api";
import {
	keepPreviousData,
	useInfiniteQuery,
	useQuery,
} from "@tanstack/react-query";
import { useSession } from "../auth/AuthProvider";
import { api } from "./jellyfin";
import {
	buildItemsQuery,
	LIBRARY_COPY,
	type LibraryKind,
	type LibraryPrefs,
	type ShelfSpec,
} from "./library";

/**
 * Items for the hero carousel: the newest titles that have a backdrop and a description.
 * Home mixes movies and shows; a library or genre page narrows it down.
 */
export function useFeaturedItems(
	options: { kind?: LibraryKind; genre?: string } = {},
) {
	const { userId } = useSession();
	const { kind, genre } = options;
	return useQuery({
		queryKey: ["home", userId, "featured", kind ?? "all", genre ?? ""],
		queryFn: async ({ signal }) => {
			const { data } = await getLibraryApi(api).getItems(
				{
					userId,
					includeItemTypes: kind
						? [LIBRARY_COPY[kind].itemType]
						: ["Movie", "Series"],
					genres: genre ? [genre] : undefined,
					recursive: true,
					sortBy: ["DateCreated"],
					sortOrder: ["Descending"],
					imageTypes: ["Backdrop"],
					fields: ["Overview", "Genres", "ChildCount"],
					enableImageTypes: ["Backdrop", "Logo", "Primary"],
					imageTypeLimit: 1,
					enableUserData: true,
					limit: 30,
				},
				{ signal },
			);
			return (data.Items ?? [])
				.filter((item) => item.Id && item.Overview)
				.slice(0, 5);
		},
	});
}

/** Partly watched movies and episodes. */
export function useResumeItems() {
	const { userId } = useSession();
	return useQuery({
		queryKey: ["home", userId, "resume"],
		queryFn: async ({ signal }) => {
			const { data } = await getLibraryApi(api).getResumeItems(
				{
					userId,
					limit: 16,
					mediaTypes: ["Video"],
					includeItemTypes: ["Movie", "Episode"],
					enableUserData: true,
					imageTypeLimit: 1,
					enableImageTypes: ["Primary", "Backdrop", "Thumb"],
				},
				{ signal },
			);
			return data.Items ?? [];
		},
	});
}

/** The next unwatched episode of each show you're following. */
export function useNextUpItems() {
	const { userId } = useSession();
	return useQuery({
		queryKey: ["home", userId, "next-up"],
		queryFn: async ({ signal }) => {
			const { data } = await getShowApi(api).getNextUp(
				{
					userId,
					limit: 16,
					enableUserData: true,
					// Partly watched episodes already appear under Continue Watching.
					enableResumable: false,
					imageTypeLimit: 1,
					enableImageTypes: ["Primary", "Backdrop", "Thumb"],
				},
				{ signal },
			);
			return data.Items ?? [];
		},
	});
}

/** Recently added movies and shows (new episodes are grouped under their show). */
export function useLatestItems() {
	const { userId } = useSession();
	return useQuery({
		queryKey: ["home", userId, "latest"],
		queryFn: async ({ signal }) => {
			const { data } = await getLibraryApi(api).getLatestMedia(
				{
					userId,
					limit: 20,
					includeItemTypes: ["Movie", "Series"],
					enableUserData: true,
					imageTypeLimit: 1,
					enableImageTypes: ["Primary"],
				},
				{ signal },
			);
			return data;
		},
	});
}

const PAGE_SIZE = 60;

/** A library grid, loaded a page at a time as the user scrolls. */
export function useLibraryItems(kind: LibraryKind, prefs: LibraryPrefs) {
	const { userId } = useSession();
	return useInfiniteQuery({
		queryKey: ["library", userId, kind, prefs],
		initialPageParam: 0,
		queryFn: async ({ pageParam, signal }) => {
			const { data } = await getLibraryApi(api).getItems(
				{
					...buildItemsQuery(kind, prefs),
					userId,
					startIndex: pageParam,
					limit: PAGE_SIZE,
					enableTotalRecordCount: true,
					enableUserData: true,
					imageTypeLimit: 1,
					enableImageTypes: ["Primary"],
				},
				{ signal },
			);
			const items = data.Items ?? [];
			return {
				items,
				total: data.TotalRecordCount ?? items.length,
				nextStart: pageParam + items.length,
			};
		},
		getNextPageParam: (last) =>
			last.items.length > 0 && last.nextStart < last.total
				? last.nextStart
				: undefined,
		// Keep showing the old grid (dimmed) while a new sort or filter loads.
		placeholderData: keepPreviousData,
	});
}

/** Genre names for the filter menu. */
export function useGenres(kind: LibraryKind) {
	const { userId } = useSession();
	return useQuery({
		queryKey: ["genres", userId, kind],
		staleTime: 5 * 60_000,
		queryFn: async ({ signal }) => {
			const { data } = await getGenreApi(api).getGenres(
				{
					userId,
					includeItemTypes: [LIBRARY_COPY[kind].itemType],
					sortBy: ["SortName"],
				},
				{ signal },
			);
			return (data.Items ?? [])
				.map((genre) => genre.Name)
				.filter((name): name is string => Boolean(name));
		},
	});
}

/** One browse-page row, e.g. "New Releases", optionally within a single genre. */
export function useShelf(kind: LibraryKind, spec: ShelfSpec, genre?: string) {
	const { userId } = useSession();
	return useQuery({
		queryKey: [
			"shelf",
			userId,
			kind,
			spec.sortBy,
			spec.descending,
			genre ?? "",
		],
		queryFn: async ({ signal }) => {
			const { data } = await getLibraryApi(api).getItems(
				{
					userId,
					includeItemTypes: [LIBRARY_COPY[kind].itemType],
					genres: genre ? [genre] : undefined,
					recursive: true,
					sortBy: [spec.sortBy, "SortName"],
					sortOrder: [
						spec.descending ? "Descending" : "Ascending",
						"Ascending",
					],
					maxPremiereDate: spec.releasedOnly
						? new Date().toISOString()
						: undefined,
					limit: 24,
					enableUserData: true,
					imageTypeLimit: 1,
					enableImageTypes: ["Primary"],
				},
				{ signal },
			);
			return data.Items ?? [];
		},
	});
}

/** Movies and shows matching a search term. Keeps the last results on screen while the next ones load. */
export function useSearchResults(term: string) {
	const { userId } = useSession();
	const trimmed = term.trim();
	return useQuery({
		queryKey: ["search", userId, trimmed],
		enabled: trimmed.length > 0,
		placeholderData: keepPreviousData,
		queryFn: async ({ signal }) => {
			const { data } = await getLibraryApi(api).getItems(
				{
					userId,
					searchTerm: trimmed,
					includeItemTypes: ["Movie", "Series"],
					recursive: true,
					fields: ["ChildCount"],
					enableImageTypes: ["Primary"],
					imageTypeLimit: 1,
					enableUserData: true,
					limit: 60,
				},
				{ signal },
			);
			const items = (data.Items ?? []).filter((item) => item.Id);
			return {
				movies: items.filter((item) => item.Type === "Movie"),
				shows: items.filter((item) => item.Type === "Series"),
			};
		},
	});
}
