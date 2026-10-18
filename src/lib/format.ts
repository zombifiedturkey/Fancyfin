import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";

/** Jellyfin measures time in "ticks": 10,000,000 per second. */
const TICKS_PER_MINUTE = 600_000_000;

export function ticksToMinutes(
	ticks: number | null | undefined,
): number | undefined {
	return typeof ticks === "number" && ticks > 0
		? Math.round(ticks / TICKS_PER_MINUTE)
		: undefined;
}

/** 112 -> "1h 52m", 45 -> "45m", 120 -> "2h". */
export function formatRuntime(minutes: number | undefined): string | undefined {
	if (minutes === undefined) return undefined;
	const hours = Math.floor(minutes / 60);
	const rest = minutes % 60;
	if (hours === 0) return `${rest}m`;
	return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`;
}

/** Minutes remaining on a partly watched item. */
export function minutesLeft(item: BaseItemDto): number | undefined {
	const total = item.RunTimeTicks;
	const position = item.UserData?.PlaybackPositionTicks;
	if (!total || !position) return undefined;
	const left = Math.round((total - position) / TICKS_PER_MINUTE);
	return left > 0 ? left : undefined;
}

/** "S2:E5", or "E5" when there is no season number. */
export function episodeLabel(item: BaseItemDto): string | undefined {
	const episode = item.IndexNumber;
	if (episode === undefined || episode === null) return undefined;
	const season = item.ParentIndexNumber;
	return season === undefined || season === null
		? `E${episode}`
		: `S${season}:E${episode}`;
}

/** The small line under a wide card: episode info for episodes, time left or year for movies. */
export function landscapeSubtitle(item: BaseItemDto): string | undefined {
	if (item.Type === "Episode") {
		const text = [episodeLabel(item), item.Name].filter(Boolean).join(" · ");
		return text || undefined;
	}
	const left = formatRuntime(minutesLeft(item));
	if (left) return `${left} left`;
	return item.ProductionYear ? String(item.ProductionYear) : undefined;
}

/** A 0-100 progress value that is safe to use as a CSS width. */
export function clampPercent(value: number | null | undefined): number {
	if (typeof value !== "number" || Number.isNaN(value)) return 0;
	return Math.min(100, Math.max(0, value));
}
