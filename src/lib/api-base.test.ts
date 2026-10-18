import { describe, expect, it } from "vitest";
import { API_BASE, apiUrl } from "./api-base";

describe("apiUrl", () => {
	it("prefixes paths with the API base", () => {
		expect(apiUrl("/System/Info/Public")).toBe(
			`${API_BASE}/System/Info/Public`,
		);
	});

	it("adds a missing leading slash", () => {
		expect(apiUrl("Users/Me")).toBe(`${API_BASE}/Users/Me`);
	});
});
