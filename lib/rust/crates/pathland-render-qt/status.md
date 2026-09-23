# pathland-render-qt — implementation status

**Last updated:** September 23, 2026

The **Qt Quick renderer**: maps opcode frames incrementally onto a Qt Quick
scene graph (shared-memory desktop path). Protocol contract: `spec/`.
Design-token contract: `spec/TOKENS.md`.

**Language split.** The crate is a **hybrid**:
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
  links the Qt frameworks/dylibs (framework search via the transitive
  `rustc-link-search=framework` kind, so demo binaries link too). The protocol
  core (`pathland-core`/`view`) stays `no_std`/wasm-safe.
- **FFI boundary** (`src/ffi.rs` + `src/qt/pathland_qt.h`): a flat
  `PathlandQtCommand` batch (create/delete/insert/remove/move/set-text/
  set-property/set-string-property/reset-node/set-scheme) plus a
  `PathlandQtEvent` (pointer-up/value-changed/text-changed) flowing C++ → Rust.
- **Delta diff** (`src/renderer.rs`): `QtRenderer` keeps a sent-state cache and
  emits only the commands that change it — an unchanged tree emits **zero**
  commands. Property changes re-send the node's *complete* property set
  preceded by `RESET_NODE` (so the C++ layer resets removed properties to
  renderer defaults). Numeric props are emitted with `VALUE` last (Qt Quick
  clamps a control's value when `from`/`to` aren't configured yet). Tree roots
  are children of pseudo-parent `0` (window contentItem). Ghost children after
  `DELETE_NODE` are guarded in the walk. Headless-tested.
- **Qt widget mapping** (`src/qt/qt_layer.cpp`): component → QML item factory —
  `VSTACK`/`HSTACK`/lazy stacks → `Column`/`Row`, `GRID`+lazy grids → `Grid`,
  `ZSTACK` → `Item`, `SCROLLVIEW` → `ScrollView`, `TEXT` → `Text`, `SPACER`/
  unknown → `Item`, `DIVIDER` → `Rectangle`, `COLOR`/`SHAPE` → `Rectangle`,
  `IMAGE` → `Image`, `BUTTON` → `Button`, `TEXT_FIELD` → `TextField`, `SLIDER`
  → `Slider`, `TOGGLE` → `Switch` (controls embed `onX: bridge.<event>(id, …)`
  handlers). Property application via generic `setProperty` (Qt ignores
  unknown props): spacing/`align`/padding+edges, width/height (`-1` FILL →
  `anchors.fill`, `-2` HUG → implicit), color, font size/weight/family, opacity,
  visible, z, value/min/max/step, selected→checked, enabled, `IS_SECURE` →
  `echoMode`, prompt/label→placeholder, image source. `RESET_NODE` resets
  renderer-owned style defaults (value props untouched so a slider/toggle
  doesn't lose position on echo).
- **Events** (spec/EVENTS.md guards): the `Bridge` reports control inputs
  (`tap` → `POINTER_UP`, `valueChanged` → `VALUE_CHANGED`, `textChanged` →
  `TEXT_CHANGED`) to Rust, which encodes the `Event`, writes it into the event
  ring, and wakes the host (renderer never drains). Interactions are gated by
  `BINDING_ID`/`ACTION_ID` and programmatic value sets suppress echoes.
- **Qt shell**: init/run split (headless tests drive `init` + `apply` without
  an event loop); `pathland_qt_layer_run` owns the window + a zero-millisecond
  idle `QTimer` that ticks Rust's pump (Qt analogue of GTK's idle pump).
- **C ABI for foreign hosts** (`src/capi.rs`): `pathland_qt_run(host,
  on_event)` (over a `pathland-view-native` `NativeHost`) and
  `pathland_qt_run_ring(ring, on_event, width, height)` (over a borrowed
  `libpathland_core` ring), both mirroring the GTK renderer's contract. Passes
  a bare program name to Qt.
- **Rust demo ergonomics** (`run`): `pathland_qt::run(app_id, title,
  Rc<RefCell<RingTransport>>, on_event)` mirrors `pathland_render_gtk::run`;
  the pump borrow is held across frame application (zero-copy batch view of the
  shared ring).

## Not implemented / gaps

- Pointer *streams* (down/move) gated by `EVENT_LISTENERS` are not yet emitted
  (only taps via Button `clicked`); `EVENT_LISTENERS` is carried but not yet
  consumed by the Qt layer.
- `BACKGROUND_COLOR`/border on stacks/controls (Qt Quick Controls own their
  background delegate; mapping is a follow-up), `SHAPE` path approximation,
  `PROGRESS_VIEW`/`GAUGE`/`STEPPER`/`DATE_PICKER`/`PICKER`/`MENU`/`COLOR_PICKER`
  not mapped yet.
- Design-token scheme detection (`QGuiApplication::styleHints`) + `SET_SCHEME`
  handling is not wired (values resolve in the shared core; a re-apply on
  scheme change is a follow-up).
- `META::RESET` resets the C++ scene (widgets + gated set) but the run shell
  doesn't watch for it; no Java JNA demo yet.
- Lazy views render eagerly; duplicate same-route pushes / nav adapter not
  implemented.

## Verified by

`cargo test -p pathland-render-qt` (needs Qt6 dev libs; offscreen for the FFI
tests) — the delta diff suite, the end-to-end FFI batch
(`command_batch_crosses_into_cpp_offscreen`), and the **full pipeline**
(`renderer_drives_live_qml_scene`: DSL → engine → frame → shared decode →
delta diff → FFI → live QML `Column`/`Text`/`Button`/`Slider` with spacing/value
applied and a delta re-applied). Full workspace: `cd lib/rust && cargo test`.
Demo: `scripts/run-rust-qt-demo.sh`.