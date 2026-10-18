import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";

/** Real seasons in order, with Specials (season 0) last. */
export function orderSeasons(seasons: BaseItemDto[]): BaseItemDto[] {
	const rank = (season: BaseItemDto) => {
		const index = season.IndexNumber;
		if (index === undefined || index === null)
			return Number.MAX_SAFE_INTEGER - 1;
		return index === 0 ? Number.MAX_SAFE_INTEGER : index;
	};
	return [...seasons].sort((a, b) => rank(a) - rank(b));
}

/** The season to open on: the first real one with something left to watch, else the first real one. */
export function defaultSeason(seasons: BaseItemDto[]): BaseItemDto | undefined {
	const ordered = orderSeasons(seasons);
	const real = ordered.filter((season) => season.IndexNumber !== 0);
	return (
		real.find((season) => (season.UserData?.UnplayedItemCount ?? 0) > 0) ??
		real[0] ??
		ordered[0]
	);
}

/** "6 of 10 watched" numbers for a season, if the server told us enough to work them out. */
export function seasonCounts(
	season: BaseItemDto,
): { watched: number; total: number } | undefined {
	const total = season.ChildCount ?? season.RecursiveItemCount;
	const unplayed = season.UserData?.UnplayedItemCount;
	if (typeof total !== "number" || typeof unplayed !== "number")
		return undefined;
	return { watched: Math.max(0, Math.min(total, total - unplayed)), total };
}

export function watchedCount(episodes: BaseItemDto[]): number {
	return episodes.filter((episode) => episode.UserData?.Played).length;
}

/** The first episode not yet watched. A half-watched one counts as not watched. */
export function nextEpisodeId(episodes: BaseItemDto[]): string | undefined {
	return episodes.find((episode) => episode.Id && !episode.UserData?.Played)
		?.Id;
}
