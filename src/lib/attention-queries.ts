import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { getLibraryApi } from "@jellyfin/sdk/lib/utils/api/library-api";
import { useQuery } from "@tanstack/react-query";
import { useSession } from "../auth/AuthProvider";
import { type AttentionReason, attentionReasons } from "./attention";
import { api } from "./jellyfin";

export type AttentionItem = {
	item: BaseItemDto;
	reasons: AttentionReason[];
};

const PAGE = 500;

/** Every movie and show with something missing from its metadata, unmatched ones first. */
export function useAttentionItems() {
	const { userId } = useSession();
	return useQuery({
		queryKey: ["attention", userId],
		staleTime: 30_000,
		queryFn: async ({ signal }): Promise<AttentionItem[]> => {
			const out: AttentionItem[] = [];
			for (let start = 0; ; start += PAGE) {
				const { data } = await getLibraryApi(api).getItems(
					{
						userId,
						includeItemTypes: ["Movie", "Series"],
						recursive: true,
						sortBy: ["SortName"],
						sortOrder: ["Ascending"],
						fields: ["ProviderIds", "Overview", "Path", "DateCreated"],
						enableImageTypes: ["Primary"],
						imageTypeLimit: 1,
						startIndex: start,
						limit: PAGE,
						enableTotalRecordCount: true,
					},
					{ signal },
				);
				const items = data.Items ?? [];
				for (const item of items) {
					if (!item.Id) continue;
					const reasons = attentionReasons(item);
					if (reasons.length > 0) out.push({ item, reasons });
				}
				if (items.length < PAGE) break;
			}
			// Titles that were never matched are the ones that need a person, so they go first.
			return out.sort(
				(a, b) =>
					Number(b.reasons.includes("match")) -
						Number(a.reasons.includes("match")) ||
					(a.item.SortName ?? a.item.Name ?? "").localeCompare(
						b.item.SortName ?? b.item.Name ?? "",
					),
			);
		},
	});
}
