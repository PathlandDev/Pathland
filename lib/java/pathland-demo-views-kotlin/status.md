# pathland-demo-views-kotlin — implementation status

**Last updated:** November 3, 2026

Kotlin-owned demo views over `pathland-view-kotlin`, with no Java-DSL dependency.
It owns the app-driven music player and the small sponsored-proxy ad app.

## Implemented

- **`MusicPlayerView`** — Apple Music-style root: one root `SizeThatFits`
  selects narrow (no volume), medium (volume), or wide (volume + `NowPlayingSidebar`),
  with the floating `PlayerBar`.
- **Persisted player state** — `player.position`, `player.playing`,
  `player.track`, `player.volume`, `player.seekRequest`, `player.volumeRequest`
  via Kotlin `Component` state delegates.
- **App-driven media** — a `MediaController` owns the source/transport; every
  fit candidate shares the same controller. `MEDIA_*` reports feed position/volume
  and `MEDIA_ENDED` advances the track.
- **Seek/volume decoupling** — `SeekControl` buffers drags and commits on release;
  `PercentSeek` maps seconds to 0..100; `VolumeControl` separates report from
  command.
- **Lyrics** — synchronized two-line karaoke pill driven by the parsed SRT cues.
- **Window metadata** — track-derived title/description and `locale("en")`.
- **Sponsored proxy** — optional `adUrl` appends a `ProxyView` in the sidebar.
- **`AdView`** — the small sponsored-proxy origin app: a title, a button, and a
  persisted click counter (`ad.clicks`), fully Kotlin-owned and Java-mountable.
- **Accessibility** — transport labels, slider labels, `MAIN`/`HEADER` roles.

## Verified by

`mvn test -pl pathland-demo-views-kotlin` — `LyricsTest`, `SeekControlTest`,
`MusicPlayerViewTest` (tree/render, fit swaps, media state, seek echo guard,
snapshot emission, sponsored proxy), and `AdViewTest` (button click / persisted
counter delta).
