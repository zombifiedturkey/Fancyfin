import { describe, expect, it } from "vitest";
import { getHttpStatus } from "./errors";

describe("getHttpStatus", () => {
	it("reads the status from an Axios-style error", () => {
		expect(getHttpStatus({ response: { status: 401 } })).toBe(401);
	});

	it("returns undefined when there is no response (network error)", () => {
		expect(getHttpStatus(new Error("Network Error"))).toBeUndefined();
	});

	it("returns undefined for non-objects", () => {
		expect(getHttpStatus("nope")).toBeUndefined();
		expect(getHttpStatus(null)).toBeUndefined();
	});
});
