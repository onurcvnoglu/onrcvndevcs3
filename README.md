# Onurcvncs3 Cloudstream Plugin

`Onurcvncs3` is a Cloudstream3 plugin repo that ports Phisher's Stremio addon provider into this repository.

## What It Does

- Uses TMDB for home page, search, and metadata
- Lets you add multiple Stremio `manifest.json` URLs from plugin settings
- Loads links from compatible Stremio stream addons
- Fetches subtitles from OpenSubtitles and Watchsomuch when available

## Setup

Each user must add their own TMDB API key after installing the plugin:

1. Install the `.cs3` plugin file in Cloudstream.
2. Open plugin settings.
3. Enter your TMDB API key.
4. Add one or more Stremio addon `manifest.json` URLs.
5. Save and restart the app.

For local development, `local.properties` is only needed if Android SDK discovery is not already configured via environment variables.

## Build

- macOS/Linux: `./gradlew Onurcvncs3:make`
- Windows: `.\gradlew.bat Onurcvncs3:make`

To generate plugin metadata as well:

```bash
./gradlew Onurcvncs3:make makePluginsJson
```

## Usage In Cloudstream

1. Install the generated `.cs3` plugin file.
2. Open the plugin settings inside Cloudstream.
3. Enter your TMDB API key.
4. Add one or more Stremio addon `manifest.json` URLs.
5. Save and restart the app when prompted.

Example accepted inputs:

- `https://example-addon.domain/manifest.json`
- `stremio://example-addon.domain/manifest.json`

## Important Notes

- This is not a full Stremio client replacement.
- Playback depends on the addon providing stream results.
- TMDB metadata is fetched in Turkish (`tr-TR`).
- Catalogue-only addons can still show metadata but may return `No link found` during playback.

## Attribution

This port is based on:

- Cloudstream provider documentation: `https://recloudstream.github.io/csdocs/devs/create-your-own-providers/`
- Phisher's Cloudstream extensions: `https://github.com/phisher98/cloudstream-extensions-phisher`
