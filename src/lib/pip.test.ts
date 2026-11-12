import { describe, expect, it } from "vitest";
import { popOutSize } from "./pip";

describe("popOutSize", () => {
	it("matches a 16:9 video", () => {
		expect(popOutSize(1920, 1080)).toEqual({ width: 480, height: 270 });
	});

	it("matches a wide cinema video", () => {
		expect(popOutSize(1920, 800)).toEqual({ width: 480, height: 200 });
	});

	it("falls back when the video size isn't known yet", () => {
		expect(popOutSize(0, 0)).toEqual({ width: 480, height: 270 });
	});

	it("keeps very tall or very flat shapes within limits", () => {
		expect(popOutSize(1000, 100).height).toBe(160);
		expect(popOutSize(100, 1000).height).toBe(520);
	});
});
