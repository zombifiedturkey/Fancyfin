import { describe, expect, it } from "vitest";
import {
	arrErrorMessage,
	buildMovieBody,
	buildSeriesBody,
	type Candidate,
	type Choices,
	connectionDetail,
	folderLabel,
	formatBytes,
	mergeCandidates,
	metaLine,
	parseArrStatus,
	parseCandidates,
	parseFolders,
	parseProfiles,
	resolveChoices,
	sanitizePrefs,
	searchTerm,
} from "./arr";

const jedi = {
	title: "Return of the Jedi",
	year: 1983,
	runtime: 132,
	studio: "Lucasfilm Ltd.",
	status: "released",
	overview: "The Rebels strike back.",
	tmdbId: 1892,
	images: [
		{ coverType: "fanart", remoteUrl: "https://image.tmdb.org/f.jpg" },
		{ coverType: "poster", remoteUrl: "https://image.tmdb.org/p.jpg" },
	],
};

const choices: Choices = {
	rootFolder: "/movies/Drama",
	qualityProfileId: 4,
	minimumAvailability: "released",
	monitor: "all",
	seriesType: "standard",
	search: true,
};

describe("parseCandidates", () => {
	it("reads a movie", () => {
		const [movie] = parseCandidates("radarr", [jedi]);
		expect(movie).toMatchObject({
			key: "1892",
			title: "Return of the Jedi",
			year: 1983,
			runtime: 132,
			studio: "Lucasfilm Ltd.",
			poster: "https://image.tmdb.org/p.jpg",
			tmdbId: 1892,
		});
		expect(movie?.existingId).toBeUndefined();
	});

	it("reads a series, with its network and real seasons", () => {
		const [show] = parseCandidates("sonarr", [
			{
				title: "Andor",
				year: 2022,
				network: "Disney+",
				status: "continuing",
				tvdbId: 393189,
				tmdbId: 83867,
				id: 12,
				seasons: [
					{ seasonNumber: 0 },
					{ seasonNumber: 1 },
					{ seasonNumber: 2 },
				],
			},
		]);
		expect(show?.key).toBe("393189");
		expect(show?.studio).toBe("Disney+");
		expect(show?.seasons).toBe(2);
		expect(show?.existingId).toBe(12);
	});

	it("skips rows without a title or the id the app uses, and bad input", () => {
		expect(
			parseCandidates("radarr", [{ title: "x" }, { tmdbId: 5 }, null, "no"]),
		).toEqual([]);
		expect(parseCandidates("sonarr", [{ title: "x", tmdbId: 5 }])).toEqual([]);
		expect(parseCandidates("radarr", { nope: true })).toEqual([]);
	});

	it("ignores picture addresses the browser couldn't open", () => {
		const [movie] = parseCandidates("radarr", [
			{ ...jedi, images: [{ coverType: "poster", url: "/MediaCover/1.jpg" }] },
		]);
		expect(movie?.poster).toBeUndefined();
	});
});

describe("mergeCandidates", () => {
	const make = (key: string): Candidate => ({ key, title: key, raw: {} });
	it("puts the exact match first and drops repeats", () => {
		const merged = mergeCandidates(
			[make("2")],
			[make("1"), make("2"), make("3")],
		);
		expect(merged.map((c) => c.key)).toEqual(["2", "1", "3"]);
	});

	it("caps the list", () => {
		const many = Array.from({ length: 20 }, (_, i) => make(String(i)));
		expect(mergeCandidates([], many, 5)).toHaveLength(5);
	});
});

