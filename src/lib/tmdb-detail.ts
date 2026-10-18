import { parseMovieRelease, parseTvRelease, type ReleaseInfo } from "./release";
import type { TmdbKind, TmdbTitle } from "./tmdb";

/** A title that isn't in the library has its own id in the address: tmdb:movie:123 or tmdb:tv:456. */
export function tmdbPeekId(kind: TmdbKind, id: number): string {
	return `tmdb:${kind}:${id}`;
}

export function parseTmdbPeekId(
	peek: string,
): { kind: TmdbKind; id: number } | undefined {
	const match = /^tmdb:(movie|tv):(\d+)$/.exec(peek);
	if (!match) return undefined;
	return { kind: match[1] === "tv" ? "tv" : "movie", id: Number(match[2]) };
}

export type TmdbPerson = {
	id: number;
	name: string;
	role?: string;
	/** TMDB image path of a photo. */
	photo?: string;
};

/** Everything the detail panel shows for a title the library doesn't have. */
export type TmdbDetail = {
	id: number;
	kind: TmdbKind;
	title: string;
	tagline?: string;
	overview: string;
	backdrop?: string;
	poster?: string;
	year?: number;
	/** Date it came out or will, as TMDB gives it (YYYY-MM-DD). */
	date?: string;
	/** Minutes: the film, or a typical episode. */
	runtime?: number;
	genres: string[];
	rating?: number;
	votes?: number;
	/** Age rating, like "MA15+" or "PG-13". */
	certification?: string;
	status?: string;
	language?: string;
	seasons?: number;
	episodes?: number;
	/** Production companies, or networks for a show. */
	studios: string[];
	/** Directors, or creators for a show. */
	makers: string[];
	cast: TmdbPerson[];
	related: TmdbTitle[];
	release?: ReleaseInfo;
};

const CAST_LIMIT = 20;
const RELATED_LIMIT = 16;
/** Ratings for these countries win, in this order. */
const REGIONS = ["AU", "US"];

function record(value: unknown): Record<string, unknown> | undefined {
	return typeof value === "object" && value !== null
		? (value as Record<string, unknown>)
		: undefined;
}

function str(value: unknown): string {
	return typeof value === "string" ? value.trim() : "";
}

function num(value: unknown): number | undefined {
	return typeof value === "number" && Number.isFinite(value)
		? value
		: undefined;
}

function list(value: unknown): Record<string, unknown>[] {
	return Array.isArray(value)
		? value.flatMap((row) => {
				const r = record(row);
				return r ? [r] : [];
			})
		: [];
}

function names(value: unknown): string[] {
	return list(value)
		.map((row) => str(row.name))
		.filter(Boolean);
}

function yearOf(date: string): number | undefined {
	const year = Number.parseInt(date.slice(0, 4), 10);
	return Number.isNaN(year) ? undefined : year;
}

/** TMDB's list of related titles. Pictures and descriptions are optional here. */
export function parseRelated(kind: TmdbKind, json: unknown): TmdbTitle[] {
	return list(record(json)?.results)
		.flatMap((row): TmdbTitle[] => {
			const rowKind: TmdbKind =
				row.media_type === "tv"
					? "tv"
					: row.media_type === "movie"
						? "movie"
						: kind;
			const id = num(row.id);
			const title = str(rowKind === "movie" ? row.title : row.name);
			const poster = str(row.poster_path);
			if (id === undefined || !title || !poster) return [];
			return [
				{
					id,
					kind: rowKind,
					title,
					overview: str(row.overview),
					backdrop: str(row.backdrop_path),
					poster,
					year: yearOf(
						str(rowKind === "movie" ? row.release_date : row.first_air_date),
					),
					popularity: num(row.popularity) ?? 0,
				},
			];
		})
		.slice(0, RELATED_LIMIT);
}

/** The age rating: a film's release_dates entries, or a show's content_ratings. */
function certificationOf(
	kind: TmdbKind,
	json: Record<string, unknown>,
): string | undefined {
	if (kind === "tv") {
		const rows = list(record(json.content_ratings)?.results);
		for (const country of REGIONS) {
			const found = rows.find(
				(row) => row.iso_3166_1 === country && str(row.rating),
			);
			if (found) return str(found.rating);
		}
		return undefined;
	}
	const rows = list(record(json.release_dates)?.results);
	for (const country of REGIONS) {
		const entries = list(
			rows.find((row) => row.iso_3166_1 === country)?.release_dates,
		);
		// Cinema releases carry the rating most reliably.
		const found =
			entries.find((entry) => entry.type === 3 && str(entry.certification)) ??
			entries.find((entry) => str(entry.certification));
		if (found) return str(found.certification);
	}
	return undefined;
}

/**
 * Read a TMDB movie or show fetched with append_to_response=credits,recommendations plus
 * release_dates (films) or content_ratings (shows).
 */
export function parseTmdbDetail(
	kind: TmdbKind,
	json: unknown,
	now: Date,
): TmdbDetail | undefined {
	const data = record(json);
	const id = num(data?.id);
	if (!data || id === undefined) return undefined;
	const title = str(kind === "movie" ? data.title : data.name);
	if (!title) return undefined;

	const date = str(kind === "movie" ? data.release_date : data.first_air_date);
	const credits = record(data.credits);
	const crew = list(credits?.crew);

	const runtimes = Array.isArray(data.episode_run_time)
		? data.episode_run_time
		: [];
	const runtime =
		kind === "movie"
			? num(data.runtime)
			: (num(runtimes[0]) ?? num(record(data.last_episode_to_air)?.runtime));

	const cast = list(credits?.cast)
		.flatMap((row): TmdbPerson[] => {
			const personId = num(row.id);
			const name = str(row.name);
			return personId === undefined || !name
				? []
				: [
						{
							id: personId,
							name,
							role: str(row.character) || undefined,
							photo: str(row.profile_path) || undefined,
						},
					];
		})
		.slice(0, CAST_LIMIT);

	const languages = list(data.spoken_languages);

	return {
		id,
		kind,
		title,
		tagline: str(data.tagline) || undefined,
		overview: str(data.overview),
		backdrop: str(data.backdrop_path) || undefined,
		poster: str(data.poster_path) || undefined,
		year: yearOf(date),
		date: date || undefined,
		runtime: runtime && runtime > 0 ? runtime : undefined,
		genres: names(data.genres),
		rating:
			(num(data.vote_average) ?? 0) > 0 ? num(data.vote_average) : undefined,
		votes: (num(data.vote_count) ?? 0) > 0 ? num(data.vote_count) : undefined,
		certification: certificationOf(kind, data),
		status: str(data.status) || undefined,
		language: str(languages[0]?.english_name) || undefined,
		seasons: kind === "tv" ? num(data.number_of_seasons) : undefined,
		episodes: kind === "tv" ? num(data.number_of_episodes) : undefined,
		studios:
			kind === "movie"
				? names(data.production_companies)
				: names(data.networks),
		makers:
			kind === "movie"
				? crew
						.filter((row) => row.job === "Director")
						.map((row) => str(row.name))
						.filter(Boolean)
				: names(data.created_by),
		cast,
		related: parseRelated(kind, data.recommendations),
		release:
			(kind === "movie"
				? parseMovieRelease(json, now)
				: parseTvRelease(json, now)) ?? undefined,
	};
}

/** The append_to_response value for each kind. */
export function appendFor(kind: TmdbKind): string {
	return kind === "movie"
		? "credits,recommendations,release_dates"
		: "credits,recommendations,content_ratings";
}
