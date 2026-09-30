# pathland-gtk-demo (Java) — implementation status

**Last updated:** September 29, 2026

Runs the shared framework-agnostic demo views (`com.pathland.demo`, e.g.
`MusicPlayerView`) under the **native GTK4 renderer** (`libpathland_gtk`) — the
cross-language, shared-renderer story for Java. Protocol contract: `spec/`.

## How it works

- The Java DSL mounts the view through its fine-grained `Emitter` into a
  `libpathland_core` shared ring (`RingOpcodeSink`, **zero-copy**).
- The in-process GTK renderer pumps the same ring in place
  (`pathland_gtk_run_ring`, via `PathlandCore.ringMut`); no
  `pathland-view-native` host is involved.
- Native inputs round-trip as `EVENT` opcodes that this host drains
  (`pathland_core_drain_events`) and dispatches into the app's bindings via the
  shared `InputDispatcher` (taps, text/value/date inputs, **media events**,
  router back).
- State/theme/platform seeding mirrors the server session: `PersistentState`
  over an `InMemoryStateStore`, `DemoTheme.adaptive()` as the emitter theme.
- **App-driven media**: the renderer plays the demo's tracks through a direct
  GStreamer `playbin` (driven by the node's media control properties) and
  reports `MEDIA_*` events back through the ring. The app's web-style
  `/_pathland/assets/...` paths are resolved against a local asset root: the
  demo extracts its audio + covers from the classpath at startup and calls
  `pathland_gtk_set_asset_root`.

## Implemented

- `GtkRendererNative` — JNA binding to `libpathland_gtk`
  (`pathland_gtk_run_ring(ring, on_event, width, height)` +
  `pathland_gtk_set_asset_root(dir)`); library resolved from the
  `pathland.gtk.lib` system property or `java.library.path`.
- `RingEventReader` — decodes raw ring `EVENT` opcodes into
  `com.pathland.view.transport.Event`, resolving `TEXT_CHANGED`/`NAVIGATE`
  strings from the shared host → guest event arena (header `0x40`), plus the
  four `MEDIA_*` events (play state, time, ended, volume). Headless unit tests
  cover pointer/value/date/text/navigate/media decoding and bounds behaviour.
- `GtkHost` (`main`) — mounts `MusicPlayerView` (the Apple Music-style player:
  library, now-playing sidebar, player bar with transport/seek/volume), extracts
  the demo's audio + covers to a runtime dir, sets the asset root, runs GTK
  (1180×800), drains and dispatches events. Blocks until the window closes.
- The `Emitter` sets `BINDING_ID` on bound controls for `RingOpcodeSink`s (the
  GTK renderer gates `TEXT_CHANGED`/`VALUE_CHANGED` on it).

## Not implemented / gaps

- **Playback requires GStreamer** with the mp3 decoding plugins installed (the
  brew `gstreamer` formula bundles them); without it the app runs but the
  pipeline errors on play.
- The GTK renderer's `attach_control_events` covers `TOGGLE`/`SLIDER`/
  `TEXT_FIELD`/`PICKER` only: `DATE_PICKER`, `STEPPER`, `COLOR_PICKER`, and
  `TEXT_EDITOR` render but do not (yet) emit events under GTK.
- macOS requires `-XstartOnFirstThread` (GTK owns the main thread).
- No automated GUI test (headless renderer tests live in `pathland-render-gtk`).

## Verified by

`mvn test -pl pathland-gtk-demo` — `RingEventReaderTest` (6 tests, headless, no
GTK). Manual: `GtkHost` boots the window and renders `MusicPlayerView` via the
shared ring; with GStreamer installed the pipeline reaches `PLAYING` and reports
`MEDIA_*` events (verified with a seeded `playing=true` run + `GST_DEBUG`). The
full Java reactor compiles with this module.