describe("display", () => {
	it("builds the meta line", () => {
		const [movie] = parseCandidates("radarr", [jedi]);
		expect(movie && metaLine(movie)).toBe(
			"1983 · 132 min · Lucasfilm Ltd. · released",
		);
		expect(
			metaLine({ key: "1", title: "x", status: "inCinemas", raw: {} }),
		).toBe("in cinemas");
	});

	it("formats sizes", () => {
		expect(formatBytes(4.2 * 1024 ** 4)).toBe("4.2 TB");
		expect(formatBytes(512 * 1024 ** 3)).toBe("512 GB");
		expect(formatBytes(7.6 * 1024 ** 3)).toBe("7.6 GB");
		expect(formatBytes(undefined)).toBe("");
		expect(formatBytes(-1)).toBe("");
	});

	it("labels folders with their free space", () => {
		expect(
			folderLabel({ path: "/movies/Drama", freeSpace: 4.2 * 1024 ** 4 }),
		).toBe("/movies/Drama — 4.2 TB free");
		expect(folderLabel({ path: "/movies" })).toBe("/movies");
	});

	it("searches movies with the year and series without", () => {
		expect(searchTerm("radarr", "Dune", 2021)).toBe("Dune 2021");
		expect(searchTerm("radarr", "Dune", undefined)).toBe("Dune");
		expect(searchTerm("sonarr", "Andor", 2022)).toBe("Andor");
	});
});

describe("options", () => {
	it("reads profiles and folders", () => {
		expect(
			parseProfiles([{ id: 4, name: "HD-1080p" }, { id: "x" }, null]),
		).toEqual([{ id: 4, name: "HD-1080p" }]);
		expect(
			parseFolders([
				{ path: "/movies", freeSpace: 10 },
				{ path: "/gone", accessible: false },
				{ path: "" },
			]),
		).toEqual([{ path: "/movies", freeSpace: 10 }]);
		expect(parseProfiles("no")).toEqual([]);
	});
});

describe("resolveChoices", () => {
	const options = {
		profiles: [
			{ id: 1, name: "Any" },
			{ id: 4, name: "HD-1080p" },
		],
		folders: [{ path: "/movies" }, { path: "/movies/Drama" }],
	};

	it("starts from the first folder and profile, searching on", () => {
		expect(resolveChoices(options, { skip: false })).toEqual({
			rootFolder: "/movies",
			qualityProfileId: 1,
			minimumAvailability: "released",
			monitor: "all",
			seriesType: "standard",
			search: true,
		});
	});

	it("keeps what was chosen last time while it still exists", () => {
		const result = resolveChoices(options, {
			skip: true,
			rootFolder: "/movies/Drama",
			qualityProfileId: 4,
			search: false,
		});
		expect(result).toMatchObject({
			rootFolder: "/movies/Drama",
			qualityProfileId: 4,
			search: false,
		});
	});

	it("falls back when the saved folder or profile has gone", () => {
		const result = resolveChoices(options, {
			skip: true,
			rootFolder: "/old",
			qualityProfileId: 99,
		});
		expect(result?.rootFolder).toBe("/movies");
		expect(result?.qualityProfileId).toBe(1);
	});

	it("takes the series type from the lookup when nothing was saved", () => {
		const anime: Candidate = {
			key: "1",
			title: "x",
			raw: { seriesType: "anime" },
		};
		expect(resolveChoices(options, { skip: false }, anime)?.seriesType).toBe(
			"anime",
		);
	});

	it("gives nothing when there is nowhere to put it", () => {
		expect(
			resolveChoices(
				{ profiles: [], folders: [{ path: "/m" }] },
				{ skip: false },
			),
		).toBeUndefined();
		expect(
			resolveChoices(
				{ profiles: [{ id: 1, name: "a" }], folders: [] },
				{ skip: false },
			),
		).toBeUndefined();
	});
});

