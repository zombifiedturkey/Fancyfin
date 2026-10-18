export type RuntimeConfig = {
	/** True when the server this page came from proxies /api to a Jellyfin server. */
	proxy: boolean;
	/** True when the server proxies /tmdb to TMDB with its own API key. */
	tmdb: boolean;
};

/** Whether /tmdb answers with JSON. Used when there is no config.json (the dev server). */
async function probeTmdb(signal?: AbortSignal): Promise<boolean> {
	try {
		const response = await fetch("/tmdb/configuration", { signal });
		return (
			response.ok &&
			(response.headers.get("content-type") ?? "").includes("json")
		);
	} catch {
		return false;
	}
}

/**
 * The Docker image writes /config.json at start-up: proxy is false when no JELLYFIN_URL was given,
 * which is when the app should ask for the server address itself, and tmdb says whether a TMDB key
 * was given. Anywhere else (dev server, other hosting) there is no such file: we assume the /api
 * proxy exists and ask /tmdb directly.
 */
export async function loadRuntimeConfig(
	signal?: AbortSignal,
): Promise<RuntimeConfig> {
	let proxy = true;
	let tmdb: boolean | undefined;
	try {
		const response = await fetch("/config.json", { signal, cache: "no-store" });
		if (response.ok) {
			const data: unknown = await response.json();
			if (typeof data === "object" && data !== null) {
				const config = data as { proxy?: unknown; tmdb?: unknown };
				if (config.proxy === false) proxy = false;
				if (typeof config.tmdb === "boolean") tmdb = config.tmdb;
			}
		}
	} catch {
		// Missing or not JSON: treat as the default.
	}
	return { proxy, tmdb: tmdb ?? (await probeTmdb(signal)) };
}
