export type RuntimeConfig = {
	/** True when the server this page came from proxies /api to a Jellyfin server. */
	proxy: boolean;
};

/**
 * The Docker image writes /config.json at start-up: proxy is false when no JELLYFIN_URL was given,
 * which is when the app should ask for the server address itself. Anywhere else (dev server, other
 * hosting) there is no such file, and we assume the /api proxy exists.
 */
export async function loadRuntimeConfig(
	signal?: AbortSignal,
): Promise<RuntimeConfig> {
	try {
		const response = await fetch("/config.json", { signal, cache: "no-store" });
		if (response.ok) {
			const data: unknown = await response.json();
			if (
				typeof data === "object" &&
				data !== null &&
				(data as { proxy?: unknown }).proxy === false
			) {
				return { proxy: false };
			}
		}
	} catch {
		// Missing or not JSON: treat as the default.
	}
	return { proxy: true };
}
