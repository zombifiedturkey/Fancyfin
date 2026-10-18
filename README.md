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

Optional: add `TMDB_API_KEY=...` to `.env.local` to turn on the "popular right now" banner (see [Popular banner](#popular-banner-tmdb)), and `RADARR_URL`/`RADARR_API_KEY` or `SONARR_URL`/`SONARR_API_KEY` for the Add buttons (see [Add to Radarr and Sonarr](#add-to-radarr-and-sonarr); the dev proxy doesn't check for administrators).

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

### Popular banner (TMDB)

The featured banner on Home and the Movies and TV Shows pages can show what's popular right now, using [TMDB](https://www.themoviedb.org/). Popular titles you own are matched to your library and play as normal. Popular titles you don't own are shown for inspiration, with a "Not in your library" label and a link to TMDB; there is a switch on the banner (and in Settings) to hide them.

1. Create a free TMDB account and copy your API key (or "API Read Access Token") from your account's API settings.
2. Set it as `TMDB_API_KEY` on the container (or in `.env.local` for `pnpm dev`).

The key stays on the server: the browser only talks to `/tmdb` on your Fancyfin address, and nginx adds the key. Never commit the key to the repository. Without a key the banner shows recently added titles.

### Add to Radarr and Sonarr

Titles in the banner (and the **Discover** row on Home) that you don't have can be sent straight to [Radarr](https://radarr.video/) (movies) or [Sonarr](https://sonarr.tv/) (TV) so they get collected. Only **Jellyfin administrators** see the buttons.

1. Set `RADARR_URL` and `RADARR_API_KEY` for movies, and/or `SONARR_URL` and `SONARR_API_KEY` for TV. The address is the one Fancyfin's container can reach (e.g. `http://192.168.1.20:7878`; include the URL base if you set one). The key is in each app under **Settings, General**.
2. `JELLYFIN_URL` must be set too: Fancyfin asks Jellyfin whether the caller is an administrator before it lets any request through to Radarr or Sonarr.
3. TMDB (above) is needed for the banner and Discover row, which is where the Add buttons live.

Click **Add to Radarr / Sonarr** and a window lets you pick the match (if it guessed wrong), root folder (with free space), quality profile, minimum availability (movies) or monitoring and series type (shows), and whether to start searching straight away. Tick **Next time, skip this window** to add in one click with the same settings; turn that back off in **Settings**.

The keys stay on the server: the browser only talks to `/radarr` and `/sonarr` on your Fancyfin address, nginx adds the key, and only the few calls the window needs are allowed. Works with Radarr v5 and Sonarr v4. Never commit the keys to the repository.

### Settings

The avatar menu has **Settings**: accent colour, poster corners and size, glass strength, titles under posters, banner size and source, which Home rows show and in what order, episode layout and animations. They are saved in the browser you change them in.

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
3. Adjust the port if 8080 is taken. **Jellyfin URL** is optional: set it, or leave it empty to enter the address in Fancyfin on first use. **TMDB API key** is optional too (see above), as are the **Radarr** and **Sonarr** address and key pairs. **Apply**.
   If you installed before the TMDB option existed, edit the container, choose **Add another Path, Port, Variable, Label or Device**, set Config Type **Variable**, Name `TMDB API key`, Key `TMDB_API_KEY`, and put your key in Value. Do the same for `RADARR_URL`, `RADARR_API_KEY`, `SONARR_URL` and `SONARR_API_KEY` if you want them (set the key variables to Mask).
4. Open `http://<unraid-ip>:8080`.

**Without the template**

**Docker** tab, **Add Container**, leave the template empty and fill in:
Repository `ghcr.io/zombifiedturkey/fancyfin:edge`, a port mapping to container port `80`, and optionally the variables `JELLYFIN_URL`, `TMDB_API_KEY`, `RADARR_URL`, `RADARR_API_KEY`, `SONARR_URL` and `SONARR_API_KEY`.

To pick up a new edge build, use **Check for updates** on the Docker tab, then **apply update** on the container. If the package is private, make it public in the repository's package settings on GitHub, or run `docker login ghcr.io` on the server with a token that has `read:packages`.

## License

[MIT](LICENSE)
