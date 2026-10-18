import { describe, expect, it } from "vitest";
import { parseTrending, tmdbImage, tmdbPage } from "./tmdb";

describe("parseTrending", () => {
	const movie = {
		id: 1,
		title: "Zog",
		overview: "A dragon.",
		backdrop_path: "/a.jpg",
		release_date: "2026-05-01",
		vote_average: 7.84,
		popularity: 90.5,
	};

	it("reads movies", () => {
		expect(parseTrending("movie", { results: [movie] })).toEqual([
			{
				id: 1,
				kind: "movie",
				title: "Zog",
				overview: "A dragon.",
				backdrop: "/a.jpg",
				year: 2026,
				rating: 7.84,
				popularity: 90.5,
			},
		]);
	});

	it("reads shows by name and first air date", () => {
		const [show] = parseTrending("tv", {
			results: [
				{
					id: 2,
					name: "Crystal Lake",
					overview: "x",
					backdrop_path: "/b.jpg",
					first_air_date: "2024-01-02",
					vote_average: 0,
				},
			],
		});
		expect(show?.title).toBe("Crystal Lake");
		expect(show?.year).toBe(2024);
		expect(show?.rating).toBeUndefined();
	});

	it("skips entries with no picture, no description or no id, and bad input", () => {
		expect(
			parseTrending("movie", {
				results: [
					{ ...movie, backdrop_path: null },
					{ ...movie, overview: "" },
					{ ...movie, id: "3" },
					null,
				],
			}),
		).toEqual([]);
		expect(parseTrending("movie", null)).toEqual([]);
		expect(parseTrending("movie", { results: "no" })).toEqual([]);
	});
});

describe("links", () => {
	it("builds image and page addresses", () => {
		expect(tmdbImage("/a.jpg", "w1280")).toBe(
			"https://image.tmdb.org/t/p/w1280/a.jpg",
		);
		expect(
			tmdbPage({
				id: 5,
				kind: "tv",
				title: "x",
				overview: "x",
				backdrop: "/a",
				popularity: 1,
			}),
		).toBe("https://www.themoviedb.org/tv/5");
	});
});
