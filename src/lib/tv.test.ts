import { describe, expect, it } from "vitest";
import { isTvUserAgent } from "./tv";

describe("isTvUserAgent", () => {
	it("recognises the Android TV app", () => {
		expect(
			isTvUserAgent(
				"Mozilla/5.0 (Linux; Android 11) Chrome/120 Mobile FancyfinTV/0.1.0",
			),
		).toBe(true);
	});

	it("ignores ordinary browsers", () => {
		expect(isTvUserAgent("Mozilla/5.0 (Windows NT 10.0) Chrome/120")).toBe(
			false,
		);
	});
});
