import { describe, expect, it } from "vitest";
import { upNextVisible } from "./up-next";

describe("upNextVisible", () => {
	it("stays hidden for most of the episode", () => {
		expect(upNextVisible(600, 2700, false)).toBe(false);
	});

	it("appears in the last 30 seconds", () => {
		expect(upNextVisible(2670, 2700, false)).toBe(true);
		expect(upNextVisible(2669, 2700, false)).toBe(false);
	});

	it("shows when the video has ended", () => {
		expect(upNextVisible(2700, 2700, true)).toBe(true);
	});

	it("ignores very short videos until they end", () => {
		expect(upNextVisible(50, 60, false)).toBe(false);
		expect(upNextVisible(60, 60, true)).toBe(true);
	});

	it("ignores an unknown length", () => {
		expect(upNextVisible(0, 0, false)).toBe(false);
		expect(upNextVisible(5, Number.NaN, false)).toBe(false);
	});
});
