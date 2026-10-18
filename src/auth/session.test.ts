import { beforeEach, describe, expect, it, vi } from "vitest";
import { clearSession, loadSession, saveSession } from "./session";

function stubLocalStorage() {
	const store = new Map<string, string>();
	vi.stubGlobal("localStorage", {
		getItem: (key: string) => store.get(key) ?? null,
		setItem: (key: string, value: string) => void store.set(key, value),
		removeItem: (key: string) => void store.delete(key),
	});
	return store;
}

describe("session storage", () => {
	let store: Map<string, string>;

	beforeEach(() => {
		store = stubLocalStorage();
	});

	it("returns null when nothing is saved", () => {
		expect(loadSession()).toBeNull();
	});

	it("round-trips a session", () => {
		const session = {
			userId: "u1",
			userName: "Wayne",
			accessToken: "tok",
			primaryImageTag: "abc",
		};
		saveSession(session);
		expect(loadSession()).toEqual(session);
	});

	it("ignores corrupt or incomplete data", () => {
		store.set("fancyfin.session", "{not json");
		expect(loadSession()).toBeNull();

		store.set(
			"fancyfin.session",
			JSON.stringify({ userId: "u1", userName: "Wayne" }),
		);
		expect(loadSession()).toBeNull();
	});

	it("clears the saved session", () => {
		saveSession({ userId: "u1", userName: "Wayne", accessToken: "tok" });
		clearSession();
		expect(loadSession()).toBeNull();
	});
});
