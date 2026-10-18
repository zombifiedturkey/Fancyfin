import { describe, expect, it } from "vitest";
import {
	matchGenreIds,
	parseGenres,
	parseSearch,
	parseTrending,
	parseTvdbId,
	tmdbImage,
	tmdbPage,
} from "./tmdb";

describe("parseTrending", () => {
	const movie = {
		id: 1,
		title: "Zog",
		overview: "A dragon.",
		backdrop_path: "/a.jpg",
		poster_path: "/p.jpg",
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
				poster: "/p.jpg",
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
		expect(show?.poster).toBeUndefined();
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

describe("parseTvdbId", () => {
	it("reads the id, and nothing when there isn't one", () => {
		expect(parseTvdbId({ tvdb_id: 393189 })).toBe(393189);
		expect(parseTvdbId({ tvdb_id: null })).toBeUndefined();
		expect(parseTvdbId(null)).toBeUndefined();
	});
});

describe("parseSearch", () => {
	it("keeps movies and shows, drops people, and doesn't need a backdrop", () => {
		const titles = parseSearch({
			results: [
				{
					id: 1,
					media_type: "movie",
					title: "Dune",
					release_date: "2021-10-01",
					poster_path: "/d.jpg",
				},
				{
					id: 2,
					media_type: "tv",
					name: "Dune: Prophecy",
					first_air_date: "2024-11-17",
				},
				{ id: 3, media_type: "person", name: "Dune Person" },
				{ id: 4, media_type: "movie", title: "" },
				{ id: "5", media_type: "movie", title: "x" },
			],
		});
		expect(titles.map((t) => [t.kind, t.id, t.title, t.year])).toEqual([
			["movie", 1, "Dune", 2021],
			["tv", 2, "Dune: Prophecy", 2024],
		]);
		expect(titles[0]?.poster).toBe("/d.jpg");
		expect(titles[1]?.poster).toBeUndefined();
		expect(parseSearch(null)).toEqual([]);
	});
});

describe("genres", () => {
	const movie = parseGenres({
		genres: [
			{ id: 28, name: "Action" },
			{ id: 878, name: "Science Fiction" },
			{ id: 99, name: "Documentary" },
			{ bad: true },
		],
	});
	const tv = parseGenres({
		genres: [
			{ id: 10759, name: "Action & Adventure" },
			{ id: 10765, name: "Sci-Fi & Fantasy" },
			{ id: 35, name: "Comedy" },
		],
	});

	it("reads TMDB's list", () => {
		expect(movie).toHaveLength(3);
		expect(parseGenres(null)).toEqual([]);
	});

	it("matches names exactly, ignoring case", () => {
		expect(matchGenreIds("action", movie)).toEqual([28]);
		expect(matchGenreIds("Comedy", tv)).toEqual([35]);
	});

	it("matches either half of TMDB's combined TV genres", () => {
		expect(matchGenreIds("Action", tv)).toEqual([10759]);
		expect(matchGenreIds("Adventure", tv)).toEqual([10759]);
		expect(matchGenreIds("Fantasy", tv)).toEqual([10765]);
		expect(matchGenreIds("Science Fiction", tv)).toEqual([10765]);
		expect(matchGenreIds("Sci-Fi", movie)).toEqual([878]);
	});

	it("gives nothing for a genre TMDB doesn't have", () => {
		expect(matchGenreIds("Anime", movie)).toEqual([]);
		expect(matchGenreIds("", movie)).toEqual([]);
	});
});
