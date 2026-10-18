import type { BaseItemDto } from "@jellyfin/sdk/lib/generated-client/models/base-item-dto";
import { describe, expect, it } from "vitest";
import {
	clampPercent,
	episodeLabel,
	formatRuntime,
	landscapeSubtitle,
	minutesLeft,
	ticksToMinutes,
} from "./format";

const MINUTE = 600_000_000;

describe("ticksToMinutes and formatRuntime", () => {
	it("converts ticks to minutes", () => {
		expect(ticksToMinutes(90 * MINUTE)).toBe(90);
		expect(ticksToMinutes(0)).toBeUndefined();
		expect(ticksToMinutes(undefined)).toBeUndefined();
	});

	it("formats runtimes", () => {
		expect(formatRuntime(112)).toBe("1h 52m");
		expect(formatRuntime(45)).toBe("45m");
		expect(formatRuntime(120)).toBe("2h");
		expect(formatRuntime(undefined)).toBeUndefined();
	});
});

describe("minutesLeft", () => {
	it("is the runtime minus the saved position", () => {
		const item: BaseItemDto = {
			RunTimeTicks: 100 * MINUTE,
			UserData: { Key: "k", PlaybackPositionTicks: 40 * MINUTE },
		};
		expect(minutesLeft(item)).toBe(60);
	});

	it("is undefined when nothing has been watched", () => {
		expect(minutesLeft({ RunTimeTicks: 100 * MINUTE })).toBeUndefined();
	});
});

describe("episodeLabel and landscapeSubtitle", () => {
	it("labels episodes", () => {
		expect(episodeLabel({ ParentIndexNumber: 2, IndexNumber: 5 })).toBe(
			"S2:E5",
		);
		expect(episodeLabel({ IndexNumber: 5 })).toBe("E5");
		expect(episodeLabel({})).toBeUndefined();
	});

	it("describes an episode card", () => {
		const episode: BaseItemDto = {
			Type: "Episode",
			ParentIndexNumber: 1,
			IndexNumber: 3,
			Name: "The Pilot",
		};
		expect(landscapeSubtitle(episode)).toBe("S1:E3 · The Pilot");
	});

	it("shows time left for a partly watched movie, else the year", () => {
		const partly: BaseItemDto = {
			Type: "Movie",
			RunTimeTicks: 130 * MINUTE,
			UserData: { Key: "k", PlaybackPositionTicks: 10 * MINUTE },
		};
		expect(landscapeSubtitle(partly)).toBe("2h left");
		expect(landscapeSubtitle({ Type: "Movie", ProductionYear: 1999 })).toBe(
			"1999",
		);
	});
});

describe("clampPercent", () => {
	it("keeps values in 0-100", () => {
		expect(clampPercent(42.5)).toBe(42.5);
		expect(clampPercent(150)).toBe(100);
		expect(clampPercent(-5)).toBe(0);
		expect(clampPercent(undefined)).toBe(0);
		expect(clampPercent(Number.NaN)).toBe(0);
	});
});
