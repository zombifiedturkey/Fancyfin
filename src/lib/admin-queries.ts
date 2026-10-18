import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import type { RemoteSearchResult } from "@jellyfin/sdk/lib/generated-client/models/remote-search-result";
import { getItemLookupApi } from "@jellyfin/sdk/lib/utils/api/item-lookup-api";
import { getLibraryApi } from "@jellyfin/sdk/lib/utils/api/library-api";
import { getUserApi } from "@jellyfin/sdk/lib/utils/api/user-api";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "../auth/AuthProvider";
import { api } from "./jellyfin";

/** Whether the signed-in user may change the library. Edit tools are hidden for everyone else. */
export function useIsAdmin() {
	const { userId } = useSession();
	return useQuery({
		queryKey: ["me", userId],
		staleTime: 5 * 60_000,
		queryFn: async ({ signal }) => {
			const { data } = await getUserApi(api).getCurrentUser({ signal });
			return data.Policy?.IsAdministrator === true;
		},
	});
}

/** Search the metadata providers (TMDb and so on) for a better match for a title. */
export function useRemoteSearch(
	item: BaseItemDto,
	name: string,
	year: number | undefined,
) {
	const itemId = item.Id ?? "";
	return useQuery({
		queryKey: ["remote-search", itemId, item.Type, name, year ?? null],
		enabled: Boolean(itemId && name.trim()),
		staleTime: 60_000,
		retry: false,
		queryFn: async ({ signal }): Promise<RemoteSearchResult[]> => {
			const lookup = getItemLookupApi(api);
			if (item.Type === "Series") {
				const { data } = await lookup.getSeriesRemoteSearchResults(
					{
						seriesInfoRemoteSearchQuery: {
							ItemId: itemId,
							SearchInfo: { Name: name, Year: year },
						},
					},
					{ signal },
				);
				return data;
			}
			const { data } = await lookup.getMovieRemoteSearchResults(
				{
					movieInfoRemoteSearchQuery: {
						ItemId: itemId,
						SearchInfo: { Name: name, Year: year },
					},
				},
				{ signal },
			);
			return data;
		},
	});
}

/** Tell the server a title really is the chosen result. It re-downloads the metadata. */
export function useApplyMatch(itemId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async (input: {
			result: RemoteSearchResult;
			replaceAllImages: boolean;
		}) => {
			await getItemLookupApi(api).applySearchCriteria({
				itemId,
				remoteSearchResult: input.result,
				replaceAllImages: input.replaceAllImages,
			});
		},
		onSuccess: () => queryClient.invalidateQueries(),
	});
}

/** Ask the server to re-scan a title's metadata and artwork. It finishes in the background. */
export function useRefreshItem(itemId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async () => {
			await getLibraryApi(api).refreshItem({
				itemId,
				metadataRefreshMode: "FullRefresh",
				imageRefreshMode: "FullRefresh",
			});
		},
		onSuccess: () => {
			// The server works on this in the background, so check back a couple of times.
			for (const delay of [4000, 12000]) {
				window.setTimeout(() => void queryClient.invalidateQueries(), delay);
			}
		},
	});
}
