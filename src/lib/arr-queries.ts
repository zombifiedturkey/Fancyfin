import {
	type QueryClient,
	queryOptions,
	useMutation,
	useQuery,
	useQueryClient,
} from "@tanstack/react-query";
import {
	ARR_KIND,
	ARR_LABEL,
	type ArrApp,
	type ArrOptions,
	type ArrStatus,
	arrErrorMessage,
	buildAddBody,
	type Candidate,
	type Choices,
	isInLibrary,
	mergeCandidates,
	parseArrStatus,
	parseCandidates,
	parseFolders,
	parseProfiles,
	resolveChoices,
	searchTerm,
} from "./arr";
import { getArrPrefs } from "./arr-prefs";
import {
	fetchTvdbId,
	type TmdbKind,
	type TmdbTitle,
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
 * A call to the Fancyfin server's settings service (/arr), which keeps the Radarr and Sonarr keys
 * and passes on only the few calls the Add dialog needs.
 * `app` is only used to word errors; leave it out for calls that aren't about one app.
 */
async function arrApi(
	app: ArrApp | undefined,
	path: string,
	init: {
		method?: "GET" | "POST" | "PUT" | "DELETE";
		body?: unknown;
		signal?: AbortSignal;
	} = {},
): Promise<unknown> {
	const hasBody = init.body !== undefined;
	const fail = (status: number, body: string) =>
		app
			? new ArrError(app, status, body)
			: new Error(`Fancyfin settings service answered ${status}.`);
	let response: Response;
	try {
		response = await fetch(`/arr/${path}`, {
			method: init.method ?? "GET",
			signal: init.signal,
			headers: {
				Accept: "application/json",
				...(hasBody ? { "Content-Type": "application/json" } : {}),
			},
			body: hasBody ? JSON.stringify(init.body) : undefined,
		});
	} catch (error) {
		if (error instanceof DOMException && error.name === "AbortError") {
			throw error;
		}
		throw fail(0, "");
	}
	if (!response.ok) throw fail(response.status, await response.text());
	return response.json();
}

/** A call to Radarr's or Sonarr's own API, through the settings service. */
function arrRequest(
	app: ArrApp,
	path: string,
	init: { method?: "GET" | "POST"; body?: unknown; signal?: AbortSignal } = {},
): Promise<unknown> {
	return arrApi(app, `${app}/api/v3/${path}`, init);
}

/**
 * What is connected, and whether it is working. Stays empty on a server without the settings
 * service, in which case Radarr and Sonarr are simply not offered.
 */
export function useArrStatus() {
	return useQuery({
		queryKey: ["arr", "status"],
		staleTime: 30_000,
		retry: false,
		queryFn: async ({ signal }): Promise<ArrStatus> => {
			const status = parseArrStatus(
				await arrApi(undefined, "status", { signal }),
			);
			if (!status)
				throw new Error("Unexpected answer from the settings service.");
			return status;
		},
	});
}

/**
 * Which of Radarr and Sonarr can be used: connected in Settings, and the saved address and key
 * worked when last checked. Until then none of the Add buttons appear anywhere.
 */
export function useArrAvailability(): Record<ArrApp, boolean> {
	const apps = useArrStatus().data?.apps;
	const usable = (app: ArrApp) =>
		apps?.[app].configured === true && !apps[app].error;
	return { radarr: usable("radarr"), sonarr: usable("sonarr") };
}

export function useArrAvailable(app: ArrApp): boolean {
	return useArrAvailability()[app];
}

/** Save an app's address and key on the server. It checks they work first. */
export function useConnectArr(app: ArrApp) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: (input: { url: string; key: string }) =>
			arrApi(app, app, { method: "PUT", body: input }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["arr"] }),
	});
}

/** Forget an app's address and key. */
export function useDisconnectArr(app: ArrApp) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: () => arrApi(app, app, { method: "DELETE" }),
		onSuccess: () => queryClient.invalidateQueries({ queryKey: ["arr"] }),
	});
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

/** Ask Radarr or Sonarr whether it already has this title. */
async function checkInArr(
	title: TmdbTitle,
	signal?: AbortSignal,
): Promise<boolean> {
	if (title.kind === "movie") {
		return isInLibrary(
			await arrRequest("radarr", `movie?tmdbId=${title.id}`, { signal }),
		);
	}
	// Sonarr knows shows by their TVDB id.
	const tvdbId = await fetchTvdbId(title.id, signal);
	if (!tvdbId) return false;
	return isInLibrary(
		await arrRequest("sonarr", `series?tvdbId=${tvdbId}`, { signal }),
	);
}

/**
 * Whether the app already has this title: "already" if it was there before, "added" if it was added
 * during this visit, otherwise false. Asks Radarr or Sonarr, so it is right even for titles added
 * some other way. Stays false while it checks, or if the app can't be asked.
 */
export function useArrAdded(title: TmdbTitle): "already" | "added" | false {
	const { data } = useQuery<"already" | "added" | false>({
		queryKey: addedKey(title),
		queryFn: async ({ signal }) =>
			(await checkInArr(title, signal)) ? "already" : false,
		// What we just added is certain; anything else is worth asking again after a minute.
		staleTime: (query) =>
			query.state.data === "added" ? Number.POSITIVE_INFINITY : 60_000,
		retry: false,
	});
	return data ?? false;
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
		onSuccess: () => queryClient.setQueryData(addedKey(title), "added"),
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
		queryClient.setQueryData(addedKey(title), "already");
		return;
	}
	const choices = resolveChoices(options, getArrPrefs()[app], candidate);
	if (!choices) {
		throw new Error(
			`${ARR_LABEL[app]} has no root folder or quality profile to add to.`,
		);
	}
	await postAdd(app, candidate, choices);
	queryClient.setQueryData(addedKey(title), "added");
}

/** What to tell the person when something in this file failed. */
export function describeArrError(error: unknown): string {
	return error instanceof Error ? error.message : "Something went wrong.";
}
