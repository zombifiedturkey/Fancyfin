# Changelog

## 0.1.0

The first release.

**Browsing and watching**
- Home with Continue Watching, Next Up, Recently Added and a Discover row.
- Movies and TV Shows pages with a featured banner, shelves, genre pages and a full library grid.
- Floating glass detail panel for any title, with cast, "More like this", episodes and details; full detail pages too.
- Search from the top bar.
- Playback with direct play and HLS transcoding, subtitles (SRT, VTT, ASS/SSA and burned-in), and audio track selection.
- Fix a wrong match, refresh metadata, edit details and pick artwork.

**Popular right now (TMDB)**
- The featured banner shows what is popular, matched to your library, and by genre on genre pages.
- Titles you don't own are shown with their release status, genres and expected streaming or disc date, and open in the same panel with details from TMDB.

**Radarr and Sonarr**
- Connect them in Settings; Add buttons appear only when the connection works.
- Add dialog with root folder, quality profile and more, or one-click add. Titles they already have show "Already in Radarr / Sonarr".

**Settings and setup**
- Accent colour, poster corners and size, glass strength, banner options, Home rows and more.
- Docker image on GitHub Container Registry, an Unraid template, and a `/config` folder that keeps the TMDB, Radarr and Sonarr connections.
