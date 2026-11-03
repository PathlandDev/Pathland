# pathland-demo-common — implementation status

**Last updated:** November 3, 2026

DSL-neutral demo support shared by the Java and Kotlin demo views.

## Implemented

- **`DemoTheme`** — the demo's global `SET_DESIGN_TOKEN` theme (base + `dark.*`).
- **`DemoAssets`** — the single embedded copy of the 8 audio tracks, 8 album
  covers, and 8 lyric SRT files under
  `META-INF/resources/_pathland/assets/`, plus temp-dir extraction for hosts.
- **`DemoAssetsTest`** — guards all 24 embedded assets and extraction.

## Not implemented / gaps

- None.

## Verified by

`mvn test -pl pathland-demo-common`.
