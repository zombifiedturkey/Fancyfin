# Project Plan: Fancyfin

A modern, cinematic web frontend for Jellyfin. It talks only to the public Jellyfin API, so the stock server stays untouched and updates never break us.

## Goals

1. **Look and feel first.** Cinematic, fast, smooth. Big artwork, glassy blur surfaces, good typography, focus/hover motion, responsive from phone to desktop.
2. **Core viewing loop done well.** Login, home (continue watching, next up, recently added), library grids, search, detail pages, playback.
3. **Solid playback.** Direct play where possible, HLS transcode fallback, subtitle and audio track switching, resume position, progress reporting.
4. **Stock server, untouched.** Public API only, via `@jellyfin/sdk`.
5. **Admin stays in the stock UI**, with a link out. The one exception is fixing individual titles (see M3b), because that belongs on the page where you notice the problem.

## Non-goals (v1)

- Admin dashboard, user management, plugin UI (per-title metadata fixes are in scope, see M3b)
- Live TV/DVR, music, books, photos
- Chromecast
- Native Android TV app (planned later, see Future)

## Audience and release

- Personal use (me and a friend) first, public GitHub repo from the start, public release when ready.
- License: permissive (MIT or Apache-2.0). Decide before first commit. An API-only client is not bound by the server's GPL.
- Name: **Fancyfin**. Avoid "Jellyfin" itself in the product name and logo to stay clear of trademark issues. Describe it as "a client for Jellyfin".

## Stack

| Concern | Choice | Why |
|---|---|---|
| Build | Vite + TypeScript (strict) | Fast, standard |
| UI | React | Largest ecosystem, best fit for animation-heavy UI |
| Styling | Tailwind CSS + CSS variables for design tokens | Fast iteration, themeable |
| Motion | Motion (Framer Motion) | Focus/parallax/page transitions |
| Routing | TanStack Router | Typed routes, data loading |
| Data | TanStack Query + `@jellyfin/sdk` | Caching, retries, typed API |
| Playback | hls.js + native `<video>` | Direct play and HLS |
| Subtitles | JASSUB (libass, WASM) for ASS/SSA; native text tracks / custom renderer for SRT/VTT | Styled subtitle rendering |
| Testing | Vitest + Playwright | Unit plus end-to-end |
| Lint/format | Biome or ESLint + Prettier | Consistency |

## Design direction

- Dark, high-contrast, content-first. Backdrops and posters carry the page.
- Large hero carousel on home with logo art, synopsis, and primary Play button.
- Shelves (horizontal rows) of cards; focus/hover scales with subtle parallax and glow.
- Frosted-glass nav and overlays (backdrop blur), minimal chrome.
- Detail pages: full-bleed backdrop, blurred gradient into content, cast and episode lists.
- Typography: Inter (or similar), generous sizing and spacing.
- Original design and assets only, with no third-party brand artwork or fonts we don't have the rights to use.
- Architecture note: keep navigation focus-aware (keyboard/D-pad friendly) from day one so a TV version is not a rewrite.

## Playback notes

- Use the server's PlaybackInfo to decide direct play vs transcode.
- Text subs (SRT/VTT/ASS) render client-side. Image-based subs (PGS/VobSub) cannot be rendered in a browser, so request server-side burn-in (forces a transcode).
- Report start, progress, and stop to the server so resume and watched state stay correct.

## Hosting

- One Docker image: nginx serving the static build, with `/api` reverse-proxied to the Jellyfin server (avoids CORS, one URL for the user).
- Config via environment variable (`JELLYFIN_URL`).
- Published to GitHub Container Registry; Unraid community-apps template added at release time.

## Milestones

**M0: Scaffold**
Repo, tooling, CI, design tokens, base layout shell, Dockerfile and compose file. Done when the empty app builds and runs in a container on Unraid.

**M1: Connect and login**
Server connection (via proxy), login, session persistence, user avatar and logout. Quick Connect optional.

**M2: Home and libraries**
Hero, continue watching, next up, recently added, library grids with sort/filter, image loading with blurhash placeholders.

**M3: Detail pages**
Movie page, series page with seasons and episodes, cast, similar items, watched/favorite toggles.

**M3b: Fix and edit titles**
For users who are allowed to edit metadata (admins), an Edit menu on the detail page:
- *Identify / fix match:* search the metadata providers, pick the right result, apply it (replaces the title's metadata and images).
- *Images:* browse provider artwork (poster, backdrop, logo), pick one to download, or upload your own; remove an image.
- *Edit metadata:* title, year, overview, genres, rating, and similar fields.
- *Refresh metadata:* re-scan the title, optionally replacing existing metadata and images.
Hidden entirely for users without permission. Needs real-server testing, since these calls change data.

*Status:* Identify / fix match and Refresh metadata are built. **Deferred until the major milestones are done (remind the owner):** the Images picker and the Edit metadata form, plus optional TMDB integration for better artwork and metadata (the key lives in a server-side env var behind the nginx/Vite proxy, never in the repo or browser code).

**M4: Playback core**
Player UI, direct play, HLS transcode fallback, seek, volume, fullscreen, resume, progress reporting, next-episode flow.

**M5: Subtitles and audio**
Track picker, ASS/SSA via JASSUB, SRT/VTT, burn-in fallback for image subs, subtitle styling options.

**M6: Search and polish**
Search, keyboard shortcuts, mobile layout pass, transitions, loading and error states, accessibility pass, performance pass.

**M7: Public release prep**
README with screenshots, license, contributing guide, versioned image on GHCR, Unraid template, issue templates.

## Future (post-v1)

- Android TV app (reuse API layer and design language; native or wrapped web, to be decided)
- Music, photos, Live TV
- Admin page shortcuts
- Chromecast, if ever wanted

## Open decisions

- [x] Project name: Fancyfin
- [ ] Check name availability (GitHub, npm, Docker Hub/GHCR, domain)
- [ ] License (MIT vs Apache-2.0)
- [ ] Which Jellyfin server version to develop against (check the Unraid container's version)
- [ ] Test library: a small set of movies/shows covering ASS subs, PGS subs, and HEVC/10-bit to exercise transcoding
