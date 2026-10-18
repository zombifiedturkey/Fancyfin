import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "../auth/AuthProvider";
import { useArrAvailability } from "./arr-queries";
import { loadIndexes } from "./featured-queries";
import {
	fetchSearch,
	type TmdbKind,
	type TmdbTitle,
	useTmdbEnabled,
} from "./tmdb";

const MAX_MISSING = 6;

/**
 * Titles matching a search that the library doesn't have, from TMDB, so they can be added to Radarr
 * or Sonarr. Movies only count when Radarr is connected, shows when Sonarr is. Empty for everyone
 * who can't add anything.
 */
export function useMissingResults(term: string) {
	const { userId } = useSession();
	const queryClient = useQueryClient();
	const available = useArrAvailability();
	const tmdb = useTmdbEnabled();

	const kinds: TmdbKind[] = [];
	if (available.radarr) kinds.push("movie");
	if (available.sonarr) kinds.push("tv");
	const trimmed = term.trim();

	return useQuery({
		queryKey: ["search-missing", userId, trimmed, kinds.join(",")],
		enabled: tmdb && kinds.length > 0 && trimmed.length >= 2,
		staleTime: 5 * 60_000,
		retry: false,
		queryFn: async ({ signal }): Promise<TmdbTitle[]> => {
			const [found, indexes] = await Promise.all([
				fetchSearch(trimmed, signal),
				loadIndexes(queryClient, userId, kinds),
			]);
			return found
				.filter(
					(title) =>
						kinds.includes(title.kind) &&
						title.poster &&
						!indexes.get(title.kind)?.has(title.id),
				)
				.slice(0, MAX_MISSING);
		},
	});
}
