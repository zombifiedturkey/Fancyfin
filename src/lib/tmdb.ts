import { useQuery } from "@tanstack/react-query";
import { loadRuntimeConfig } from "./runtime-config";

export type TmdbKind = "movie" | "tv";

/** A title from TMDB's popularity charts. Only what the featured banner needs. */
export type TmdbTitle = {
	id: number;
	kind: TmdbKind;
	title: string;
	overview: string;
	/** TMDB image path, like "/abc.jpg". */
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
