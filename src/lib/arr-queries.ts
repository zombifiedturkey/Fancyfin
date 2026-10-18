import {
	type QueryClient,
	queryOptions,
	useMutation,
	useQuery,
	useQueryClient,
} from "@tanstack/react-query";
import { useIsAdmin } from "./admin-queries";
import {
	ARR_KIND,
	ARR_LABEL,
	type ArrApp,
	type ArrOptions,
	arrErrorMessage,
	buildAddBody,
	type Candidate,
	type Choices,
	mergeCandidates,
	parseCandidates,
	parseFolders,
	parseProfiles,
	resolveChoices,
	searchTerm,
} from "./arr";
import { getArrPrefs } from "./arr-prefs";
import { api } from "./jellyfin";
import {
	fetchTvdbId,
	type TmdbKind,
	type TmdbTitle,
	useRuntimeConfig,
} from "./tmdb";

/** Radarr adds movies; Sonarr adds series. */
export function arrForKind(kind: TmdbKind): ArrApp {
	return kind === "movie" ? "radarr" : "sonarr";
}

/** A refusal or failure from Radarr or Sonarr (through the Fancyfin server), with words for it. */
export class ArrError extends Error {
	constructor(
		readonly app: ArrApp,
		readonly status: number,
		body: string,
	) {
		super(arrErrorMessage(app, status, body));
	}
}

/**
 * A call to Radarr or Sonarr through the Fancyfin server, which adds the API key and only lets
 * Jellyfin administrators through. The Jellyfin token is how the server knows who is asking.
 */
async function arrRequest(
	app: ArrApp,
	path: string,
	init: { method?: "GET" | "POST"; body?: unknown; signal?: AbortSignal } = {},
): Promise<unknown> {
	const hasBody = init.body !== undefined;
	let response: Response;
	try {
		response = await fetch(`/${app}/api/v3/${path}`, {
			method: init.method ?? "GET",
			signal: init.signal,
			headers: {
				Accept: "application/json",
				"X-Emby-Token": api.accessToken ?? "",
				...(hasBody ? { "Content-Type": "application/json" } : {}),
			},
			body: hasBody ? JSON.stringify(init.body) : undefined,
		});
	} catch (error) {
		if (error instanceof DOMException && error.name === "AbortError") {
			throw error;
		}
		throw new ArrError(app, 0, "");
	}
	if (!response.ok) {
		throw new ArrError(app, response.status, await response.text());
	}
	return response.json();
}

/** Which of Radarr and Sonarr this person can use: set up on the server, and they're an administrator. */
export function useArrAvailability(): Record<ArrApp, boolean> {
	const config = useRuntimeConfig();
	const admin = useIsAdmin();
	const isAdmin = admin.data === true;
	return {
		radarr: isAdmin && config.data?.radarr === true,
		sonarr: isAdmin && config.data?.sonarr === true,
	};
}

export function useArrAvailable(app: ArrApp): boolean {
	return useArrAvailability()[app];
}

/** The quality profiles and root folders (with free space) the app offers. */
export function optionsQuery(app: ArrApp) {
	return queryOptions({
		queryKey: ["arr", app, "options"],
		staleTime: 60_000,
		retry: false,
		queryFn: async ({ signal }): Promise<ArrOptions> => {
			const [profiles, folders] = await Promise.all([
				arrRequest(app, "qualityprofile", { signal }),
				arrRequest(app, "rootfolder", { signal }),
			]);
			return {
				profiles: parseProfiles(profiles),
				folders: parseFolders(folders),
			};
		},
	});
}

async function lookup(
	app: ArrApp,
	term: string,
	signal?: AbortSignal,
): Promise<Candidate[]> {
	const json = await arrRequest(
		app,
		`${ARR_KIND[app]}/lookup?term=${encodeURIComponent(term)}`,
		{ signal },
	);
	return parseCandidates(app, json);
}

/**
 * How the app sees a TMDB title: the exact match first (by TMDB id for movies, TVDB id for shows),
 * then the other titles a search for the name turns up, in case the exact one is wrong.
 */
export function candidatesQuery(app: ArrApp, title: TmdbTitle) {
	return queryOptions({
		queryKey: ["arr", app, "candidates", title.kind, title.id],
		staleTime: 5 * 60_000,
		retry: false,
		queryFn: async ({ signal }): Promise<Candidate[]> => {
			const exact = async (): Promise<Candidate[]> => {
				if (app === "radarr") return lookup(app, `tmdb:${title.id}`, signal);
				const tvdbId = await fetchTvdbId(title.id, signal).catch(
					() => undefined,
				);
				return tvdbId ? lookup(app, `tvdb:${tvdbId}`, signal) : [];
			};
			const [exactResult, searchResult] = await Promise.allSettled([
				exact(),
				lookup(app, searchTerm(app, title.title, title.year), signal),
			]);
			if (
				exactResult.status === "rejected" &&
				searchResult.status === "rejected"
			) {
				throw exactResult.reason;
			}
			return mergeCandidates(
				exactResult.status === "fulfilled" ? exactResult.value : [],
				searchResult.status === "fulfilled" ? searchResult.value : [],
			);
		},
	});
}

const addedKey = (title: TmdbTitle) =>
	["arr", "added", title.kind, title.id] as const;

/** True once this title has been added during this visit, so its button can say so. */
export function useArrAdded(title: TmdbTitle): boolean {
	const { data } = useQuery({
		queryKey: addedKey(title),
		queryFn: () => false,
		initialData: false,
		staleTime: Number.POSITIVE_INFINITY,
		gcTime: Number.POSITIVE_INFINITY,
	});
	return data;
}

async function postAdd(app: ArrApp, candidate: Candidate, choices: Choices) {
	await arrRequest(app, ARR_KIND[app], {
		method: "POST",
		body: buildAddBody(app, candidate, choices),
	});
}

/** Add the chosen match with the chosen settings. */
export function useAddToArr(title: TmdbTitle) {
	const queryClient = useQueryClient();
	const app = arrForKind(title.kind);
	return useMutation({
		mutationFn: (input: { candidate: Candidate; choices: Choices }) =>
			postAdd(app, input.candidate, input.choices),
		onSuccess: () => queryClient.setQueryData(addedKey(title), true),
	});
}

/**
 * Add a title using the settings saved last time, without asking. Throws when it can't be done
 * safely (no exact match, or nowhere to put it); the caller then shows the dialog instead.
 */
export async function quickAdd(
	queryClient: QueryClient,
	title: TmdbTitle,
): Promise<void> {
	const app = arrForKind(title.kind);
	const [options, candidates] = await Promise.all([
		queryClient.fetchQuery(optionsQuery(app)),
		queryClient.fetchQuery(candidatesQuery(app, title)),
	]);
	// Only add without asking when the app's answer is certainly this title.
	const candidate = candidates.find((c) => c.tmdbId === title.id);
	if (!candidate) {
		throw new Error(`${ARR_LABEL[app]} didn't return an exact match.`);
	}
	if (candidate.existingId) {
		queryClient.setQueryData(addedKey(title), true);
		return;
	}
	const choices = resolveChoices(options, getArrPrefs()[app], candidate);
	if (!choices) {
		throw new Error(
			`${ARR_LABEL[app]} has no root folder or quality profile to add to.`,
		);
	}
	await postAdd(app, candidate, choices);
	queryClient.setQueryData(addedKey(title), true);
}

/** What to tell the person when something in this file failed. */
export function describeArrError(error: unknown): string {
	return error instanceof Error ? error.message : "Something went wrong.";
}
