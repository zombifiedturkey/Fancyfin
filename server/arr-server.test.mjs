import { mkdtemp, readFile, stat } from "node:fs/promises";
import http from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import {
	allowedCall,
	createServer,
	normalizeUrl,
	validKey,
} from "./arr-server.mjs";

function listen(server) {
	return new Promise((resolve) =>
		server.listen(0, "127.0.0.1", () => resolve(server.address().port)),
	);
}

/** A stand-in for Radarr or Sonarr, at an optional URL base. */
function fakeArr({ name, key, base = "" }) {
	const seen = { posts: [], keys: [] };
	const server = http.createServer(async (req, res) => {
		seen.keys.push(req.headers["x-api-key"]);
		if (req.headers["x-api-key"] !== key)
			return void res.writeHead(401).end("{}");
		const url = req.url.startsWith(base)
			? req.url.slice(base.length)
			: "/missing";
		const json = (status, body) =>
			res
				.writeHead(status, { "Content-Type": "application/json" })
				.end(JSON.stringify(body));
		if (url === "/api/v3/system/status")
			return json(200, { appName: name, version: "9.9.9.1" });
		if (url === "/api/v3/movie" && req.method === "GET")
			return json(200, [{}, {}, {}]);
		if (url === "/api/v3/series" && req.method === "GET")
			return json(200, [{}, {}]);
		if (url === "/api/v3/qualityprofile")
			return json(200, [{ id: 4, name: "HD-1080p" }]);
		if (url.startsWith("/api/v3/movie/lookup"))
			return json(200, [{ title: "Dune", term: url }]);
		if (req.method === "POST") {
			let body = "";
			for await (const chunk of req) body += chunk;
			seen.posts.push({ url, body: JSON.parse(body) });
			return json(201, { id: 77 });
		}
		return json(404, {});
	});
	return { server, seen };
}

describe("helpers", () => {
	it("cleans addresses", () => {
		expect(normalizeUrl("192.168.1.20:7878/")).toBe("http://192.168.1.20:7878");
		expect(normalizeUrl(" https://tv.example.com/sonarr/ ")).toBe(
			"https://tv.example.com/sonarr",
		);
		expect(normalizeUrl("ftp://x")).toBe(undefined);
		expect(normalizeUrl("http://user:pw@x")).toBe(undefined);
		expect(normalizeUrl("")).toBe(undefined);
	});

	it("checks keys", () => {
		expect(validKey("0123456789abcdef0123456789abcdef")).toBe(true);
		expect(validKey("short")).toBe(false);
		expect(validKey("has space in it 12345")).toBe(false);
	});

	it("allows only the Add dialog's calls", () => {
		expect(allowedCall("radarr", "GET", "api/v3/qualityprofile")).toBe(true);
		expect(allowedCall("radarr", "GET", "api/v3/movie/lookup")).toBe(true);
		expect(allowedCall("radarr", "POST", "api/v3/movie")).toBe(true);
		expect(allowedCall("sonarr", "POST", "api/v3/series")).toBe(true);
		expect(allowedCall("sonarr", "GET", "api/v3/series/lookup")).toBe(true);
		expect(allowedCall("radarr", "GET", "api/v3/movie")).toBe(false);
		expect(allowedCall("radarr", "DELETE", "api/v3/movie")).toBe(false);
		expect(allowedCall("radarr", "GET", "api/v3/system/status")).toBe(false);
		expect(allowedCall("radarr", "POST", "api/v3/series")).toBe(false);
		expect(allowedCall("nope", "GET", "api/v3/rootfolder")).toBe(false);
	});
});

