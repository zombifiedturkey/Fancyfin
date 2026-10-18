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

`JELLYFIN_URL` is optional:

- **Set it** (the address of your Jellyfin server as seen from the container, e.g. `http://192.168.1.20:8096`) and the container proxies to Jellyfin for you. No CORS setup is needed.
- **Leave it empty** and Fancyfin asks for the Jellyfin address the first time you open it, then remembers it in your browser. In this mode your browser talks to Jellyfin directly, so the address must be reachable from the device you browse on, and (if Fancyfin is served over HTTPS) Jellyfin must be HTTPS too.

### Published images

GitHub Actions builds and publishes to `ghcr.io/zombifiedturkey/fancyfin`:

| Tag | Follows |
|---|---|
| `edge` | every push to `main` (latest development build) |
| `latest`, `1.2.3` | tagged releases (`v1.2.3`) |
| `sha-xxxxxxx` | a specific commit |

### Unraid

**Using the template (recommended)**

1. Open the Unraid web terminal (or SSH in) and run:
   ```bash
   wget -O /boot/config/plugins/dockerMan/templates-user/my-fancyfin.xml \
     https://raw.githubusercontent.com/zombifiedturkey/Fancyfin/main/unraid/fancyfin.xml
   ```
   (Or copy `unraid/fancyfin.xml` from this repo to `\\<unraid>\flash\config\plugins\dockerMan\templates-user\my-fancyfin.xml`.)
2. **Docker** tab, **Add Container**, then pick **Fancyfin** from the template dropdown.
3. Adjust the port if 8080 is taken. **Jellyfin URL** is optional: set it, or leave it empty to enter the address in Fancyfin on first use. **Apply**.
4. Open `http://<unraid-ip>:8080`.

**Without the template**

**Docker** tab, **Add Container**, leave the template empty and fill in:
Repository `ghcr.io/zombifiedturkey/fancyfin:edge`, a port mapping to container port `80`, and optionally a variable `JELLYFIN_URL`.

To pick up a new edge build, use **Check for updates** on the Docker tab, then **apply update** on the container. If the package is private, make it public in the repository's package settings on GitHub, or run `docker login ghcr.io` on the server with a token that has `read:packages`.

## License

[MIT](LICENSE)
