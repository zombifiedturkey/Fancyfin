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

/** Where a title is in its release, fetched from TMDB. Nothing while loading or if unknown. */
export function ReleaseLine({ title }: { title: TmdbTitle }) {
	const release = useRelease(title).data;
	if (!release) return null;
	return <ReleasePill release={release} />;
}
