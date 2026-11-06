# pathland-desktop-demo — implementation status

**Last updated:** November 5, 2026

Runs the shared Kotlin `MusicPlayerView` (`dev.pathland.demo.music` from
`pathland-demo-views-kotlin`) under the **native GTK4 renderer**
(`libpathland_gtk`, `dev.pathland.demo.gtk.GtkHost`) or the **Qt Quick renderer**
(`libpathland_qt`, `dev.pathland.demo.qt.QtHost`) — the cross-language,
shared-renderer story. Protocol contract: `spec/`.

## How it works

- The Kotlin DSL mounts the view through its fine-grained `Emitter` into a
  `libpathland_core` shared ring (`RingOpcodeSink`, **zero-copy**).
- The in-process renderer pumps the same ring in place
  (`pathland_gtk_run_ring` / `pathland_qt_run_ring`); no
  `pathland-view-native` host is involved.
- Native inputs round-trip as `EVENT` opcodes that the host drains
  (`pathland_core_drain_events`) and dispatches into the app's bindings via the
  shared `InputDispatcher` (taps, text/value/date inputs, **media events**,
  router back).
- State/theme/platform seeding mirrors the server session: `PersistentState`
  over an `InMemoryStateStore`, `DemoTheme.adaptive()` as the emitter theme.
- **App-driven media**: the renderer plays the demo's tracks and reports
  `MEDIA_*` events back through the ring. The app's web-style
  `/_pathland/assets/...` paths resolve against a local asset root: the demo
  extracts its audio + covers from the shared `pathland-demo-views-kotlin` jar
  at startup (`DemoAssets.extractToTemp`) and calls the renderer's
  `set_asset_root`.

## Implemented

- **`GtkRendererNative`** — JNA binding to `libpathland_gtk`
  (`pathland_gtk_run_ring` + `pathland_gtk_set_asset_root`); library resolved
  from `pathland.gtk.lib` or `java.library.path`.
- **`QtRendererNative`** — JNA binding to `libpathland_qt`
  (`pathland_qt_run_ring` + `pathland_qt_set_asset_root`); library resolved
  from `pathland.qt.lib` or `java.library.path`.
- **`RingEventReader`** (shared, `dev.pathland.demo.desktop`) — decodes raw ring
  `EVENT` opcodes into `dev.pathland.view.core.transport.Event`, resolving
  `TEXT_CHANGED`/`NAVIGATE` strings from the shared host → guest event arena
  (header `0x40`), plus `MEDIA_*` and `FIT_CHANGED` (routed to the emitter's fit
  sink, so the `SizeThatFits` slot swaps to its wide candidate).
- **`GtkHost`** (`main`) — Java host; mounts the Apple Music-style player,
  extracts the demo assets, runs GTK (1180×800), drains and dispatches events.
- **`QtHost`** (`main`) — Kotlin host; mounts the player, extracts the demo
  assets, runs Qt Quick, drains and dispatches events.
- The `Emitter` sets `BINDING_ID` on bound controls for `RingOpcodeSink`s (the
  GTK renderer gates `TEXT_CHANGED`/`VALUE_CHANGED` on it).

## Not implemented / gaps

- **GTK playback requires GStreamer** with the mp3 decoding plugins installed;
  without it the app runs but the pipeline errors on play.
- The GTK renderer's `attach_control_events` covers `TOGGLE`/`SLIDER`/
  `TEXT_FIELD`/`PICKER` only: `DATE_PICKER`, `STEPPER`, `COLOR_PICKER`, and
  `TEXT_EDITOR` render but do not (yet) emit events under GTK.
- Qt6 dev libraries are required to build/run the Qt renderer.
- macOS requires `-XstartOnFirstThread` for both hosts.
- No automated GUI test (headless renderer tests live in the Rust crates).

## Verified by

`mvn test -pl pathland-desktop-demo` — `RingEventReaderTest` (headless, no GTK/Qt;
pointer/value/date/text/navigate/media/fit decoding and bounds behaviour).
Manual: `scripts/run-java-gtk-demo.sh` and `scripts/run-java-qt-demo.sh`.
