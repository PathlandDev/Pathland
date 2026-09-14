# pathland-gtk-demo (Java) — implementation status

**Last updated:** September 14, 2026

Runs the shared framework-agnostic demo views (`com.pathland.demo`, e.g.
`SplitNavDemo`) under the **native GTK4 renderer** (`libpathland_gtk`) — the
cross-language, shared-renderer story for Java. Protocol contract: `spec/`.

## How it works

- The Java DSL mounts the view through its fine-grained `Emitter` into a
  `libpathland_core` shared ring (`RingOpcodeSink`, **zero-copy**).
- The in-process GTK renderer pumps the same ring in place
  (`pathland_gtk_run_ring`, via `PathlandCore.ringMut`); no
  `pathland-view-native` host is involved.
- Native inputs round-trip as `EVENT` opcodes that this host drains
  (`pathland_core_drain_events`) and dispatches into the app's bindings via the
  shared `InputDispatcher` (taps, text/value/date inputs, router back).
- State/theme/platform seeding mirrors the server session: `PersistentState`
  over an `InMemoryStateStore`, `DemoTheme.adaptive()` as the emitter theme, and
  `Platform.ACTIVE_PATH` injected as a host signal (`/home`) — `SplitNavDemo`
  reads it and builds its bound router.

## Implemented

- `GtkRendererNative` — JNA binding to `libpathland_gtk`
  (`pathland_gtk_run_ring(ring, on_event, width, height)`); library resolved
  from the `pathland.gtk.lib` system property or `java.library.path`.
- `RingEventReader` — decodes raw ring `EVENT` opcodes into
  `com.pathland.view.transport.Event`, resolving `TEXT_CHANGED`/`NAVIGATE`
  strings from the shared host → guest event arena (header `0x40`).
  Headless unit tests cover pointer/value/date/text/navigate decoding and
  bounds behaviour.
- `GtkHost` (`main`) — mounts `SplitNavDemo`, runs GTK (1100×720), drains and
  dispatches events. Blocks until the window closes.
- The `Emitter` sets `BINDING_ID` on bound controls for `RingOpcodeSink`s (the
  GTK renderer gates `TEXT_CHANGED`/`VALUE_CHANGED` on it).

## Not implemented / gaps

- The GTK renderer's `attach_control_events` covers `TOGGLE`/`SLIDER`/
  `TEXT_FIELD`/`PICKER` only: `DATE_PICKER`, `STEPPER`, `COLOR_PICKER`, and
  `TEXT_EDITOR` render but do not (yet) emit events under GTK — those sections
  of the demo views are non-interactive on this path.
- macOS requires `-XstartOnFirstThread` (GTK owns the main thread).
- No automated GUI test (headless renderer tests live in `pathland-render-gtk`).

## Verified by

`mvn test -pl pathland-gtk-demo` — `RingEventReaderTest` (5 tests, headless, no
GTK). The full Java reactor compiles with this module.