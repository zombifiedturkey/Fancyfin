import { useQuery } from "@tanstack/react-query";
import {
	parseGenreNames,
	parseMovieRelease,
	parseTvRelease,
	type ReleaseInfo,
} from "./release";
import type { TmdbTitle } from "./tmdb";

/** Where a title is in its release, and its genres, from one TMDB call. */
export type DiscoverInfo = { release?: ReleaseInfo; genres: string[] };

/** Ask TMDB where a title is in its release (in cinemas, on streaming, still to come) and its genres. */
async function fetchRelease(
	title: TmdbTitle,
	signal?: AbortSignal,
): Promise<DiscoverInfo> {
	const extra =
		title.kind === "movie" ? "&append_to_response=release_dates" : "";
	const response = await fetch(
		`/tmdb/${title.kind}/${title.id}?language=en-US${extra}`,
		{ signal },
	);
	if (!response.ok) throw new Error(`TMDB ${response.status}`);
	const json: unknown = await response.json();
	const now = new Date();
	return {
		release:
			(title.kind === "movie"
				? parseMovieRelease(json, now)
				: parseTvRelease(json, now)) ?? undefined,
		genres: parseGenreNames(json),
	};
}

/** The release status and genres of a title that isn't in the library. */
export function useRelease(title: TmdbTitle) {
	return useQuery({
		queryKey: ["tmdb", "release", title.kind, title.id],
		staleTime: 30 * 60_000,
		retry: false,
		queryFn: ({ signal }) => fetchRelease(title, signal),
	});
}
