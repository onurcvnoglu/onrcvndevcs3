# Onurcvnoglu Cloudstream Plugins

This repository currently contains two Cloudstream3 plugins built around Stremio addon support.

## Included Plugins

### `Onurcvncs3`

- Uses TMDB for home page, search, and metadata
- Lets users add multiple Stremio `manifest.json` URLs from plugin settings
- Loads links from compatible Stremio stream addons
- Adds OpenSubtitles and Watchsomuch fallbacks

### `UseStremio`

- Does not use TMDB
- Uses only user-provided Stremio manifests at runtime
- Reads catalog, meta, stream, and subtitle resources directly from compatible Stremio addons
- Works best with already configured Stremio addon URLs

## Setup In Cloudstream

### `Onurcvncs3`

1. Install the `.cs3` plugin file in Cloudstream.
2. Open plugin settings.
3. Enter your TMDB API key.
4. Add one or more Stremio addon `manifest.json` URLs.
5. Save and restart the app.

### `UseStremio`

1. Install the `.cs3` plugin file in Cloudstream.
2. Open plugin settings.
3. Add one or more configured Stremio addon `manifest.json` URLs.
4. Save and restart the app.

Accepted manifest inputs include:

- `https://example-addon.domain/manifest.json`
- `stremio://example-addon.domain/manifest.json`

## Build

Build a single plugin:

- macOS/Linux: `./gradlew Onurcvncs3:make`
- macOS/Linux: `./gradlew UseStremio:make`
- Windows: `.\gradlew.bat Onurcvncs3:make`
- Windows: `.\gradlew.bat UseStremio:make`

Generate all plugin metadata:

```bash
./gradlew make makePluginsJson
```

For local development, `local.properties` is only needed if Android SDK discovery is not already configured via environment variables.

## Important Notes

- `UseStremio` is the closer Stremio-like experience inside Cloudstream.
- `Onurcvncs3` still relies on TMDB and adds subtitle fallbacks outside the Stremio ecosystem.
- Catalogue-only addons can still show metadata but may return `No link found` during playback if they do not expose stream resources.

## Attribution

This repository is based on:

- Cloudstream provider documentation: `https://recloudstream.github.io/csdocs/devs/create-your-own-providers/`
- Phisher's Cloudstream extensions: `https://github.com/phisher98/cloudstream-extensions-phisher`
