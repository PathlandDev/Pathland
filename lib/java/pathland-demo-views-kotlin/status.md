# pathland-demo-views-kotlin — implementation status

**Last updated:** November 3, 2026

Kotlin-owned demo views over `pathland-view-kotlin`, with no Java-DSL dependency.
The first (and currently only) view is the app-driven music player.

## Implemented

- **`MusicPlayerView`** — Apple Music-style root: `LibraryView` on the left,
  `NowPlayingSidebar` on the right (inside a `SizeThatFits` wide/compact slot),
  and the floating `PlayerBar`.
- **Persisted player state** — `player.position`, `player.playing`,
  `player.track`, `player.volume`, `player.seekRequest`, `player.volumeRequest`
  via Kotlin `KotlinView` state delegates.
- **App-driven media** — a `MediaController` owns the source/transport; the bar
  and sidebar drive the same controller across fit swaps. `MEDIA_*` reports feed
  position/volume and `MEDIA_ENDED` advances the track.
- **Seek/volume decoupling** — `SeekControl` buffers drags and commits on release;
  `PercentSeek` maps seconds to 0..100; `VolumeControl` separates report from
  command.
- **Lyrics** — synchronized two-line karaoke pill driven by the parsed SRT cues.
- **Window metadata** — track-derived title/description and `locale("en")`.
- **Sponsored proxy** — optional `adUrl` appends a `ProxyView` in the sidebar.
- **Accessibility** — transport labels, slider labels, `MAIN`/`HEADER` roles.

## Not implemented / gaps

- Remaining demo views (KitchenSink, SplitNav, AdView) are still Java; tracked by
  #169.

## Verified by

`mvn test -pl pathland-demo-views-kotlin` — `LyricsTest`, `SeekControlTest`,
`MusicPlayerViewTest` (tree/render, fit swaps, media state, seek echo guard,
snapshot emission, sponsored proxy).
