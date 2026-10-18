export type RuntimeConfig = {
	/** True when the server this page came from proxies /api to a Jellyfin server. */
	proxy: boolean;
	/** True when the server proxies /tmdb to TMDB with its own API key. */
	tmdb: boolean;
	/** True when the server proxies /radarr to Radarr (administrators only). */
	radarr: boolean;
	/** True when the server proxies /sonarr to Sonarr (administrators only). */
	sonarr: boolean;
};

/** Whether an address answers with JSON. Used when there is no config.json (the dev server). */
async function probe(path: string, signal?: AbortSignal): Promise<boolean> {
	try {
		const response = await fetch(path, { signal });
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
 * which is when the app should ask for the server address itself, and tmdb, radarr and sonarr say
 * whether those were set up. Anywhere else (dev server, other hosting) there is no such file: we
 * assume the /api proxy exists and ask the others directly.
 */
export async function loadRuntimeConfig(
	signal?: AbortSignal,
): Promise<RuntimeConfig> {
	let proxy = true;
	const known: { tmdb?: boolean; radarr?: boolean; sonarr?: boolean } = {};
	try {
		const response = await fetch("/config.json", { signal, cache: "no-store" });
		if (response.ok) {
			const data: unknown = await response.json();
			if (typeof data === "object" && data !== null) {
				const config = data as Record<string, unknown>;
				if (config.proxy === false) proxy = false;
				for (const name of ["tmdb", "radarr", "sonarr"] as const) {
					const value = config[name];
					if (typeof value === "boolean") known[name] = value;
				}
			}
		}
	} catch {
		// Missing or not JSON: treat as the default.
	}
	const [tmdb, radarr, sonarr] = await Promise.all([
		known.tmdb ?? probe("/tmdb/configuration", signal),
		known.radarr ?? probe("/radarr/api/v3/rootfolder", signal),
		known.sonarr ?? probe("/sonarr/api/v3/rootfolder", signal),
	]);
	return { proxy, tmdb, radarr, sonarr };
}
