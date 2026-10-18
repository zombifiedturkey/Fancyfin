import { useQuery } from "@tanstack/react-query";
import { loadRuntimeConfig } from "./runtime-config";

export type TmdbKind = "movie" | "tv";

/** A title from TMDB's popularity charts. Only what the featured banner needs. */
export type TmdbTitle = {
	id: number;
	kind: TmdbKind;
	title: string;
	overview: string;
	/** TMDB image path, like "/abc.jpg". Empty for search results that have none. */
	backdrop: string;
	/** TMDB image path of the poster, when there is one. */
	poster?: string;
	year?: number;
	rating?: number;
	popularity: number;
};

function text(value: unknown): string {
	return typeof value === "string" ? value.trim() : "";
}

/** Read TMDB's trending response. Titles without a picture or a description are left out. */
export function parseTrending(kind: TmdbKind, json: unknown): TmdbTitle[] {
	const results = (json as { results?: unknown } | null)?.results;
	if (!Array.isArray(results)) return [];

	const titles: TmdbTitle[] = [];
	for (const raw of results) {
		if (typeof raw !== "object" || raw === null) continue;
		const row = raw as Record<string, unknown>;
		const id = row.id;
		const title = text(kind === "movie" ? row.title : row.name);
		const backdrop = text(row.backdrop_path);
		const overview = text(row.overview);
		if (typeof id !== "number" || !title || !backdrop || !overview) continue;

		const poster = text(row.poster_path);

		const date = text(kind === "movie" ? row.release_date : row.first_air_date);
		const year = Number.parseInt(date.slice(0, 4), 10);
		const rating = typeof row.vote_average === "number" ? row.vote_average : 0;
		titles.push({
			id,
			kind,
			title,
			overview,
			backdrop,
			poster: poster || undefined,
			year: Number.isNaN(year) ? undefined : year,
			rating: rating > 0 ? rating : undefined,
			popularity: typeof row.popularity === "number" ? row.popularity : 0,
		});
	}
	return titles;
}

/** Read TMDB's multi-search. Movies and shows only (not people); a picture is not required here. */
export function parseSearch(json: unknown): TmdbTitle[] {
	const results = (json as { results?: unknown } | null)?.results;
	if (!Array.isArray(results)) return [];

	const titles: TmdbTitle[] = [];
	for (const raw of results) {
		if (typeof raw !== "object" || raw === null) continue;
		const row = raw as Record<string, unknown>;
		const kind: TmdbKind | undefined =
			row.media_type === "movie"
				? "movie"
				: row.media_type === "tv"
					? "tv"
					: undefined;
		if (!kind || typeof row.id !== "number") continue;
		const title = text(kind === "movie" ? row.title : row.name);
		if (!title) continue;

		const date = text(kind === "movie" ? row.release_date : row.first_air_date);
		const year = Number.parseInt(date.slice(0, 4), 10);
		titles.push({
			id: row.id,
			kind,
			title,
			overview: text(row.overview),
			backdrop: text(row.backdrop_path),
			poster: text(row.poster_path) || undefined,
			year: Number.isNaN(year) ? undefined : year,
			popularity: typeof row.popularity === "number" ? row.popularity : 0,
		});
	}
	return titles;
}

/** TMDB's image CDN. These need no key, so the browser loads them directly. */
export function tmdbImage(
	path: string,
	size: "w342" | "w500" | "w780" | "w1280",
): string {
	return `https://image.tmdb.org/t/p/${size}${path}`;
}

export function tmdbPage(title: TmdbTitle): string {
	return `https://www.themoviedb.org/${title.kind}/${title.id}`;
}

/** Search TMDB for movies and shows by name, through the server's /tmdb proxy. */
export async function fetchSearch(
	term: string,
	signal?: AbortSignal,
): Promise<TmdbTitle[]> {
	const response = await fetch(
		`/tmdb/search/multi?query=${encodeURIComponent(term)}&include_adult=false&language=en-US`,
		{ signal },
	);
	if (!response.ok) throw new Error(`TMDB ${response.status}`);
	return parseSearch(await response.json());
}

