import { getUserDataApi } from "@jellyfin/sdk/lib/utils/api/user-data-api";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useSession } from "../auth/AuthProvider";
import { api } from "./jellyfin";

/** Mark a whole season watched or unwatched. The server applies it to every episode in it. */
export function useSetSeasonPlayed(seasonId: string | undefined) {
	const { userId } = useSession();
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async (played: boolean) => {
			if (!seasonId) return;
			const dataApi = getUserDataApi(api);
			if (played) await dataApi.markPlayedItem({ itemId: seasonId, userId });
			else await dataApi.markUnplayedItem({ itemId: seasonId, userId });
		},
		// Episodes, season counts, Next Up and Continue Watching all change.
		onSuccess: () => queryClient.invalidateQueries(),
	});
}
