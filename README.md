# Fancyfin

A modern, cinematic web client for Jellyfin. Beautiful browsing, smooth playback, and styled subtitles, built on the public API.

Fancyfin talks only to the public Jellyfin API, so your server stays stock and updates never break it. See [PLAN.md](PLAN.md) for goals, stack and milestones.

> **Status:** early development. Browsing, detail pages, playback (direct play and HLS transcode), subtitles (SRT, VTT, ASS/SSA, burned-in image subs), audio track selection and search all work. Expect rough edges.

## Local development

Requirements: Node 22+ and [pnpm](https://pnpm.io/installation) 10+.

```bash
pnpm install

# Point the dev server at your Jellyfin
cp .env.example .env.local        # Windows PowerShell: Copy-Item .env.example .env.local
# edit .env.local and set JELLYFIN_URL, e.g. http://192.168.1.50:8096

pnpm dev                          # add --host to open it from your phone on the same network
```

The app calls Jellyfin through `/api`, which the Vite dev server (and the Docker image's nginx) proxy to `JELLYFIN_URL`. That means no CORS setup is needed on the Jellyfin server.

### Scripts

| Command | What it does |
|---|---|
| `pnpm dev` | Dev server with hot reload |
| `pnpm build` | Typecheck and production build to `dist/` |
| `pnpm typecheck` | TypeScript only |
| `pnpm lint` | Biome lint and format check |
| `pnpm format` | Biome auto-fix |
| `pnpm test` | Unit tests (Vitest) |

## Docker

```bash
docker compose up --build         # then open http://localhost:8080
```

Set `JELLYFIN_URL` in `docker-compose.yml` to the address of your Jellyfin server as seen from the container.

### Published images

GitHub Actions builds and publishes to `ghcr.io/zombifiedturkey/fancyfin`:

| Tag | Follows |
|---|---|
| `edge` | every push to `main` (latest development build) |
| `latest`, `1.2.3` | tagged releases (`v1.2.3`) |
| `sha-xxxxxxx` | a specific commit |

### Unraid

1. **Docker** tab, **Add Container**.
2. **Repository:** `ghcr.io/zombifiedturkey/fancyfin:edge`
3. **Port:** container port `80`, host port of your choice (for example `8080`).
4. **Variable:** `JELLYFIN_URL` set to your Jellyfin address, for example `http://192.168.1.50:8096`.
5. Apply, then open `http://<unraid-ip>:8080`.

To pick up a new edge build, use **Check for updates** on the Docker tab, then **apply update** on the container. If the package is private, make it public in the repository's package settings on GitHub, or run `docker login ghcr.io` on the server with a token that has `read:packages`.

## License

[MIT](LICENSE)
