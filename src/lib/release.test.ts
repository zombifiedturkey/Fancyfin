import { describe, expect, it } from "vitest";
import {
	formatReleaseDate,
	parseMovieRelease,
	parseTvRelease,
} from "./release";

const now = new Date("2026-10-18T04:00:00Z");

function movie(
	status: string,
	dates: Record<string, [number, string][]>,
	release_date?: string,
) {
	return {
		status,
		release_date,
		release_dates: {
			results: Object.entries(dates).map(([country, list]) => ({
				iso_3166_1: country,
				release_dates: list.map(([type, date]) => ({
					type,
					release_date: `${date}T00:00:00.000Z`,
				})),
			})),
		},
	};
}

describe("formatReleaseDate", () => {
	it("shows the date as given", () => {
		expect(formatReleaseDate(Date.parse("2026-11-20T00:00:00Z"))).toBe(
			"20 Nov 2026",
		);
	});
});

describe("parseMovieRelease", () => {
	it("says it is out when streaming or disc has happened", () => {
		expect(
			parseMovieRelease(
				movie("Released", {
					AU: [
						[3, "2026-05-01"],
						[4, "2026-07-01"],
					],
				}),
				now,
			),
		).toEqual({ status: "Released", detail: "Out now on streaming" });
		expect(
			parseMovieRelease(
				movie("Released", {
					US: [
						[4, "2026-07-01"],
						[5, "2026-07-01"],
					],
				}),
				now,
			)?.detail,
		).toBe("Out now on streaming and disc");
	});

	it("shows the first of streaming and disc while it is in cinemas", () => {
		expect(
			parseMovieRelease(
				movie("Released", {
					AU: [
						[3, "2026-10-01"],
						[5, "2026-12-20"],
						[4, "2026-12-01"],
					],
				}),
				now,
			),
		).toEqual({
			status: "In cinemas now",
			detail: "Streaming expected 1 Dec 2026",
		});
		expect(
			parseMovieRelease(
				movie("Released", {
					AU: [
						[3, "2026-10-01"],
						[5, "2026-11-20"],
					],
				}),
				now,
			)?.detail,
		).toBe("Disc expected 20 Nov 2026");
	});

	it("prefers Australian dates, then US, then anywhere", () => {
		const info = parseMovieRelease(
			movie("Released", {
				US: [
					[3, "2026-10-01"],
					[4, "2026-11-01"],
				],
				AU: [
					[3, "2026-10-02"],
					[4, "2026-12-05"],
				],
			}),
			now,
		);
		expect(info?.detail).toBe("Streaming expected 5 Dec 2026");
		const fallback = parseMovieRelease(
			movie("Released", {
				FR: [
					[3, "2026-10-01"],
					[4, "2027-01-05"],
				],
			}),
			now,
		);
		expect(fallback?.detail).toBe("Streaming expected 5 Jan 2027");
	});

	it("handles a film not yet in cinemas", () => {
		expect(
			parseMovieRelease(
				movie("Post Production", { AU: [[3, "2026-11-20"]] }),
				now,
			),
		).toEqual({
			status: "Releases 20 Nov 2026",
			detail: "No streaming or disc date yet",
		});
	});

	it("says no date yet only for fairly new releases", () => {
		expect(
			parseMovieRelease(movie("Released", { AU: [[3, "2026-09-20"]] }), now),
		).toEqual({
			status: "In cinemas now",
			detail: "No streaming or disc date yet",
		});
		expect(
			parseMovieRelease(movie("Released", { AU: [[3, "2025-01-20"]] }), now),
		).toEqual({ status: "Released", detail: undefined });
	});

	it("uses TMDB's own release date and status when there are no listed dates", () => {
		expect(
			parseMovieRelease(movie("Released", {}, "2026-12-25"), now)?.status,
		).toBe("Releases 25 Dec 2026");
		expect(parseMovieRelease(movie("In Production", {}), now)).toEqual({
			status: "In production",
		});
		expect(parseMovieRelease(movie("Canceled", {}), now)).toEqual({
			status: "Cancelled",
		});
		expect(parseMovieRelease(null, now)).toBeUndefined();
	});

	it("handles a streaming-only release that hasn't happened", () => {
		expect(
			parseMovieRelease(movie("Planned", { US: [[4, "2026-11-11"]] }), now),
		).toEqual({
			status: "Coming soon",
			detail: "Streaming expected 11 Nov 2026",
		});
	});
});

describe("parseTvRelease", () => {
	it("describes a returning series and its next episode", () => {
		expect(
			parseTvRelease(
				{
					status: "Returning Series",
					first_air_date: "2022-01-01",
					next_episode_to_air: { air_date: "2026-11-02" },
				},
				now,
			),
		).toEqual({
			status: "Returning series",
			detail: "Next episode 2 Nov 2026",
		});
		expect(
			parseTvRelease(
				{ status: "Returning Series", first_air_date: "2022-01-01" },
				now,
			)?.detail,
		).toBe("Next episode not announced");
	});

	it("describes ended, cancelled and not-yet-aired shows", () => {
		expect(parseTvRelease({ status: "Ended" }, now)).toEqual({
			status: "Ended",
		});
		expect(parseTvRelease({ status: "Canceled" }, now)).toEqual({
			status: "Cancelled",
		});
		expect(
			parseTvRelease(
				{ status: "In Production", first_air_date: "2027-02-01" },
				now,
			),
		).toEqual({ status: "Premieres 1 Feb 2027" });
		expect(parseTvRelease({ status: "Whatever" }, now)).toBeUndefined();
	});
});
