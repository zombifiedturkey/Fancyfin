/**
 * All Jellyfin traffic goes through this path prefix.
 * - Dev: the Vite dev server proxies it to JELLYFIN_URL (see vite.config.ts).
 * - Prod: the nginx container proxies it to JELLYFIN_URL (see nginx/default.conf.template).
 */
export const API_BASE = "/api";

/** Build a same-origin URL for a Jellyfin API path, e.g. apiUrl("/System/Info/Public"). */
export function apiUrl(path: string): string {
	const clean = path.startsWith("/") ? path : `/${path}`;
	return `${API_BASE}${clean}`;
}
