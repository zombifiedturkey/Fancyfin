import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "../auth/AuthProvider";
import { loadIndexes } from "./featured-queries";
import type { TmdbKind } from "./tmdb";
import { appendFor, parseTmdbDetail, type TmdbDetail } from "./tmdb-detail";

async function fetchDetail(
	kind: TmdbKind,
	id: number,
	signal?: AbortSignal,
): Promise<TmdbDetail | null> {
	const response = await fetch(
		`/tmdb/${kind}/${id}?language=en-US&append_to_response=${appendFor(kind)}`,
		{ signal },
	);
	if (!response.ok) throw new Error(`TMDB ${response.status}`);
	return parseTmdbDetail(kind, await response.json(), new Date()) ?? null;
}

/** The full details of a title from TMDB, for the floating panel. */
export function useTmdbDetail(kind: TmdbKind, id: number) {
	return useQuery({
		queryKey: ["tmdb", "detail", kind, id],
		staleTime: 30 * 60_000,
		retry: false,
		queryFn: ({ signal }) => fetchDetail(kind, id, signal),
	});
}

/** The library's item id for a TMDB title, if the library has it (so the real panel can open). */
export function useOwnedId(kind: TmdbKind, id: number) {
	const { userId } = useSession();
	const queryClient = useQueryClient();
	return useQuery({
		queryKey: ["tmdb", "owned", userId, kind, id],
		staleTime: 5 * 60_000,
		queryFn: async (): Promise<string | null> => {
			const indexes = await loadIndexes(queryClient, userId, [kind]);
			return indexes.get(kind)?.get(id) ?? null;
		},
	});
}
