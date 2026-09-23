# pathland-render-qt — implementation status

**Last updated:** September 23, 2026

The **Qt Quick renderer**: maps opcode frames incrementally onto a Qt Quick
scene graph (shared-memory desktop path). Protocol contract: `spec/`.
Design-token contract: `spec/TOKENS.md`.

**Language split (M2 skeleton).** The crate is a **hybrid**:
- **Rust** owns the shared decode core (`pathland-render-core`), a sent-state
  **delta diff** that turns each frame into a batch of C-compatible commands,
  the `Pump` transport seam (frames in / events out), the Qt main-loop shell,
  and the C ABI for foreign hosts.
- **C++** (in `src/qt/`) owns the Qt application shell (QGuiApplication +
  QQuickWindow + QQmlEngine + a QML-facing `Bridge` QObject) and applies the
  delta command batch to QML items. Built via `cc` + `moc` in `build.rs`;
  needs Qt6 dev libs (Homebrew: `qtbase qtdeclarative`).

## Implemented

- **Build/toolchain**: `build.rs` locates Qt via `qmake -query` (Homebrew +
  Linux CI), runs `moc` on the bridge, compiles the C++ layer with `cc`, and
  links the Qt frameworks/dylibs. Workspace member; the protocol core
  (`pathland-core`/`view`) stays `no_std`/wasm-safe.
- **FFI boundary** (`src/ffi.rs` + `src/qt/pathland_qt.h`): a flat
  `PathlandQtCommand` batch (create/delete/insert/remove/move/set-text/
  set-property/set-string-property/reset-node/set-scheme) — Rust produces the
  batch, C++ applies it. `b = (valueType<<16)|propertyId`, `c = value`, mirroring
  the opcode wire.
- **Delta diff** (`src/renderer.rs`): `QtRenderer` keeps a sent-state cache of
  what the C++ layer has and, per frame, emits only the commands that change
  it — an unchanged tree emits **zero** commands. Property changes re-send the
  node's *complete* property set preceded by `RESET_NODE` (so the C++ layer can
  reset removed properties to renderer defaults). Tree roots are children of
  the pseudo-parent `0` (window contentItem). Headless-tested.
- **Qt shell** (`src/qt/qt_layer.cpp`): init/run split (headless tests drive
  `init` + `apply` without an event loop); `pathland_qt_layer_run` owns the
  window + a zero-millisecond idle `QTimer` that ticks Rust's pump (the Qt
  analogue of GTK's idle pump). The `Bridge` is exposed to QML as a context
  property (`bridge.controlEvent(nodeId)` etc.) and wakes the host (no
  payload — the renderer never drains events).
- **C ABI for foreign hosts** (`src/capi.rs`): `pathland_qt_run(host,
  on_event)` (over a `pathland-view-native` `NativeHost`) and
  `pathland_qt_run_ring(ring, on_event, width, height)` (over a borrowed
  `libpathland_core` ring), both mirroring the GTK renderer's contract. Passes
  a bare program name to Qt (the host's argv belongs to the embedding process).

## Not implemented / gaps (M3 scope)

- **No widget mapping yet**: `apply` is a stub that records what crossed the
  boundary (count + last text). Component→QML mapping, STYLE property
  application, control signals (`onClicked` → `bridge`), event encoding, and
  design tokens land next.
- No `META::RESET`/scheme handling in the C++ layer (Rust `reset()` wires the
  command; the C++ side resets only its test counters).
- No Java demo / JNA binding yet.

## Verified by

`cargo test -p pathland-render-qt` (needs Qt6 dev libs; offscreen for the FFI
round-trip) — the delta diff (`first_frame`, `unchanged_frame_emits_zero_commands`,
`spacing_delta`, `text_delta`, `deleted_node`, `children_diff`, `roots_are_children_of_content_item`)
and the end-to-end FFI batch (`command_batch_crosses_into_cpp_offscreen`).
Full workspace: `cd lib/rust && cargo test`.