describe("service", () => {
	const KEY = "radarrkey1234567";
	const SKEY = "sonarrkey1234567";
	let radarr;
	let sonarr;
	let service;
	let base;
	let dir;
	let radarrUrl;
	let sonarrUrl;

	const call = async (method, p, { body } = {}) => {
		const response = await fetch(`${base}${p}`, {
			method,
			headers: body ? { "Content-Type": "application/json" } : {},
			body: body ? JSON.stringify(body) : undefined,
		});
		const text = await response.text();
		let json;
		try {
			json = JSON.parse(text);
		} catch {
			json = undefined;
		}
		return { status: response.status, json, text };
	};

	beforeAll(async () => {
		radarr = fakeArr({ name: "Radarr", key: KEY });
		sonarr = fakeArr({ name: "Sonarr", key: SKEY, base: "/tv" });
		const [rp, sp] = await Promise.all([
			listen(radarr.server),
			listen(sonarr.server),
		]);
		radarrUrl = `http://127.0.0.1:${rp}`;
		sonarrUrl = `http://127.0.0.1:${sp}/tv`;
		dir = await mkdtemp(path.join(tmpdir(), "fancyfin-arr-"));
		service = createServer({ configDir: dir });
		base = `http://127.0.0.1:${await listen(service)}`;
	});

	afterAll(() => {
		for (const s of [radarr.server, sonarr.server, service]) s.close();
	});

	it("starts with nothing connected", async () => {
		const { status, json } = await call("GET", "/arr/status");
		expect(status).toBe(200);
		expect(json.apps).toEqual({
			radarr: { configured: false },
			sonarr: { configured: false },
		});
		const refused = await call("GET", "/arr/radarr/api/v3/qualityprofile");
		expect(refused.status).toBe(409);
		expect(refused.json.message).toMatch(/isn't connected/);
	});

	it("refuses a bad key, the wrong app and a missing key, saving nothing", async () => {
		const wrongKey = await call("PUT", "/arr/radarr", {
			body: { url: radarrUrl, key: "wrongwrongwrong1" },
		});
		expect(wrongKey.status).toBe(400);
		expect(wrongKey.json.message).toMatch(/refused that API key/);
		const wrongApp = await call("PUT", "/arr/sonarr", {
			body: { url: radarrUrl, key: KEY },
		});
		expect(wrongApp.status).toBe(400);
		const noKey = await call("PUT", "/arr/radarr", {
			body: { url: radarrUrl },
		});
		expect(noKey.json.message).toMatch(/API key/);
		const noUrl = await call("PUT", "/arr/radarr", {
			body: { url: "", key: KEY },
		});
		expect(noUrl.status).toBe(400);
		expect((await call("GET", "/arr/status")).json.apps.radarr.configured).toBe(
			false,
		);
	});

	it("connects, reports version and size, and never returns the key", async () => {
		const put = await call("PUT", "/arr/radarr", {
			body: { url: `${radarrUrl}/`, key: KEY },
		});
		expect(put.status).toBe(200);
		expect(put.json).toEqual({
			configured: true,
			url: radarrUrl,
			version: "9.9.9.1",
			count: 3,
		});
		expect(put.text.includes(KEY)).toBe(false);

		const status = await call("GET", "/arr/status");
		expect(status.json.apps.radarr).toMatchObject({
			configured: true,
			version: "9.9.9.1",
			count: 3,
		});
		expect(status.text.includes(KEY)).toBe(false);

		const file = path.join(dir, "arr.json");
		expect(JSON.parse(await readFile(file, "utf8")).radarr).toEqual({
			url: radarrUrl,
			key: KEY,
		});
		expect((await stat(file)).mode & 0o777).toBe(0o600);
	});

	it("works with an app that has a URL base", async () => {
		const put = await call("PUT", "/arr/sonarr", {
			body: { url: sonarrUrl, key: SKEY },
		});
		expect(put.status).toBe(200);
		expect(put.json).toMatchObject({
			configured: true,
			version: "9.9.9.1",
			count: 2,
		});
	});

	it("keeps the saved key when only the address is edited", async () => {
		const put = await call("PUT", "/arr/radarr", { body: { url: radarrUrl } });
		expect(put.status).toBe(200);
		expect(put.json.configured).toBe(true);
	});

	it("passes the allowed calls on with the key added", async () => {
		const profiles = await call("GET", "/arr/radarr/api/v3/qualityprofile");
		expect(profiles.json).toEqual([{ id: 4, name: "HD-1080p" }]);
		const lookup = await call(
			"GET",
			"/arr/radarr/api/v3/movie/lookup?term=tmdb%3A438631",
		);
		expect(lookup.json[0].term).toBe("/api/v3/movie/lookup?term=tmdb%3A438631");

		const add = await call("POST", "/arr/radarr/api/v3/movie", {
			body: { title: "Dune", tmdbId: 438631 },
		});
		expect(add.status).toBe(201);
		expect(radarr.seen.posts[0].body).toEqual({
			title: "Dune",
			tmdbId: 438631,
		});
		expect(radarr.seen.keys.at(-1)).toBe(KEY);

		// Through the URL base too.
		const tv = await call("POST", "/arr/sonarr/api/v3/series", {
			body: { title: "Andor" },
		});
		expect(tv.status).toBe(201);
		expect(sonarr.seen.posts[0].url).toBe("/api/v3/series");
	});

	it("refuses anything outside that list", async () => {
		expect((await call("GET", "/arr/radarr/api/v3/movie")).status).toBe(404);
		expect((await call("DELETE", "/arr/radarr/api/v3/movie")).status).toBe(404);
		expect((await call("GET", "/arr/radarr/api/v3/system/status")).status).toBe(
			404,
		);
		expect((await call("GET", "/arr/other/api/v3/qualityprofile")).status).toBe(
			404,
		);
		expect((await call("POST", "/arr/radarr/api/v3/series")).status).toBe(404);
	});

	it("says so when the app has since refused the key or gone away", async () => {
		const { writeFile } = await import("node:fs/promises");
		await writeFile(
			path.join(dir, "arr.json"),
			JSON.stringify({ radarr: { url: radarrUrl, key: "changedchanged1" } }),
		);
		const refused = await call("GET", "/arr/radarr/api/v3/qualityprofile");
		expect(refused.status).toBe(424);
		expect(refused.json.message).toMatch(/Reconnect/);
		const status = await call("GET", "/arr/status");
		expect(status.json.apps.radarr.error).toMatch(/refused the saved API key/);

		await writeFile(
			path.join(dir, "arr.json"),
			JSON.stringify({ radarr: { url: "http://127.0.0.1:1", key: KEY } }),
		);
		const gone = await call("GET", "/arr/radarr/api/v3/qualityprofile");
		expect(gone.status).toBe(502);
		expect(gone.json.message).toMatch(/couldn't reach Radarr/);
	});

	it("disconnects", async () => {
		const del = await call("DELETE", "/arr/radarr");
		expect(del.json).toEqual({ configured: false });
		expect((await call("GET", "/arr/status")).json.apps.radarr.configured).toBe(
			false,
		);
	});
});
