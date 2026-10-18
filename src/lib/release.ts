/** Where a title that isn't in the library is in its life: in cinemas, on streaming, still to come. */
export type ReleaseInfo = {
	/** The headline, like "In cinemas now" or "Releases 20 Nov 2026". */
	status: string;
	/** What's next for streaming or disc, like "Streaming expected 14 Nov 2026". */
	detail?: string;
};

const DAY = 24 * 60 * 60 * 1000;
/** A cinema run is assumed to last about this long. */
const IN_CINEMAS_DAYS = 75;
/** Newer than this, a missing streaming or disc date is worth mentioning. */
const RECENT_DAYS = 120;
/** Dates for these countries win, in this order; anywhere else is a fallback. */
const REGIONS = ["AU", "US"];

const THEATRICAL = [1, 2, 3];
const STREAMING = 4;
const DISC = 5;

type Entry = { type: number; time: number };

function asRecord(value: unknown): Record<string, unknown> | undefined {
	return typeof value === "object" && value !== null
		? (value as Record<string, unknown>)
		: undefined;
}

/** A date TMDB gave, as a time in ms. Date-only values are midnight UTC. */
function parseTime(value: unknown): number | undefined {
	if (typeof value !== "string" || !value) return undefined;
	const time = Date.parse(value);
	return Number.isNaN(time) ? undefined : time;
}

/** "20 Nov 2026". The dates have no time of day, so they are shown as given, in any time zone. */
export function formatReleaseDate(time: number): string {
	return new Intl.DateTimeFormat("en-AU", {
		day: "numeric",
		month: "short",
		year: "numeric",
		timeZone: "UTC",
	})
		.format(new Date(time))
		.replace(".", "");
}

/** Every release entry TMDB lists for a movie, by country. */
function entriesByCountry(json: unknown): Map<string, Entry[]> {
	const out = new Map<string, Entry[]>();
	const results = asRecord(asRecord(json)?.release_dates)?.results;
	if (!Array.isArray(results)) return out;
	for (const raw of results) {
		const row = asRecord(raw);
		const country = row?.iso_3166_1;
		const dates = row?.release_dates;
		if (typeof country !== "string" || !Array.isArray(dates)) continue;
		const entries: Entry[] = [];
		for (const d of dates) {
			const date = asRecord(d);
			const time = parseTime(date?.release_date);
			if (typeof date?.type === "number" && time !== undefined) {
				entries.push({ type: date.type, time });
			}
		}
		out.set(country, [...(out.get(country) ?? []), ...entries]);
	}
	return out;
}

/** Entries of the given types: from the first preferred country that has any, else from anywhere. */
function pick(byCountry: Map<string, Entry[]>, types: number[]): Entry[] {
	const wanted = (entry: Entry) => types.includes(entry.type);
	for (const country of REGIONS) {
		const found = (byCountry.get(country) ?? []).filter(wanted);
		if (found.length > 0) return found.sort((a, b) => a.time - b.time);
	}
	return [...byCountry.values()]
		.flat()
		.filter(wanted)
		.sort((a, b) => a.time - b.time);
}

function homeLabel(types: Set<number>): string {
	if (types.has(STREAMING) && types.has(DISC)) return "streaming and disc";
	return types.has(STREAMING) ? "streaming" : "disc";
}

const MOVIE_STATUS: Record<string, string> = {
	Rumored: "Rumoured",
	Planned: "Planned",
	"In Production": "In production",
	"Post Production": "Post-production",
	Canceled: "Cancelled",
};

/**
 * Read a TMDB movie (fetched with append_to_response=release_dates). Says whether it is out, and if
 * it isn't on streaming or disc yet, when it is expected there, whichever comes first.
 */
export function parseMovieRelease(
	json: unknown,
	now: Date,
): ReleaseInfo | undefined {
	const movie = asRecord(json);
	if (!movie) return undefined;
	const status = typeof movie.status === "string" ? movie.status : "";
	if (status === "Canceled") return { status: "Cancelled" };

	const nowMs = now.getTime();
	const byCountry = entriesByCountry(json);
	const home = pick(byCountry, [STREAMING, DISC]);

	// Already on streaming or disc.
	const out = home.filter((entry) => entry.time <= nowMs);
	if (out.length > 0) {
		return {
			status: "Released",
			detail: `Out now on ${homeLabel(new Set(out.map((entry) => entry.type)))}`,
		};
	}

	const next = home[0];
	const homeDetail = next
		? `${homeLabel(
				new Set(
					home.filter((e) => e.time === next.time).map((entry) => entry.type),
				),
			).replace(/^./, (c) =>
				c.toUpperCase(),
			)} expected ${formatReleaseDate(next.time)}`
		: undefined;

	const cinema =
		pick(byCountry, THEATRICAL)[0]?.time ?? parseTime(movie.release_date);

	if (cinema === undefined) {
		if (homeDetail) return { status: "Coming soon", detail: homeDetail };
		const text = MOVIE_STATUS[status];
		return text ? { status: text } : undefined;
	}

	if (cinema > nowMs) {
		return {
			status: `Releases ${formatReleaseDate(cinema)}`,
			detail: homeDetail ?? "No streaming or disc date yet",
		};
	}

	const age = nowMs - cinema;
	return {
		status: age <= IN_CINEMAS_DAYS * DAY ? "In cinemas now" : "Released",
		detail:
			homeDetail ??
			(age <= RECENT_DAYS * DAY ? "No streaming or disc date yet" : undefined),
	};
}

const TV_STATUS: Record<string, string> = {
	"Returning Series": "Returning series",
	Ended: "Ended",
	Canceled: "Cancelled",
	"In Production": "In production",
	Planned: "Planned",
	Pilot: "Pilot",
};

/** Read a TMDB show: whether it is airing, finished or still to come, and when it is next on. */
export function parseTvRelease(
	json: unknown,
	now: Date,
): ReleaseInfo | undefined {
	const show = asRecord(json);
	if (!show) return undefined;
	const status = typeof show.status === "string" ? show.status : "";
	if (status === "Canceled") return { status: "Cancelled" };

	const first = parseTime(show.first_air_date);
	if (first !== undefined && first > now.getTime()) {
		return { status: `Premieres ${formatReleaseDate(first)}` };
	}

	const text = TV_STATUS[status];
	if (!text) return undefined;
	if (status !== "Returning Series") return { status: text };

	const nextEpisode = parseTime(asRecord(show.next_episode_to_air)?.air_date);
	return {
		status: text,
		detail:
			nextEpisode !== undefined && nextEpisode > now.getTime()
				? `Next episode ${formatReleaseDate(nextEpisode)}`
				: "Next episode not announced",
	};
}

/** The genre names in a TMDB movie or show. */
export function parseGenreNames(json: unknown): string[] {
	const genres = (json as { genres?: unknown } | null)?.genres;
	if (!Array.isArray(genres)) return [];
	return genres.flatMap((row) => {
		const name = (row as { name?: unknown } | null)?.name;
		return typeof name === "string" && name.trim() ? [name.trim()] : [];
	});
}
