import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import type { UserItemDataDto } from "@jellyfin/sdk/lib/generated-client/models/user-item-data-dto";
import { getLibraryApi } from "@jellyfin/sdk/lib/utils/api/library-api";
import { getShowApi } from "@jellyfin/sdk/lib/utils/api/show-api";
import { getUserDataApi } from "@jellyfin/sdk/lib/utils/api/user-data-api";
import {
	type QueryClient,
	useMutation,
	useQuery,
	useQueryClient,
} from "@tanstack/react-query";
import { useSession } from "../auth/AuthProvider";
import { api } from "./jellyfin";

/** One title with everything the detail page needs (people, media info and so on). */
export function useItem(itemId: string) {
	const { userId } = useSession();
	return useQuery({
		queryKey: ["item", userId, itemId],
		queryFn: async ({ signal }) => {
			const { data } = await getLibraryApi(api).getItem(
				{ itemId, userId },
				{ signal },
			);
			return data;
		},
	});
}

/** "More like this". */
export function useSimilarItems(itemId: string, enabled: boolean) {
	const { userId } = useSession();
	return useQuery({
		queryKey: ["similar", userId, itemId],
		enabled,
		queryFn: async ({ signal }) => {
			const { data } = await getLibraryApi(api).getSimilarItems(
				{ itemId, userId, limit: 16 },
				{ signal },
			);
			return data.Items ?? [];
		},
	});
}

export function useSeasons(seriesId: string) {
	const { userId } = useSession();
	return useQuery({
		queryKey: ["seasons", userId, seriesId],
		queryFn: async ({ signal }) => {
			const { data } = await getShowApi(api).getSeasons(
				{
					seriesId,
					userId,
					enableUserData: true,
					enableImages: false,
					fields: ["ChildCount"],
				},
				{ signal },
			);
			return data.Items ?? [];
		},
	});
}

export function useEpisodes(seriesId: string, seasonId: string | undefined) {
	const { userId } = useSession();
	return useQuery({
		queryKey: ["episodes", userId, seriesId, seasonId],
		enabled: Boolean(seasonId),
		queryFn: async ({ signal }) => {
			const { data } = await getShowApi(api).getEpisodes(
				{
					seriesId,
					seasonId,
					userId,
					enableUserData: true,
					imageTypeLimit: 1,
					enableImageTypes: ["Primary"],
					fields: ["Overview"],
				},
				{ signal },
			);
			return data.Items ?? [];
		},
	});
}

/** Put fresh watched/favourite state into the cached title, then refresh every list that shows it. */
function applyUserData(
	queryClient: QueryClient,
	userId: string,
	itemId: string,
	userData: UserItemDataDto,
) {
	queryClient.setQueryData<BaseItemDto>(["item", userId, itemId], (old) =>
		old ? { ...old, UserData: userData } : old,
	);
	void queryClient.invalidateQueries({
		predicate: (query) =>
			!(query.queryKey[0] === "item" && query.queryKey[2] === itemId),
	});
}

/** Mark a title watched or unwatched. */
export function useSetPlayed(itemId: string) {
	const { userId } = useSession();
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async (played: boolean) => {
			const dataApi = getUserDataApi(api);
			const { data } = played
				? await dataApi.markPlayedItem({ itemId, userId })
				: await dataApi.markUnplayedItem({ itemId, userId });
			return data;
		},
		onSuccess: (userData) =>
			applyUserData(queryClient, userId, itemId, userData),
	});
}

/** Add or remove a title from favourites. */
export function useSetFavorite(itemId: string) {
	const { userId } = useSession();
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async (favorite: boolean) => {
			const dataApi = getUserDataApi(api);
			const { data } = favorite
				? await dataApi.markFavoriteItem({ itemId, userId })
				: await dataApi.unmarkFavoriteItem({ itemId, userId });
			return data;
		},
		onSuccess: (userData) =>
			applyUserData(queryClient, userId, itemId, userData),
	});
}
