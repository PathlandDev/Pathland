# pathland-qt-demo — implementation status

**Last updated:** November 3, 2026

Fully Kotlin JNA desktop demo: the shared Kotlin `MusicPlayerView` under the native
Qt Quick renderer (`libpathland_qt`), over the `libpathland_core` shared ring.

## Implemented

- **`QtHost`** — mounts the Kotlin music player through the emitter into the shared
  ring, seeds per-session state/theme, extracts the shared demo assets, and runs
  `pathland_qt_run_ring`; blocks until the window closes.
- **`QtRendererNative`** — JNA binding for `pathland_qt_run_ring` +
  `pathland_qt_set_asset_root`; resolves `pathland.qt.lib` or `java.library.path`.
- **`RingEventReader`** — decodes raw ring `EVENT` opcodes, event-arena strings,
  media events, and `FIT_CHANGED`.
- **Fully Kotlin host** — no Java DSL, no Swing/Qt API in app code.

## Not implemented / gaps

- Qt6 dev libraries are required to build/run the renderer.
- macOS needs `-XstartOnFirstThread`.
- No automated GUI test; the headless `RingEventReaderTest` covers decoding.

## Verified by

`mvn test -pl pathland-qt-demo` — `RingEventReaderTest` (5 tests). Manual:
`scripts/run-java-qt-demo.sh`.
