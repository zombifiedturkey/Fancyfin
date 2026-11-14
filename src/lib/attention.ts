import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";

export type AttentionReason = "match" | "poster" | "description";

export const REASON_LABEL: Record<AttentionReason, string> = {
	match: "No match",
	poster: "No poster",
	description: "No description",
};

/** The ids Jellyfin gets from a metadata match. Without any of them the title was never identified. */
const MATCH_IDS = ["Tmdb", "Imdb", "Tvdb", "TmdbCollection"];

/** What is missing from a title's metadata. Empty means it looks fine. */
export function attentionReasons(item: BaseItemDto): AttentionReason[] {
	const reasons: AttentionReason[] = [];
	const ids = item.ProviderIds ?? {};
	if (!MATCH_IDS.some((key) => (ids[key] ?? "").trim() !== "")) {
		reasons.push("match");
	}
	if (!item.ImageTags?.Primary) reasons.push("poster");
	if (!(item.Overview ?? "").trim()) reasons.push("description");
	return reasons;
}
