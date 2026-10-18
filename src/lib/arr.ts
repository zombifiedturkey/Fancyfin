/**
 * Radarr and Sonarr, as plain data. No browser APIs in here, so it can be tested on its own.
 * The requests themselves are in arr-queries.ts; the saved choices are in arr-prefs.ts.
 *
 * Radarr adds movies and Sonarr adds TV series. Both speak the same style of API (v3), and the
 * Fancyfin server forwards a few of its calls, adding the API key itself.
 */

export type ArrApp = "radarr" | "sonarr";

export const ARR_LABEL: Record<ArrApp, string> = {
	radarr: "Radarr",
	sonarr: "Sonarr",
};

/** Radarr's word for a title is "movie"; Sonarr's is "series". */
export const ARR_KIND: Record<ArrApp, "movie" | "series"> = {
	radarr: "movie",
	sonarr: "series",
};

/** What the Fancyfin server knows about one app's connection. It never includes the key. */
export type ArrConnection = {
	configured: boolean;
	url?: string;
	version?: string;
	/** Movies in Radarr, or series in Sonarr. */
	count?: number;
	/** Why a saved connection isn't working, in words for the person. */
	error?: string;
};

export type ArrStatus = {
	/** False when the server has no mounted /config folder, so the connection is forgotten on update. */
	persistent: boolean;
	apps: Record<ArrApp, ArrConnection>;
};

export type QualityProfile = { id: number; name: string };
export type RootFolder = { path: string; freeSpace?: number };
export type ArrOptions = { profiles: QualityProfile[]; folders: RootFolder[] };

export const AVAILABILITY = [
	{ value: "announced", label: "Announced" },
	{ value: "inCinemas", label: "In cinemas" },
	{ value: "released", label: "Released" },
] as const;
export type Availability = (typeof AVAILABILITY)[number]["value"];

export const MONITOR = [
	{ value: "all", label: "All episodes" },
	{ value: "future", label: "Future episodes" },
	{ value: "missing", label: "Missing episodes" },
	{ value: "firstSeason", label: "First season" },
	{ value: "lastSeason", label: "Latest season" },
	{ value: "none", label: "Nothing yet" },
] as const;
export type Monitor = (typeof MONITOR)[number]["value"];

export const SERIES_TYPES = [
	{ value: "standard", label: "Standard" },
	{ value: "anime", label: "Anime" },
	{ value: "daily", label: "Daily" },
] as const;
export type SeriesType = (typeof SERIES_TYPES)[number]["value"];

/** What the person picks in the Add dialog. Radarr ignores the series fields and vice versa. */
export type Choices = {
	rootFolder: string;
	qualityProfileId: number;
	minimumAvailability: Availability;
	monitor: Monitor;
	seriesType: SeriesType;
	/** Start looking for the download as soon as it's added. */
	search: boolean;
};

/** Remembered between visits. `skip` means: add straight away, without showing the dialog. */
export type ArrPrefs = Partial<Choices> & { skip: boolean };

export type AllPrefs = Record<ArrApp, ArrPrefs>;

export const DEFAULT_PREFS: AllPrefs = {
	radarr: { skip: false },
	sonarr: { skip: false },
};

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null;
}

function oneOf<T extends string>(
	value: unknown,
	options: readonly { value: T }[],
): T | undefined {
	return options.find((option) => option.value === value)?.value;
}

function sanitizeOne(raw: unknown): ArrPrefs {
	const input = isRecord(raw) ? raw : {};
	const prefs: ArrPrefs = { skip: input.skip === true };
	if (typeof input.rootFolder === "string" && input.rootFolder) {
		prefs.rootFolder = input.rootFolder;
	}
	if (
		typeof input.qualityProfileId === "number" &&
		Number.isInteger(input.qualityProfileId)
	) {
		prefs.qualityProfileId = input.qualityProfileId;
	}
	const availability = oneOf(input.minimumAvailability, AVAILABILITY);
	if (availability) prefs.minimumAvailability = availability;
	const monitor = oneOf(input.monitor, MONITOR);
	if (monitor) prefs.monitor = monitor;
	const seriesType = oneOf(input.seriesType, SERIES_TYPES);
	if (seriesType) prefs.seriesType = seriesType;
	if (typeof input.search === "boolean") prefs.search = input.search;
	return prefs;
}

/** Turn whatever was saved (possibly old, partial or damaged) into valid preferences. */
export function sanitizePrefs(raw: unknown): AllPrefs {
	const input = isRecord(raw) ? raw : {};
	return {
		radarr: sanitizeOne(input.radarr),
		sonarr: sanitizeOne(input.sonarr),
	};
}

