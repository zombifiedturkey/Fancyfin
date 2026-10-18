import { fileURLToPath } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv } from "vite";

// In dev (and `vite preview`) the app talks to Jellyfin through /api, the same
// path the production nginx container uses. /api/System/Info -> JELLYFIN_URL/System/Info
// This avoids CORS and keeps the client code identical in dev and prod.
export default defineConfig(({ mode }) => {
	const env = loadEnv(mode, process.cwd(), "");
	const jellyfinUrl = env.JELLYFIN_URL || "http://localhost:8096";

	const proxy = {
		// The TMDB, Radarr and Sonarr connections saved in Settings, and the calls made with them:
		// the small settings service (pnpm dev:server).
		"/tmdb": { target: "http://127.0.0.1:8283" },
		"/arr": { target: "http://127.0.0.1:8283" },
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
