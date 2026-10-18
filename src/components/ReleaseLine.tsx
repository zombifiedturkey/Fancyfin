import type { ReleaseInfo } from "../lib/release";
import { useRelease } from "../lib/release-queries";
import type { TmdbTitle } from "../lib/tmdb";

/** The status, and when it is expected on streaming or disc. */
export function ReleasePill({ release }: { release: ReleaseInfo }) {
	return (
		<p className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1.5 text-sm">
			<span className="glass rounded-pill px-3 py-1 font-semibold text-ink">
				{release.status}
			</span>
			{release.detail && (
				<span className="font-medium text-ink-muted">{release.detail}</span>
			)}
		</p>
	);
}

/** A title's genres, as the line under the year and rating. */
export function GenreLine({ genres }: { genres: string[] }) {
	if (genres.length === 0) return null;
	return (
		<p className="mt-1 text-sm font-medium text-ink/80">
			{genres.slice(0, 3).join(", ")}
		</p>
	);
}

/** The genres and release status of a title that isn't in the library, fetched from TMDB. */
export function ReleaseLine({ title }: { title: TmdbTitle }) {
	const info = useRelease(title).data;
	if (!info) return null;
	return (
		<>
			<GenreLine genres={info.genres} />
			{info.release && <ReleasePill release={info.release} />}
		</>
	);
}
