export type RuntimeConfig = {
	/** True when the server this page came from proxies /api to a Jellyfin server. */
	proxy: boolean;
	/** True when a TMDB API key is saved on the server (Settings), so /tmdb works. */
	tmdb: boolean;
};

/** Is a TMDB key saved on the server? No settings service (or no answer) counts as no. */
async function loadTmdbConnected(signal?: AbortSignal): Promise<boolean> {
	try {
		const response = await fetch("/arr/tmdb", { signal, cache: "no-store" });
		if (!response.ok) return false;
		const data: unknown = await response.json();
		return (data as { configured?: unknown } | null)?.configured === true;
	} catch {
		return false;
	}
}

/** Is the Jellyfin proxy set up? The Docker image writes /config.json at start-up. */
async function loadProxy(signal?: AbortSignal): Promise<boolean> {
	try {
		const response = await fetch("/config.json", { signal, cache: "no-store" });
		if (response.ok) {
			const data: unknown = await response.json();
			if ((data as { proxy?: unknown } | null)?.proxy === false) return false;
		}
	} catch {
		// Missing or not JSON: treat as the default.
	}
	return true;
}

/**
 * What the server is set up with. The Docker image writes /config.json at start-up: proxy is false
 * when no JELLYFIN_URL was given, which is when the app should ask for the server address itself.
 * Anywhere else (dev server, other hosting) there is no such file and we assume the /api proxy
 * exists. Whether TMDB is connected comes from the settings service, since the key is saved from
 * Settings and can change while the server runs.
 */
export async function loadRuntimeConfig(
	signal?: AbortSignal,
): Promise<RuntimeConfig> {
	const [proxy, tmdb] = await Promise.all([
		loadProxy(signal),
		loadTmdbConnected(signal),
	]);
	return { proxy, tmdb };
}
