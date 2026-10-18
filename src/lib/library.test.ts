import { beforeEach, describe, expect, it, vi } from "vitest";
import {
	buildItemsQuery,
	DEFAULT_PREFS,
	loadPrefs,
	sanitizePrefs,
	savePrefs,
	sortDefaults,
} from "./library";

describe("sanitizePrefs", () => {
	it("falls back to defaults for junk", () => {
		expect(sanitizePrefs(null)).toEqual(DEFAULT_PREFS);
		expect(sanitizePrefs("nope")).toEqual(DEFAULT_PREFS);
		expect(sanitizePrefs({ sort: "bogus", filter: 7, genre: 3 })).toEqual(
			DEFAULT_PREFS,
		);
	});

	it("keeps valid values", () => {
		const prefs = {
			sort: "rating",
			descending: false,
			filter: "unwatched",
			genre: "Horror",
		};
		expect(sanitizePrefs(prefs)).toEqual(prefs);
	});
});

describe("sortDefaults", () => {
	it("is A-Z for names and newest/best first otherwise", () => {
		expect(sortDefaults("name")).toBe(false);
		expect(sortDefaults("added")).toBe(true);
		expect(sortDefaults("rating")).toBe(true);
	});
});

describe("buildItemsQuery", () => {
	it("asks for movies or series", () => {
		expect(buildItemsQuery("movies", DEFAULT_PREFS).includeItemTypes).toEqual([
			"Movie",
		]);
		expect(buildItemsQuery("shows", DEFAULT_PREFS).includeItemTypes).toEqual([
			"Series",
		]);
	});

	it("adds a name tiebreak when sorting by something else", () => {
		const query = buildItemsQuery("movies", {
			...DEFAULT_PREFS,
			sort: "rating",
			descending: true,
		});
		expect(query.sortBy).toEqual(["CommunityRating", "SortName"]);
		expect(query.sortOrder).toEqual(["Descending", "Ascending"]);
	});

	it("sorts by name alone", () => {
		const query = buildItemsQuery("movies", {
			...DEFAULT_PREFS,
			sort: "name",
			descending: false,
		});
		expect(query.sortBy).toEqual(["SortName"]);
		expect(query.sortOrder).toEqual(["Ascending"]);
	});

	it("maps the watch filters", () => {
		const q = (filter: "all" | "unwatched" | "watched" | "favorites") =>
			buildItemsQuery("movies", { ...DEFAULT_PREFS, filter });
		expect(q("all").isPlayed).toBeUndefined();
		expect(q("unwatched").isPlayed).toBe(false);
		expect(q("watched").isPlayed).toBe(true);
		expect(q("favorites").isFavorite).toBe(true);
	});

	it("passes the genre only when one is chosen", () => {
		expect(buildItemsQuery("movies", DEFAULT_PREFS).genres).toBeUndefined();
		expect(
			buildItemsQuery("movies", { ...DEFAULT_PREFS, genre: "Comedy" }).genres,
		).toEqual(["Comedy"]);
	});
});

describe("saved preferences", () => {
	beforeEach(() => {
		const store = new Map<string, string>();
		vi.stubGlobal("localStorage", {
			getItem: (key: string) => store.get(key) ?? null,
			setItem: (key: string, value: string) => void store.set(key, value),
		});
	});

	it("starts with defaults and round-trips per library", () => {
		expect(loadPrefs("movies")).toEqual(DEFAULT_PREFS);
		const prefs = {
			...DEFAULT_PREFS,
			sort: "name" as const,
			descending: false,
		};
		savePrefs("movies", prefs);
		expect(loadPrefs("movies")).toEqual(prefs);
		expect(loadPrefs("shows")).toEqual(DEFAULT_PREFS);
	});
});
