import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { describe, expect, it } from "vitest";
import {
	defaultSeason,
	nextEpisodeId,
	orderSeasons,
	seasonCounts,
	watchedCount,
} from "./seasons";

const season = (
	id: string,
	index: number | undefined,
	unplayed = 0,
	count?: number,
): BaseItemDto => ({
	Id: id,
	IndexNumber: index,
	ChildCount: count,
	UserData: { UnplayedItemCount: unplayed } as BaseItemDto["UserData"],
});

describe("orderSeasons", () => {
	it("puts specials last", () => {
		const ordered = orderSeasons([
			season("sp", 0),
			season("s2", 2),
			season("s1", 1),
		]);
		expect(ordered.map((s) => s.Id)).toEqual(["s1", "s2", "sp"]);
	});
});

describe("defaultSeason", () => {
	it("opens on the first season with something left to watch", () => {
		const seasons = [
			season("sp", 0, 3),
			season("s1", 1, 0),
			season("s2", 2, 4),
		];
		expect(defaultSeason(seasons)?.Id).toBe("s2");
	});
	it("falls back to the first real season", () => {
		expect(defaultSeason([season("sp", 0), season("s1", 1)])?.Id).toBe("s1");
	});
	it("copes with only specials or nothing", () => {
		expect(defaultSeason([season("sp", 0)])?.Id).toBe("sp");
		expect(defaultSeason([])).toBeUndefined();
	});
});

describe("seasonCounts", () => {
	it("works out how many are watched", () => {
		expect(seasonCounts(season("s", 1, 4, 10))).toEqual({
			watched: 6,
			total: 10,
		});
	});
	it("is undefined when the server didn't say", () => {
		expect(seasonCounts(season("s", 1, 4))).toBeUndefined();
		expect(seasonCounts({ Id: "x", ChildCount: 3 })).toBeUndefined();
	});
});

describe("episodes", () => {
	const ep = (id: string, played: boolean): BaseItemDto => ({
		Id: id,
		UserData: { Played: played } as BaseItemDto["UserData"],
	});
	it("counts watched and finds the next one", () => {
		const list = [ep("a", true), ep("b", true), ep("c", false), ep("d", false)];
		expect(watchedCount(list)).toBe(2);
		expect(nextEpisodeId(list)).toBe("c");
	});
	it("has no next episode when all are watched", () => {
		expect(nextEpisodeId([ep("a", true)])).toBeUndefined();
	});
});
