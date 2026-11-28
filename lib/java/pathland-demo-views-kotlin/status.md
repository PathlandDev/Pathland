# pathland-demo-views-kotlin — implementation status

**Last updated:** November 27, 2026

Kotlin-owned demo views over `pathland-view-core`, with no Java-DSL dependency.
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
- `music/sidebar/` — `NavigationSidebar` (left), `NowPlayingSidebar` (right),
  `UpNextRowView`.
- `ad/` — `AdView`.

## Shared demo support

- **`DemoTheme`** — the demo's global `SET_DESIGN_TOKEN` theme (base + `dark.*`).
- **`DemoAssets`** — the single embedded copy of the 8 audio tracks, 8 album
  covers, and 8 lyric SRT files under
  `META-INF/resources/_pathland/assets/`, plus temp-dir extraction for hosts.
- **`DemoAssetsTest`** — guards all 32 embedded assets and extraction.
- **Responsive-size cover assets** — full 440×440 JPEGs remain for the 220×220
  sidebar; separate 176×176 JPEG thumbnails are used by the smaller track-row
  and player-bar images. Both sets are embedded and extracted for web and GTK.

## Implemented

- **`MusicPlayerView`** — Apple Music-style root: one root `ViewThatFits`
  selects narrow (no nav, no volume, compact play-only transport), medium
  (left `NavigationSidebar` + volume), or wide (left nav + volume +
  `NowPlayingSidebar`), with the floating `PlayerBar`. The medium threshold is
  960 pt and wide is 1200 pt so the full player bar fits beside the 220 pt nav
  (and 280 pt now-playing) sidebar; Now Playing is wide-only.
- **Theme mode button** — the player bar's color-mode button cycles
  system → light → dark → system. `MediaPlayerService.colorScheme` is the
  host-provided `Platform.COLOR_SCHEME` signal (or a local fallback); the host
  emits `META::SET_COLOR_SCHEME`, and the icon switches between `settings`,
  `sun`, and `moon` with a matching accessible label.
- **`NavigationSidebar`** — a static left navigation list (Library, Playlists,
  Artists, Albums, Songs) authored with the DSL `List` + `SidebarListStyle`.
  Rows are `Label`s; the list style supplies the Tailwind-aligned sidebar
  label/icon layout and default/selected color tokens. Selection is local
  visual feedback only (tap/arrow highlight); it does not navigate or filter
  yet. The selected index/focus live on the shared service so they survive root
  fit-candidate swaps.
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
- **Windowed library** — `LibraryView` is a `ScrollView` over a `LazyVStack` +
  paged `ForEach` with a fixed 64 pt row extent; `MediaPlayerService` repeats the
  eight base tracks into a 1000-entry playlist and serves 50-row pages with a
  small simulated latency. Loaded rows render `TrackRowView` (a tappable `HStack`
  highlighted by absolute index); unloaded rows render a skeleton placeholder.
  The live/SSR session uses the `PagedDataSource`; emitter-only unit-test mounts
  keep the in-memory list for determinism. The engine's reactive prefetch keeps
  rows rendered ahead of fast scrolling; the demo has no overscan setting.
  Visible-range reports are coalesced latest-wins and stale pages are cached
  without rendering, so a fast scrollbar drag back and forth reuses the visited
  windows instead of showing blank rows and refetching (ADR-0017).
  **Keyboard library navigation (ADR-0018)** — the library uses the shared
  `LazySelection` component: one roving `FOCUS` tab stop on the service's
  ephemeral `focusedIndex`; ↑/↓/Home/End move it, Enter/tap play, Space toggles
  playback. Selection never requests native focus while the list is unfocused,
  so scrolling/clamping cannot steal focus; tapping a row selects and focuses it
  synchronously before playback. Loaded and skeleton rows share the component's
  wrapper; placeholder rows are keyboard-focusable too. The selected row uses a
  blue background while the list is focused and a neutral gray background while
  it is not; text stays black when unfocused. Every row keeps a stable leading
  speaker icon child and the current track reveals it by opacity, so switching
  tracks cannot accumulate stale icons. List rows use the selected background
  as their keyboard indicator and suppress the generic focus outline. Native player-bar controls
  are explicitly tabbable, so Tab leaves the list and reaches the transport
  controls/seek slider under Safari's default keyboard-nav setting as well as
  Chrome. The volume slider carries an explicit 5% `STEP_VALUE`, so each arrow
  press moves it audibly; the seek slider stays continuous (1% per arrow).

## Verified by

`mvn test -pl pathland-demo-views-kotlin` — `LyricsTest`, `SeekControlTest`,
`MediaPlayerServiceTest` (persisted keys, select/next/previous, the 1000-entry
repeated library), `MusicPlayerViewTest` (tree/render, fit swaps, medium nav
layout, wide-only Now Playing, media state, seek echo guard, snapshot emission,
sponsored proxy, and the bounded lazy library window), `MusicPlayerFitMediaTest`
(the pinned media controller survives SSR
snapshot→live and repeated fit crossings; media routing and play stay bound),
and `AdViewTest` (button click / persisted counter delta).
