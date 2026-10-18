import { describe, expect, it } from "vitest";
import {
	parseRelated,
	parseTmdbDetail,
	parseTmdbPeekId,
	tmdbPeekId,
} from "./tmdb-detail";

const now = new Date("2026-10-18T04:00:00Z");

describe("peek ids", () => {
	it("round-trips", () => {
		expect(tmdbPeekId("movie", 12)).toBe("tmdb:movie:12");
		expect(parseTmdbPeekId("tmdb:tv:99")).toEqual({ kind: "tv", id: 99 });
		expect(parseTmdbPeekId("tmdb:movie:5")).toEqual({ kind: "movie", id: 5 });
	});

	it("rejects library ids and junk", () => {
		expect(parseTmdbPeekId("0f3a9c2b7d")).toBeUndefined();
		expect(parseTmdbPeekId("tmdb:book:1")).toBeUndefined();
		expect(parseTmdbPeekId("tmdb:movie:x")).toBeUndefined();
	});
});

const movie = {
	id: 77,
	title: "Other Mommy",
	tagline: "She looks like mum.",
	overview: "A sinister entity.",
	backdrop_path: "/b.jpg",
	poster_path: "/p.jpg",
	release_date: "2026-11-20",
	runtime: 98,
	vote_average: 5.94,
	vote_count: 120,
	status: "Post Production",
	genres: [{ name: "Horror" }, { name: "Thriller" }],
	spoken_languages: [{ english_name: "English" }],
	production_companies: [{ name: "Studio A" }, { name: "Studio B" }],
	credits: {
		cast: [
			{ id: 1, name: "Ana", character: "Bela", profile_path: "/a.jpg" },
			{ id: 2, name: "Bo", character: "" },
			{ id: 3 },
		],
		crew: [
			{ job: "Director", name: "Dee" },
			{ job: "Writer", name: "Wes" },
		],
	},
	recommendations: {
		results: [
			{
				id: 9,
				title: "Another",
				poster_path: "/x.jpg",
				release_date: "2025-01-02",
				media_type: "movie",
			},
			{ id: 10, title: "No poster" },
		],
	},
	release_dates: {
		results: [
			{
				iso_3166_1: "AU",
				release_dates: [
					{
						type: 3,
						certification: "MA15+",
						release_date: "2026-11-20T00:00:00.000Z",
					},
				],
			},
		],
	},
};

describe("parseTmdbDetail", () => {
	it("reads a film", () => {
		const detail = parseTmdbDetail("movie", movie, now);
		expect(detail).toMatchObject({
			id: 77,
			title: "Other Mommy",
			tagline: "She looks like mum.",
			year: 2026,
			runtime: 98,
			genres: ["Horror", "Thriller"],
			certification: "MA15+",
			language: "English",
			studios: ["Studio A", "Studio B"],
			makers: ["Dee"],
			votes: 120,
		});
		expect(detail?.cast).toEqual([
			{ id: 1, name: "Ana", role: "Bela", photo: "/a.jpg" },
			{ id: 2, name: "Bo", role: undefined, photo: undefined },
		]);
		expect(detail?.related.map((r) => r.id)).toEqual([9]);
		expect(detail?.release?.status).toBe("Releases 20 Nov 2026");
	});

	it("reads a show, with its creators and network", () => {
		const detail = parseTmdbDetail(
			"tv",
			{
				id: 5,
				name: "Andor",
				first_air_date: "2022-09-21",
				status: "Ended",
				episode_run_time: [45],
				number_of_seasons: 2,
				number_of_episodes: 24,
				created_by: [{ name: "Tony" }],
				networks: [{ name: "Disney+" }],
				content_ratings: { results: [{ iso_3166_1: "US", rating: "TV-14" }] },
			},
			now,
		);
		expect(detail).toMatchObject({
			title: "Andor",
			runtime: 45,
			seasons: 2,
			episodes: 24,
			makers: ["Tony"],
			studios: ["Disney+"],
			certification: "TV-14",
		});
		expect(detail?.release?.status).toBe("Ended");
	});

	it("gives nothing for nonsense", () => {
		expect(parseTmdbDetail("movie", null, now)).toBeUndefined();
		expect(parseTmdbDetail("movie", { id: 1 }, now)).toBeUndefined();
	});
});

describe("parseRelated", () => {
	it("keeps titles with a poster and uses the kind it was given", () => {
		const rows = parseRelated("tv", {
			results: [
				{
					id: 1,
					name: "Show",
					poster_path: "/p.jpg",
					first_air_date: "2020-01-01",
				},
			],
		});
		expect(rows).toHaveLength(1);
		expect(rows[0]).toMatchObject({ kind: "tv", title: "Show", year: 2020 });
	});
});