describe("request bodies", () => {
	it("adds a movie, searching straight away", () => {
		const [movie] = parseCandidates("radarr", [{ ...jedi, id: 0 }]);
		if (!movie) throw new Error("no candidate");
		const body = buildMovieBody(movie, choices);
		expect(body).toMatchObject({
			title: "Return of the Jedi",
			tmdbId: 1892,
			qualityProfileId: 4,
			rootFolderPath: "/movies/Drama",
			monitored: true,
			minimumAvailability: "released",
			addOptions: { monitor: "movieOnly", searchForMovie: true },
		});
		expect(body).not.toHaveProperty("id");
		expect(
			buildMovieBody(movie, { ...choices, search: false }).addOptions,
		).toMatchObject({ searchForMovie: false });
	});

	it("adds a series", () => {
		const [show] = parseCandidates("sonarr", [
			{ title: "Andor", tvdbId: 393189, seasons: [{ seasonNumber: 1 }] },
		]);
		if (!show) throw new Error("no candidate");
		const body = buildSeriesBody(show, {
			...choices,
			monitor: "firstSeason",
			seriesType: "anime",
			search: false,
		});
		expect(body).toMatchObject({
			tvdbId: 393189,
			rootFolderPath: "/movies/Drama",
			seasonFolder: true,
			seriesType: "anime",
			addOptions: {
				monitor: "firstSeason",
				searchForMissingEpisodes: false,
				searchForCutoffUnmetEpisodes: false,
			},
		});
	});
});

describe("sanitizePrefs", () => {
	it("gives the defaults for nothing or nonsense", () => {
		const empty = { radarr: { skip: false }, sonarr: { skip: false } };
		expect(sanitizePrefs(undefined)).toEqual(empty);
		expect(sanitizePrefs("x")).toEqual(empty);
		expect(
			sanitizePrefs({
				radarr: { skip: "yes", rootFolder: 4, monitor: "sometimes" },
			}),
		).toEqual(empty);
	});

	it("keeps valid choices", () => {
		expect(
			sanitizePrefs({
				sonarr: {
					skip: true,
					rootFolder: "/tv",
					qualityProfileId: 2,
					monitor: "future",
					seriesType: "daily",
					search: false,
				},
			}).sonarr,
		).toEqual({
			skip: true,
			rootFolder: "/tv",
			qualityProfileId: 2,
			monitor: "future",
			seriesType: "daily",
			search: false,
		});
	});
});

describe("arrErrorMessage", () => {
	it("explains refusals", () => {
		expect(arrErrorMessage("radarr", 403, "")).toMatch(/Check the connection/);
		expect(arrErrorMessage("sonarr", 502, "")).toMatch(/couldn't reach Sonarr/);
		expect(arrErrorMessage("radarr", 0, "")).toMatch(/couldn't reach Radarr/);
	});

	it("uses the app's own reason when it gives one", () => {
		expect(
			arrErrorMessage(
				"radarr",
				400,
				'[{"propertyName":"TmdbId","errorMessage":"This movie has already been added"}]',
			),
		).toBe("This movie has already been added");
		expect(arrErrorMessage("radarr", 500, '{"message":"Boom"}')).toBe("Boom");
		expect(arrErrorMessage("radarr", 500, "<html>")).toBe(
			"Radarr didn't accept that (error 500).",
		);
	});
});

describe("connection status", () => {
	it("reads the server's answer", () => {
		const status = parseArrStatus({
			persistent: true,
			apps: {
				radarr: {
					configured: true,
					url: "http://10.0.0.138:7878",
					version: "5.14.0.9383",
					count: 1748,
				},
				sonarr: { configured: false },
			},
		});
		expect(status?.persistent).toBe(true);
		expect(status?.apps.radarr).toMatchObject({
			configured: true,
			url: "http://10.0.0.138:7878",
			count: 1748,
		});
		expect(status?.apps.sonarr).toEqual({ configured: false });
	});

	it("rejects other shapes", () => {
		expect(parseArrStatus(null)).toBeUndefined();
		expect(parseArrStatus({ apps: 3 })).toBeUndefined();
		expect(parseArrStatus({ message: "nope" })).toBeUndefined();
	});

	it("describes a connection", () => {
		expect(
			connectionDetail("radarr", {
				configured: true,
				version: "5.14.0.9383",
				count: 1748,
			}),
		).toBe("v5.14.0.9383 · 1748 movies");
		expect(connectionDetail("radarr", { configured: true, count: 1 })).toBe(
			"1 movie",
		);
		expect(
			connectionDetail("sonarr", {
				configured: true,
				version: "4.0.10.2544",
				count: 136,
			}),
		).toBe("v4.0.10.2544 · 136 series");
		expect(connectionDetail("sonarr", { configured: true })).toBe("");
	});
});
