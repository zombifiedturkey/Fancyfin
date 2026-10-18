/**
 * Where Jellyfin lives, from the browser's point of view.
 * - Default: the same-origin /api path. The Vite dev server and the Docker image's nginx proxy it to the
 *   Jellyfin server (JELLYFIN_URL), so there is no CORS to worry about.
 * - Custom: if the user typed a server address on the connect screen, requests go straight to it.
 */
export const API_BASE = "/api";

const SERVER_KEY = "fancyfin.server";

/**
 * Turn what someone typed into a clean base address: adds http:// when no scheme is given, drops any
 * trailing slash, and rejects anything that isn't a plain http(s) address.
 */
export function normalizeServerUrl(input: string): string | null {
	const trimmed = input.trim();
	if (!trimmed) return null;
	const withScheme = /^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed)
		? trimmed
		: `http://${trimmed}`;
	let url: URL;
	try {
		url = new URL(withScheme);
	} catch {
		return null;
	}
	if (url.protocol !== "http:" && url.protocol !== "https:") return null;
	if (!url.hostname) return null;
	return `${url.origin}${url.pathname.replace(/\/+$/, "")}`;
}

/** The server address the user chose on the connect screen, if any. */
export function getServerUrl(): string | null {
	try {
		return localStorage.getItem(SERVER_KEY);
	} catch {
		return null;
	}
}

export function saveServerUrl(url: string): void {
	try {
		localStorage.setItem(SERVER_KEY, url);
	} catch {
		// Without storage the choice can't be remembered.
	}
}

export function clearServerUrl(): void {
	try {
		localStorage.removeItem(SERVER_KEY);
	} catch {
		// Nothing to clear.
	}
}

/** The base every Jellyfin request starts from: the chosen server, or the /api proxy. */
export function getApiBase(): string {
	return getServerUrl() ?? API_BASE;
}

/** Build a URL for a Jellyfin API path, e.g. apiUrl("/System/Info/Public"). */
export function apiUrl(path: string): string {
	const clean = path.startsWith("/") ? path : `/${path}`;
	return `${getApiBase()}${clean}`;
}
