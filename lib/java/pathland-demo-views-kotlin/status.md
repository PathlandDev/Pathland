# pathland-demo-views-kotlin — implementation status

**Last updated:** November 3, 2026

Kotlin-owned demo views over `pathland-view-kotlin`, with no Java-DSL dependency.
It owns the app-driven music player and the small sponsored-proxy ad app.

## Structure

- `music/` — the root `MusicPlayerView` (public, Java-mountable).
- `music/model/` — `Track`, `Lyrics`.
- `music/library/` — `LibraryView`, `TrackRowView`.
- `music/player/` — `PlayerMainArea`, `PlayerBar`, `PlayerControls`,
  `TransportControls`, `TrackInfoView`, `VolumeControls`.
- `music/player/controls/` — `SeekControl`, `PercentSeek`, `VolumeControl`.
- `music/service/` — `MediaPlayerService`: per-session player state, playlist,
  `MediaController`, and the environment key shared by the views.
- `music/lyrics/` — `SubtitlePill`.
- `music/sidebar/` — `NowPlayingSidebar`, `UpNextRowView`.
- `ad/` — `AdView`.

## Implemented

- **`MusicPlayerView`** — Apple Music-style root: one root `SizeThatFits`
  selects narrow (no volume), medium (volume), or wide (volume + `NowPlayingSidebar`),
  with the floating `PlayerBar`.
- **Persisted player state** — owned by `MediaPlayerService` (`player.position`, `player.playing`,
  `player.track`, `player.volume`, `player.seekRequest`, `player.volumeRequest`)
  and created with `Signals.state` while the framework's session state is active.
- **App-driven media** — `MediaPlayerService` owns and registers the
  `MediaController`, and the emitter projects it as a pinned root node; every fit
  candidate shares the same controller. `MEDIA_*` reports feed position/volume
  and `MEDIA_ENDED` advances the track.
- **Seek/volume decoupling** — `SeekControl` buffers drags and commits on release;
  `PercentSeek` maps seconds to 0..100; `VolumeControl` separates report from
  command (each in its own file under `player/controls/`).
- **Lyrics** — synchronized two-line karaoke pill driven by the parsed SRT cues.
- **Window metadata** — track-derived title/description and `locale("en")`.
- **Sponsored proxy** — optional `adUrl` appends a `ProxyView` in the sidebar.
- **`AdView`** — the small sponsored-proxy origin app: a title, a button, and a
  persisted click counter (`ad.clicks`), fully Kotlin-owned and Java-mountable.
- **Accessibility** — transport labels, slider labels, `MAIN`/`HEADER` roles.

## Verified by

`mvn test -pl pathland-demo-views-kotlin` — `LyricsTest`, `SeekControlTest`,
`MediaPlayerServiceTest` (persisted keys, select/next/previous), `MusicPlayerViewTest`
(tree/render, fit swaps, media state, seek echo guard, snapshot emission, sponsored
proxy), and `AdViewTest` (button click / persisted counter delta).
