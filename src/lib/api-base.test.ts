import { describe, expect, it } from "vitest";
import { API_BASE, apiUrl, normalizeServerUrl } from "./api-base";

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

describe("normalizeServerUrl", () => {
	it("adds http:// when there is no scheme", () => {
		expect(normalizeServerUrl("192.168.1.20:8096")).toBe(
			"http://192.168.1.20:8096",
		);
	});

	it("keeps https and drops trailing slashes", () => {
		expect(normalizeServerUrl(" https://media.example.com/ ")).toBe(
			"https://media.example.com",
		);
	});

	it("keeps a path prefix", () => {
		expect(normalizeServerUrl("http://host:8096/jellyfin/")).toBe(
			"http://host:8096/jellyfin",
		);
	});

	it("rejects empty and non-http input", () => {
		expect(normalizeServerUrl("")).toBeNull();
		expect(normalizeServerUrl("   ")).toBeNull();
		expect(normalizeServerUrl("ftp://host")).toBeNull();
	});
});