/** A title as the app knows it, from its lookup. `raw` is sent back when adding. */
export type Candidate = {
	/** Stable id for picking from a list: the TMDB id for movies, the TVDB id for series. */
	key: string;
	title: string;
	year?: number;
	/** Minutes. */
	runtime?: number;
	/** Studio for a movie, network for a series. */
	studio?: string;
	status?: string;
	overview?: string;
	/** A picture address the browser can load directly. */
	poster?: string;
	seasons?: number;
	tmdbId?: number;
	tvdbId?: number;
	/** Set when the app already has this title. */
	existingId?: number;
	raw: Record<string, unknown>;
};

function text(value: unknown): string | undefined {
	return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function positive(value: unknown): number | undefined {
	return typeof value === "number" && Number.isFinite(value) && value > 0
		? value
		: undefined;
}

function posterOf(row: Record<string, unknown>): string | undefined {
	const images = Array.isArray(row.images) ? row.images : [];
	for (const image of images) {
		if (!isRecord(image) || image.coverType !== "poster") continue;
		const url = text(image.remoteUrl) ?? text(image.url);
		// Only addresses the browser can open on its own. Relative ones would point at the app.
		if (url?.startsWith("https://") || url?.startsWith("http://")) return url;
	}
	return undefined;
}

/** Read an app's lookup answer. Entries it can't make sense of are left out. */
export function parseCandidates(app: ArrApp, json: unknown): Candidate[] {
	if (!Array.isArray(json)) return [];
	const out: Candidate[] = [];
	for (const row of json) {
		if (!isRecord(row)) continue;
		const title = text(row.title);
		const tmdbId = positive(row.tmdbId);
		const tvdbId = positive(row.tvdbId);
		const key = app === "radarr" ? tmdbId : tvdbId;
		if (!title || key === undefined) continue;
		out.push({
			key: String(key),
			title,
			year: positive(row.year),
			runtime: positive(row.runtime),
			studio: text(app === "radarr" ? row.studio : row.network),
			status: text(row.status),
			overview: text(row.overview),
			poster: posterOf(row),
			seasons: Array.isArray(row.seasons)
				? row.seasons.filter(
						(season) => isRecord(season) && positive(season.seasonNumber),
					).length || undefined
				: undefined,
			tmdbId,
			tvdbId,
			existingId: positive(row.id),
			raw: row,
		});
	}
	return out;
}

/** The exact match first, then the rest, each title once. */
export function mergeCandidates(
	exact: Candidate[],
	others: Candidate[],
	limit = 8,
): Candidate[] {
	const seen = new Set<string>();
	const out: Candidate[] = [];
	for (const candidate of [...exact, ...others]) {
		if (seen.has(candidate.key)) continue;
		seen.add(candidate.key);
		out.push(candidate);
	}
	return out.slice(0, limit);
}

/** "inCinemas" -> "in cinemas". */
export function humanStatus(status: string | undefined): string | undefined {
	return status?.replace(/([A-Z])/g, " $1").toLowerCase();
}

/** "1983 · 132 min · Lucasfilm Ltd. · released" */
export function metaLine(candidate: Candidate): string {
	return [
		candidate.year ? String(candidate.year) : undefined,
		candidate.runtime ? `${candidate.runtime} min` : undefined,
		candidate.seasons
			? candidate.seasons === 1
				? "1 season"
				: `${candidate.seasons} seasons`
			: undefined,
		candidate.studio,
		humanStatus(candidate.status),
	]
		.filter(Boolean)
		.join(" · ");
}

function parseConnection(raw: unknown): ArrConnection {
	if (!isRecord(raw) || raw.configured !== true) return { configured: false };
	return {
		configured: true,
		url: text(raw.url),
		version: text(raw.version),
		count: typeof raw.count === "number" ? raw.count : undefined,
		error: text(raw.error),
	};
}

/** Read /arr/status. Undefined when the answer isn't the expected shape. */
export function parseArrStatus(json: unknown): ArrStatus | undefined {
	if (!isRecord(json) || !isRecord(json.apps)) return undefined;
	return {
		persistent: json.persistent === true,
		apps: {
			radarr: parseConnection(json.apps.radarr),
			sonarr: parseConnection(json.apps.sonarr),
		},
	};
}

/** "v5.14.0.9383 · 1748 movies" */
export function connectionDetail(
	app: ArrApp,
	connection: ArrConnection,
): string {
	const noun = app === "radarr" ? "movie" : "series";
	const count =
		connection.count === undefined
			? undefined
			: `${connection.count} ${noun}${connection.count === 1 || noun === "series" ? "" : "s"}`;
	return [connection.version ? `v${connection.version}` : undefined, count]
		.filter(Boolean)
		.join(" · ");
}

/** Read the quality profiles. */
export function parseProfiles(json: unknown): QualityProfile[] {
	if (!Array.isArray(json)) return [];
	return json.flatMap((row): QualityProfile[] => {
		if (!isRecord(row)) return [];
		const name = text(row.name);
		return typeof row.id === "number" && name ? [{ id: row.id, name }] : [];
	});
}

/** Read the root folders. Ones the app says it can't reach are left out. */
export function parseFolders(json: unknown): RootFolder[] {
	if (!Array.isArray(json)) return [];
	return json.flatMap((row): RootFolder[] => {
		if (!isRecord(row) || row.accessible === false) return [];
		const path = text(row.path);
		if (!path) return [];
		return [
			{
				path,
				freeSpace:
					typeof row.freeSpace === "number" && row.freeSpace >= 0
						? row.freeSpace
						: undefined,
			},
		];
	});
}

/** 4_617_089_843_200 -> "4.2 TB". Empty when the size isn't known. */
export function formatBytes(bytes: number | undefined): string {
	if (bytes === undefined || !Number.isFinite(bytes) || bytes < 0) return "";
	const units = ["B", "KB", "MB", "GB", "TB", "PB"];
	let value = bytes;
	let unit = 0;
	while (value >= 1024 && unit < units.length - 1) {
		value /= 1024;
		unit++;
	}
	const digits = unit === 0 || value >= 100 ? 0 : 1;
	return `${value.toFixed(digits)} ${units[unit]}`;
}

/** "/movies/Drama — 4.2 TB free" */
export function folderLabel(folder: RootFolder): string {
	const free = formatBytes(folder.freeSpace);
	return free ? `${folder.path} — ${free} free` : folder.path;
}

/**
 * The settings the dialog starts with: what the person chose last time if it still exists,
 * otherwise the first folder and profile on offer. Undefined when the app has no folders or no
 * profiles at all (nothing can be added then).
 */
export function resolveChoices(
	options: ArrOptions,
	prefs: ArrPrefs,
	candidate?: Candidate,
): Choices | undefined {
	const folder =
		options.folders.find((f) => f.path === prefs.rootFolder) ??
		options.folders[0];
	const profile =
		options.profiles.find((p) => p.id === prefs.qualityProfileId) ??
		options.profiles[0];
	if (!folder || !profile) return undefined;

	return {
		rootFolder: folder.path,
		qualityProfileId: profile.id,
		minimumAvailability: prefs.minimumAvailability ?? "released",
		monitor: prefs.monitor ?? "all",
		seriesType:
			prefs.seriesType ??
			oneOf(candidate?.raw.seriesType, SERIES_TYPES) ??
			"standard",
		search: prefs.search ?? true,
	};
}

/** What to send to Radarr to add a movie. */
export function buildMovieBody(
	candidate: Candidate,
	choices: Choices,
): Record<string, unknown> {
	const { id: _id, ...rest } = candidate.raw;
	return {
		...rest,
		qualityProfileId: choices.qualityProfileId,
		rootFolderPath: choices.rootFolder,
		monitored: true,
		minimumAvailability: choices.minimumAvailability,
		addOptions: { monitor: "movieOnly", searchForMovie: choices.search },
	};
}

/** What to send to Sonarr to add a series. */
export function buildSeriesBody(
	candidate: Candidate,
	choices: Choices,
): Record<string, unknown> {
	const { id: _id, ...rest } = candidate.raw;
	return {
		...rest,
		qualityProfileId: choices.qualityProfileId,
		rootFolderPath: choices.rootFolder,
		monitored: true,
		seasonFolder: true,
		seriesType: choices.seriesType,
		addOptions: {
			monitor: choices.monitor,
			searchForMissingEpisodes: choices.search,
			searchForCutoffUnmetEpisodes: false,
		},
	};
}

export function buildAddBody(
	app: ArrApp,
	candidate: Candidate,
	choices: Choices,
): Record<string, unknown> {
	return app === "radarr"
		? buildMovieBody(candidate, choices)
		: buildSeriesBody(candidate, choices);
}

/** The app's search words for a title: movies are found best with the year. */
export function searchTerm(
	app: ArrApp,
	title: string,
	year: number | undefined,
): string {
	return app === "radarr" && year ? `${title} ${year}` : title;
}

/** The app's own reason for a refusal, when it gave one. Both apps answer 400s with a list. */
function reasonFrom(body: string): string | undefined {
	try {
		const json: unknown = JSON.parse(body);
		if (Array.isArray(json)) {
			for (const entry of json) {
				if (isRecord(entry) && text(entry.errorMessage)) {
					return text(entry.errorMessage);
				}
			}
		}
		if (isRecord(json)) return text(json.message) ?? text(json.error);
	} catch {
		// Not JSON.
	}
	return undefined;
}

/** What went wrong, in words a person can act on. */
export function arrErrorMessage(
	app: ArrApp,
	status: number,
	body: string,
): string {
	const label = ARR_LABEL[app];
	if (status === 401 || status === 403) {
		return `Only Jellyfin administrators can add to ${label}. Sign in as one and try again.`;
	}
	// 0 means the request never got an answer at all.
	if ([0, 404, 502, 503, 504].includes(status)) {
		return `Fancyfin couldn't reach ${label}. Check its address in Settings.`;
	}
	const reason = reasonFrom(body);
	if (reason) return reason;
	return `${label} didn't accept that (error ${status}).`;
}
