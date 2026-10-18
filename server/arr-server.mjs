// Fancyfin's small settings service. It runs inside the container, next to nginx, and nginx forwards
// /arr/ to it. It exists because Radarr and Sonarr need an API key, and keys must never reach the
// browser. It:
//   - keeps the Radarr and Sonarr address and key in a file in the config folder, and
//   - passes a short list of calls on to Radarr and Sonarr, adding the key.
// No dependencies: Node 22's built-in http and fetch are enough.
import { randomBytes } from "node:crypto";
import { statSync } from "node:fs";
import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import http from "node:http";
import path from "node:path";
import { pathToFileURL } from "node:url";

export const APPS = {
	radarr: { name: "Radarr", kind: "movie", list: "movie" },
	sonarr: { name: "Sonarr", kind: "series", list: "series" },
};

const MAX_BODY = 1024 * 1024;
const COUNT_CACHE_MS = 60_000;

/** A clean http(s) address with no trailing slash, or undefined. */
export function normalizeUrl(input) {
	if (typeof input !== "string") return undefined;
	const trimmed = input.trim();
	if (!trimmed) return undefined;
	const withScheme = /^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed)
		? trimmed
		: `http://${trimmed}`;
	let url;
	try {
		url = new URL(withScheme);
	} catch {
		return undefined;
	}
	if (url.protocol !== "http:" && url.protocol !== "https:") return undefined;
	if (!url.hostname || url.username || url.password) return undefined;
	return `${url.origin}${url.pathname.replace(/\/+$/, "")}`;
}

/** Keys are letters, digits and . _ - only; anything else is not a real Radarr/Sonarr key. */
export function validKey(key) {
	return typeof key === "string" && /^[A-Za-z0-9._-]{8,200}$/.test(key);
}

/**
 * Is this call one the Add dialog needs? `sub` is what follows /arr/<app>/, for example
 * "api/v3/qualityprofile". Everything else is refused.
 */
export function allowedCall(app, method, sub) {
	const kind = APPS[app]?.kind;
	if (!kind) return false;
	if (method === "GET") {
		return [
			"api/v3/qualityprofile",
			"api/v3/rootfolder",
			`api/v3/${kind}/lookup`,
		].includes(sub);
	}
	if (method === "POST") return sub === `api/v3/${kind}`;
	return false;
}

/** Where config.json lives, and whether that folder survives the container being replaced. */
export function configLocation(dir) {
	let persistent = false;
	try {
		persistent = statSync(dir).dev !== statSync("/").dev;
	} catch {
		// Folder doesn't exist yet.
	}
	return { file: path.join(dir, "arr.json"), persistent };
}

async function readConfig(file) {
	try {
		const data = JSON.parse(await readFile(file, "utf8"));
		const out = {};
		for (const app of Object.keys(APPS)) {
			const entry = data?.[app];
			if (
				entry &&
				typeof entry.url === "string" &&
				typeof entry.key === "string"
			) {
				out[app] = { url: entry.url, key: entry.key };
			}
		}
		return out;
	} catch {
		return {};
	}
}

async function writeConfig(file, config) {
	await mkdir(path.dirname(file), { recursive: true });
	const temp = `${file}.${randomBytes(4).toString("hex")}.tmp`;
	await writeFile(temp, `${JSON.stringify(config, null, 2)}\n`, {
		mode: 0o600,
	});
	await rename(temp, file);
}

class HttpFailure extends Error {
	constructor(status, message) {
		super(message);
		this.status = status;
	}
}

