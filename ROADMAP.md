# Roadmap

The working to-do list. `PLAN.md` holds the original goals and milestones; this file tracks what's being built next.

## Done

- M0 to M5: scaffold, login, home and libraries, detail pages, playback, subtitles and audio
- Search (top-bar box with a results panel)
- Fix a match, refresh metadata, edit details, artwork picker
- First-run "Connect to Jellyfin" screen, GitHub CI, `:edge` image on GHCR, Unraid template

## Next features

### 1. Featured content bar from TMDB popularity (built, needs testing)

- [x] Server-side TMDB proxy (`TMDB_API_KEY` environment variable; nginx in Docker, Vite in dev). The key never reaches the browser.
- [x] `config.json` reports whether TMDB is set up, so the app falls back to recently added when it isn't.
- [x] TMDB trending (movies and TV), matched to the library by TMDB id, ordered by popularity.
- [x] Popular titles not in the library are shown for inspiration, with a "Not in your library" label and a link to TMDB.
- [x] Obvious switch on the banner to hide titles you don't own (also in Settings).
- [x] Source setting: Popular / Popular + recent / Recently added.
- [ ] Try it with a real key on Unraid.
- [ ] Possible: genre pages using TMDB's genre charts (they use recently added for now).

### 2. Add to Radarr and Sonarr (built, needs testing)

Collect the titles the banner shows that the library lacks.

- [x] Server-side proxies `/radarr` and `/sonarr` (`RADARR_URL`/`RADARR_API_KEY`, `SONARR_URL`/`SONARR_API_KEY`); keys never reach the browser, only the calls the dialog needs are allowed.
- [x] Jellyfin administrators only, enforced by nginx (`auth_request` against an admin-only Jellyfin endpoint), so it needs `JELLYFIN_URL`.
- [x] Add dialog: pick another match, root folder with free space, quality profile, minimum availability (Radarr) or monitor and series type (Sonarr), start searching straight away.
- [x] "Next time, skip this window": add in one click with the last settings; switch back in Settings.
- [x] Add button on "Not in your library" banner slides and a **Discover** row on Home.
- [ ] Try it on Unraid with real Radarr and Sonarr (check a non-admin gets refused).
- [ ] Possible: show "In Radarr, waiting for download" on titles already added.
- [ ] Possible: Add from search results and title pages; tags and language profiles.

### 3. Customisation (built, needs testing)

Settings dialog from the avatar menu, saved per browser, applied before first paint.

- [x] Accent colour: seven presets plus a custom picker
- [x] Poster corners: rounded / soft / square
- [x] Poster size: small / medium / large
- [x] Glass strength: clear / standard / frosted
- [x] Show or hide titles under posters
- [x] Top banner size: full or compact
- [x] Home rows: show, hide and reorder
- [x] Animations: follow the device or keep to a minimum
- [x] Reset to defaults
- [ ] Possible: sync settings to the Jellyfin account instead of just the browser
- [ ] Possible: light theme, font size, density

### 4. Better seasons (built, needs testing)

- [x] Season pills with watched counts, specials last
- [x] Episode list with thumbnail, number, runtime, air date, description and a play button
- [x] "Up next" marker on the first unwatched episode
- [x] Season summary: "6 of 10 watched" with a progress bar, and mark season watched or unwatched
- [x] List or row-of-cards layout (toggle on the page and in Settings)
- [ ] Possible: season posters and season descriptions
- [ ] Possible: skip intro / auto-play next from the list

## Ideas bank (from what other media apps' users ask for)

- [ ] Skip intro and skip credits (Jellyfin media segments)
- [ ] Choose a version of a movie (Director's Cut, Theatrical)
- [x] Customisable home rows (done, in Settings)
- [ ] Per-user Up Next and quick user switching
- [ ] Watch together (Syncplay)
- [ ] Admin "who's watching now" strip

## Polish and release

- [ ] Blurhash placeholders
- [ ] Loading and error states
- [ ] Accessibility pass
- [ ] Keyboard shortcuts
- [ ] README screenshots, contributing guide, issue templates
- [ ] Tag `v0.1.0`, then switch the Unraid template from `:edge` to `:latest`
- [ ] GHCR package set to Public
- [ ] Delete the leftover local `nginx\default.conf.template`