/** The TVDB id of a TMDB show (Sonarr finds shows by it), from /tv/{id}/external_ids. */
export function parseTvdbId(json: unknown): number | undefined {
	const id = (json as { tvdb_id?: unknown } | null)?.tvdb_id;
	return typeof id === "number" && id > 0 ? id : undefined;
}

/** Ask TMDB, through the server, which TVDB id a show has. */
export async function fetchTvdbId(
	tmdbId: number,
	signal?: AbortSignal,
): Promise<number | undefined> {
	const response = await fetch(`/tmdb/tv/${tmdbId}/external_ids`, { signal });
	if (!response.ok) throw new Error(`TMDB ${response.status}`);
	return parseTvdbId(await response.json());
}

/** What's trending this week, through the server's /tmdb proxy (which holds the API key). */
export async function fetchTrending(
	kind: TmdbKind,
	signal?: AbortSignal,
): Promise<TmdbTitle[]> {
	const response = await fetch(`/tmdb/trending/${kind}/week?language=en-US`, {
		signal,
	});
	if (!response.ok) throw new Error(`TMDB ${response.status}`);
	return parseTrending(kind, await response.json());
}

/** A TMDB genre, like { id: 28, name: "Action" }. */
export type TmdbGenre = { id: number; name: string };

export function parseGenres(json: unknown): TmdbGenre[] {
	const genres = (json as { genres?: unknown } | null)?.genres;
	if (!Array.isArray(genres)) return [];
	return genres.flatMap((raw): TmdbGenre[] => {
		const row = raw as { id?: unknown; name?: unknown } | null;
		return typeof row?.id === "number" && typeof row.name === "string"
			? [{ id: row.id, name: row.name }]
			: [];
	});
}

/** Lower-case, with the spellings Jellyfin and TMDB differ on made the same. */
function genreWord(name: string): string {
	const word = name.trim().toLowerCase();
	return word === "sci-fi" || word === "scifi" ? "science fiction" : word;
}

/**
 * The TMDB genres that match a Jellyfin genre name. Jellyfin has "Action" and "Science Fiction";
 * TMDB's TV list has combined ones like "Action & Adventure" and "Sci-Fi & Fantasy", which match
 * either half.
 */
export function matchGenreIds(name: string, genres: TmdbGenre[]): number[] {
	const wanted = genreWord(name);
	if (!wanted) return [];
	const exact = genres.filter((genre) => genreWord(genre.name) === wanted);
	if (exact.length > 0) return exact.map((genre) => genre.id);
	return genres
		.filter((genre) =>
			genre.name.split("&").some((part) => genreWord(part) === wanted),
		)
		.map((genre) => genre.id);
}

/** TMDB's genre list for movies or shows. */
export async function fetchGenres(
	kind: TmdbKind,
	signal?: AbortSignal,
): Promise<TmdbGenre[]> {
	const response = await fetch(`/tmdb/genre/${kind}/list?language=en-US`, {
		signal,
	});
	if (!response.ok) throw new Error(`TMDB ${response.status}`);
	return parseGenres(await response.json());
}

/** The most popular titles in one or more genres (any of them), most popular first. */
export async function fetchPopularInGenres(
	kind: TmdbKind,
	genreIds: number[],
	signal?: AbortSignal,
): Promise<TmdbTitle[]> {
	const params = new URLSearchParams({
		with_genres: genreIds.join("|"),
		sort_by: "popularity.desc",
		"vote_count.gte": "50",
		include_adult: "false",
		language: "en-US",
	});
	const response = await fetch(`/tmdb/discover/${kind}?${params}`, { signal });
	if (!response.ok) throw new Error(`TMDB ${response.status}`);
	return parseTrending(kind, await response.json());
}

/** What the server told us at start-up (shared with the login screen). */
export function useRuntimeConfig() {
	return useQuery({
		queryKey: ["runtime-config"],
		queryFn: ({ signal }) => loadRuntimeConfig(signal),
		staleTime: Number.POSITIVE_INFINITY,
	});
}

/** True when the server has a TMDB key set up. */
export function useTmdbEnabled(): boolean {
	return useRuntimeConfig().data?.tmdb ?? false;
}