/** Create the request handler. Separate from listen() so tests can start it on any port. */
export function createServer({ configDir }) {
	const { file, persistent } = configLocation(configDir);
	const counts = new Map();

	/** Call Radarr or Sonarr with a key. Returns the fetch Response. */
	async function callApp(app, config, method, apiPath, body) {
		const label = APPS[app].name;
		let response;
		try {
			response = await fetch(`${config.url}${apiPath}`, {
				method,
				headers: {
					"X-Api-Key": config.key,
					Accept: "application/json",
					...(body === undefined ? {} : { "Content-Type": "application/json" }),
				},
				body,
				signal: AbortSignal.timeout(20_000),
			});
		} catch {
			throw new HttpFailure(
				502,
				`Fancyfin couldn't reach ${label} at ${config.url}.`,
			);
		}
		return response;
	}

	/** Version and library size, or what is wrong. Never includes the key. */
	async function describe(app, config) {
		if (!config) return { configured: false };
		const label = APPS[app].name;
		const base = { configured: true, url: config.url };
		try {
			const response = await callApp(
				app,
				config,
				"GET",
				"/api/v3/system/status",
			);
			if (response.status === 401 || response.status === 403) {
				return { ...base, error: `${label} refused the saved API key.` };
			}
			if (!response.ok) {
				return {
					...base,
					error: `${label} answered with an error (${response.status}).`,
				};
			}
			const status = await response.json();
			let count = counts.get(config.url);
			if (!count || count.until < Date.now()) {
				const list = await callApp(
					app,
					config,
					"GET",
					`/api/v3/${APPS[app].list}`,
				);
				const items = list.ok ? await list.json() : undefined;
				count = {
					value: Array.isArray(items) ? items.length : undefined,
					until: Date.now() + COUNT_CACHE_MS,
				};
				counts.set(config.url, count);
			}
			return {
				...base,
				version:
					typeof status.version === "string" ? status.version : undefined,
				count: count.value,
			};
		} catch (error) {
			return {
				...base,
				error:
					error instanceof HttpFailure
						? error.message
						: `Couldn't read from ${label}.`,
			};
		}
	}

	async function readBody(request) {
		const chunks = [];
		let size = 0;
		for await (const chunk of request) {
			size += chunk.length;
			if (size > MAX_BODY)
				throw new HttpFailure(413, "That request is too large.");
			chunks.push(chunk);
		}
		return Buffer.concat(chunks);
	}

	function send(response, status, body, headers = {}) {
		const text = typeof body === "string" ? body : JSON.stringify(body);
		response.writeHead(status, {
			"Content-Type": "application/json",
			"Cache-Control": "no-store",
			...headers,
		});
		response.end(text);
	}

	async function handle(request, response) {
		const url = new URL(request.url ?? "/", "http://local");
		const parts = url.pathname
			.replace(/^\/arr\/?/, "")
			.split("/")
			.filter(Boolean);
		const method = request.method ?? "GET";

		// GET /arr/status
		if (parts.length === 1 && parts[0] === "status" && method === "GET") {
			const config = await readConfig(file);
			const [radarr, sonarr] = await Promise.all([
				describe("radarr", config.radarr),
				describe("sonarr", config.sonarr),
			]);
			return send(response, 200, { persistent, apps: { radarr, sonarr } });
		}

		const app = parts[0];
		if (!app || !(app in APPS)) throw new HttpFailure(404, "Not found.");
		const label = APPS[app].name;

		// PUT /arr/<app>  { url, key? }  and  DELETE /arr/<app>
		if (parts.length === 1) {
			if (method === "DELETE") {
				const config = await readConfig(file);
				delete config[app];
				await writeConfig(file, config);
				return send(response, 200, { configured: false });
			}
			if (method === "PUT") {
				let input;
				try {
					input = JSON.parse((await readBody(request)).toString("utf8"));
				} catch {
					throw new HttpFailure(400, "That wasn't valid JSON.");
				}
				const config = await readConfig(file);
				const address = normalizeUrl(input?.url);
				if (!address) {
					throw new HttpFailure(
						400,
						`Enter ${label}'s address, like http://192.168.1.20:${app === "radarr" ? 7878 : 8989}.`,
					);
				}
				const key =
					typeof input?.key === "string" && input.key.trim()
						? input.key.trim()
						: config[app]?.key;
				if (!key)
					throw new HttpFailure(
						400,
						`Enter ${label}'s API key (Settings, General in ${label}).`,
					);
				if (!validKey(key))
					throw new HttpFailure(400, "That doesn't look like an API key.");

				// Check it works before saving anything.
				const candidate = { url: address, key };
				const check = await callApp(
					app,
					candidate,
					"GET",
					"/api/v3/system/status",
				);
				if (check.status === 401 || check.status === 403) {
					throw new HttpFailure(400, `${label} refused that API key.`);
				}
				if (!check.ok) {
					throw new HttpFailure(
						400,
						`${label} answered with an error (${check.status}). Is the address right?`,
					);
				}
				const status = await check.json().catch(() => undefined);
				if (status?.appName && status.appName !== label) {
					throw new HttpFailure(
						400,
						`That address is ${status.appName}, not ${label}.`,
					);
				}
				counts.delete(address);
				config[app] = candidate;
				await writeConfig(file, config);
				return send(response, 200, await describe(app, candidate));
			}
			throw new HttpFailure(405, "Not allowed.");
		}

		// /arr/<app>/api/v3/...  passed on to the app, for the few calls the Add dialog makes
		const sub = parts.slice(1).join("/");
		if (!allowedCall(app, method, sub))
			throw new HttpFailure(404, "Not found.");

		const config = (await readConfig(file))[app];
		if (!config) {
			throw new HttpFailure(
				409,
				`${label} isn't connected yet. Add it in Settings.`,
			);
		}
		const body =
			method === "POST"
				? (await readBody(request)).toString("utf8")
				: undefined;
		const upstream = await callApp(
			app,
			config,
			method,
			`/${sub}${url.search}`,
			body,
		);
		if (upstream.status === 401 || upstream.status === 403) {
			throw new HttpFailure(
				424,
				`${label} refused the saved API key. Reconnect it in Settings.`,
			);
		}
		const text = await upstream.text();
		return send(response, upstream.status, text, {
			"Content-Type":
				upstream.headers.get("content-type") ?? "application/json",
		});
	}

	return http.createServer((request, response) => {
		handle(request, response).catch((error) => {
			if (error instanceof HttpFailure) {
				send(response, error.status, { message: error.message });
			} else {
				console.error("fancyfin settings service:", error);
				send(response, 500, {
					message: "Something went wrong on the Fancyfin server.",
				});
			}
		});
	});
}

// Start listening when run directly (node server/arr-server.mjs), not when imported by a test.
if (
	process.argv[1] &&
	import.meta.url === pathToFileURL(process.argv[1]).href
) {
	const flag = process.argv.find((arg) => arg.startsWith("--config-dir="));
	const configDir = flag
		? flag.slice("--config-dir=".length)
		: process.env.CONFIG_DIR || "/config";
	const port = Number(process.env.ARR_PORT) || 8283;
	createServer({ configDir }).listen(port, "127.0.0.1", () => {
		const { persistent } = configLocation(configDir);
		console.log(
			`fancyfin: settings service on 127.0.0.1:${port}, saving to ${configDir}${persistent ? "" : " (not a mounted folder: settings will be lost when the container is replaced)"}`,
		);
	});
}
