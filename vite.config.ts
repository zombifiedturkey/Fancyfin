import { fileURLToPath } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv, type ProxyOptions } from "vite";

// In dev (and `vite preview`) the app talks to Jellyfin through /api, the same
// path the production nginx container uses. /api/System/Info -> JELLYFIN_URL/System/Info
// This avoids CORS and keeps the client code identical in dev and prod.
export default defineConfig(({ mode }) => {
	const env = loadEnv(mode, process.cwd(), "");
	const jellyfinUrl = env.JELLYFIN_URL || "http://localhost:8096";

	// TMDB, for the featured banner. The key stays in this process; the browser only sees /tmdb.
	// A "read access token" (it contains dots) goes in a header, a plain API key in the address.
	const tmdbKey = env.TMDB_API_KEY ?? "";
	const tmdbIsToken = tmdbKey.includes(".");
	const tmdbProxy: Record<string, ProxyOptions> = tmdbKey
		? {
				"/tmdb": {
					target: "https://api.themoviedb.org",
					changeOrigin: true,
					headers: tmdbIsToken
						? { Authorization: `Bearer ${tmdbKey}` }
						: undefined,
					rewrite: (path: string) => {
						const rewritten = path.replace(/^\/tmdb/, "/3");
						if (tmdbIsToken) return rewritten;
						const joiner = rewritten.includes("?") ? "&" : "?";
						return `${rewritten}${joiner}api_key=${encodeURIComponent(tmdbKey)}`;
					},
				},
			}
		: {};

	// Radarr and Sonarr, for the Add button. Same idea: the key stays in this process. The production
	// image also checks that the caller is a Jellyfin administrator; this dev proxy does not.
	const arrProxy = (
		name: "radarr" | "sonarr",
		url: string | undefined,
		key: string | undefined,
	): Record<string, ProxyOptions> => {
		if (!url || !key) return {};
		const parsed = new URL(url);
		const base = parsed.pathname.replace(/\/+$/, "");
		return {
			[`/${name}`]: {
				target: parsed.origin,
				changeOrigin: true,
				headers: { "X-Api-Key": key },
				rewrite: (path: string) => `${base}${path.slice(name.length + 1)}`,
			},
		};
	};

	const proxy = {
		...tmdbProxy,
		...arrProxy("radarr", env.RADARR_URL, env.RADARR_API_KEY),
		...arrProxy("sonarr", env.SONARR_URL, env.SONARR_API_KEY),
		"/api": {
			target: jellyfinUrl,
			changeOrigin: true,
			ws: true,
			rewrite: (path: string) => path.replace(/^\/api/, ""),
		},
	};

	return {
		plugins: [react(), tailwindcss()],
		define: {
			__APP_VERSION__: JSON.stringify(
				process.env.npm_package_version ?? "0.0.0",
			),
		},
		resolve: {
			alias: {
				"@": fileURLToPath(new URL("./src", import.meta.url)),
			},
		},
		// jassub loads its worker and wasm relative to its own files, so Vite must not pre-bundle it.
		optimizeDeps: { exclude: ["jassub"] },
		server: { proxy },
		preview: { proxy },
	};
});
