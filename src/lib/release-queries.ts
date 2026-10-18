import { useQuery } from "@tanstack/react-query";
import { parseMovieRelease, parseTvRelease, type ReleaseInfo } from "./release";
import type { TmdbTitle } from "./tmdb";

/** Ask TMDB where a title is in its release: in cinemas, on streaming, still to come. */
async function fetchRelease(
	title: TmdbTitle,
	signal?: AbortSignal,
): Promise<ReleaseInfo | null> {
	const extra =
		title.kind === "movie" ? "&append_to_response=release_dates" : "";
	const response = await fetch(
		`/tmdb/${title.kind}/${title.id}?language=en-US${extra}`,
		{ signal },
	);
	if (!response.ok) throw new Error(`TMDB ${response.status}`);
	const json: unknown = await response.json();
	const now = new Date();
	return (
		(title.kind === "movie"
			? parseMovieRelease(json, now)
			: parseTvRelease(json, now)) ?? null
	);
}

/** The release status of a title that isn't in the library. Nothing while loading or if unknown. */
export function useRelease(title: TmdbTitle) {
	return useQuery({
		queryKey: ["tmdb", "release", title.kind, title.id],
		staleTime: 30 * 60_000,
		retry: false,
		queryFn: ({ signal }) => fetchRelease(title, signal),
	});
}
