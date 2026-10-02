# pathland-render-tui — implementation status

**Last updated:** October 2, 2026

The **Ratatui (TUI) renderer**: maps opcode frames incrementally onto native
terminal widgets (immediate-mode redraw each terminal frame). Protocol contract:
`spec/`. Retained-tree decoding + token resolution come from the shared
`pathland-host` crate.

## Implemented

- **Components → widgets** (`render.rs`):
  - `VSTACK`/`HSTACK`/`LAZY_VSTACK`/`LAZY_HSTACK` → ratatui `Layout` (spacing/
    padding/`ALIGNMENT` cross-axis, `FILL`/greedy children split leftover on the
    main axis),
  - `TEXT` → `Paragraph` (per-line `Span`s; `COLOR`/`BACKGROUND_COLOR`/
    `FONT_WEIGHT`/`FONT_STYLE`/`UNDERLINE`/`STRIKETHROUGH` styles; `TEXT_ALIGNMENT`;
    `LINE_LIMIT` truncation),
  - `BUTTON` → `Paragraph` + `Block` border (`BORDER_WIDTH`/`BORDER_COLOR`);
    composite bodies render as a horizontal row,
  - `SPACER` → flexible `Constraint`; `DIVIDER` → `─` line; `COLOR` → `Fill`
    background,
  - `PROGRESS_VIEW` → `Gauge` (determinate) / animated braille spinner
    (indeterminate), `GAUGE` → `LineGauge` (`VALUE`/`MIN_VALUE`/`MAX_VALUE`),
  - `SLIDER` → `LineGauge` at the value ratio, `TOGGLE` → `[x] label`,
    `STEPPER` → `− value +`, `TEXT_FIELD`/`TEXT_EDITOR` → `Paragraph` + a focus
    cursor.
- **Interactive input** (`render.rs` + `run.rs`):
  - Mouse down/up/move/drag → `POINTER_DOWN`/`UP`/`MOVE`, wheel → `WHEEL`,
    routed by hit-testing the per-frame `id → Rect` cache (draw order =
    z-order); keyboard: `Tab`/`Shift+Tab` focus cycling, `Enter`/`Space`
    activates the focused button (a synthesized press+release the app's
    `TapRecognizer` turns into a tap), arrows adjust `SLIDER`/`STEPPER` →
    `VALUE_CHANGED`, typing → `TEXT_CHANGED`/`SUBMIT`, keys forward as
    `KEY_DOWN`.
  - **Gating (GTK parity)**: `EVENT_LISTENERS` bits gate pointer/key/wheel
    (buttons default to pointer listeners); `BINDING_ID` gates value/text
    events.
  - Focus model: single `focus` id, REVERSED focus styling, cursor placement
    on text inputs.
  - `run(pump, wake)` event loop over a `Pump` (`FrameSource` +
    `DriverTransport`, `Rc<RefCell<P>>`): apply frames → draw → route input →
    `send_input` + wake; the **host drains** (renderer only writes, GTK rule).
    Quit `q`/`Ctrl+C`; `Esc` clears focus or sends `NAVIGATE` back.
  - **C ABI** (`capi.rs`, cdylib): `pathland_tui_run_ring(ring, on_event)`
    borrows a `libpathland_core` `RingTransport` in-process via a
    `CoreRingHandle` pump (mirror of `pathland_gtk_run_ring`).
- **Layout** (`layout.rs`, pure + headless-testable): bottom-up natural cell
  size, top-down `Rect` assignment; per-edge `PADDING` (`PADDING_*` > `PADDING`
  > `CONTENT_MARGINS`), `SPACING` gaps, 2D `ALIGNMENT` → cross-axis position
  (`align_h`/`align_v`, codes 0–8), `FILL`/`HUG`/Fixed `WIDTH`/`HEIGHT`, greedy
  primitives (`SPACER`/`COLOR`/`SCROLLVIEW`). 1 protocol point = 1 cell.
- **Per-frame geometry cache**: `id → Rect` (`TuiRenderer::rect`) for
  hit-testing; rebuilt every draw.
- **Retained-tree decoding** via `pathland_host::RenderTree` (`apply_frame`),
  incl. `SET_DESIGN_TOKEN` overrides + token refs; `TuiRenderer::set_scheme`
  re-resolves on scheme change.
- **Demos**: `pathland-render-tui-demo` (Rust DSL counter, interactive via
  `TapRecognizer`) and `pathland-tui-demo` (Java DSL `MusicPlayerView` through
  `pathland_tui_run_ring`, mirroring `GtkHost`); both quit on `q`/`Ctrl+C`.

## Not implemented / gaps

- **No grid/ZStack/ScrollView yet** (M3 planned): `GRID`/`GRID_ROW`/
  `GRID_TRACKS`, `ZSTACK` overlay + 2D alignment, `SCROLLVIEW` + `Scrollbar` +
  `SCROLL`/`WHEEL` scrolling (a `SCROLLVIEW` currently shows its first child at
  natural size), `PICKER`/`MENU`/`DATE_PICKER`, `SHAPE`.
- **`IMAGE`** deferred (feature-gated `ratatui-image`, M5); **`AUDIO`/`VIDEO`**
  out of scope (render as empty placeholders — the music player's controls work,
  playback/covers do not).
- **No design-token default tables for TUI colors** yet (`SET_DESIGN_TOKEN`
  overrides decode, but `COLOR`-token refs resolve to the shared `pathland-host`
  concrete defaults; a TUI-specific table + scheme detection is a follow-up).
- **No terminal-resize reporting** (`META::ENVIRONMENT` viewport) — the app
  re-draws to `frame.area()` automatically, but the guest is not notified.
- Effects (`SHADOW_*`/`BLUR`/`ROTATION`/`SCALE`/`OPACITY`) and
  `FONT_SIZE`/`BORDER_RADIUS` ignored (renderer fidelity).
- Value/text events require `BINDING_ID` (`ACTION_ID`-only gating not
  implemented, GTK parity).

## Verified by

`cargo test -p pathland-render-tui` — layout decisions (stack placement,
spacing gaps, filler absorption, `FILL` stretch, cross-axis alignment, padding),
`TestBackend` buffer assertions (text, spacing, button border, fill color,
gauge ratio), and input routing (click → pointer events on the button,
Tab focus cycling, slider arrows → `VALUE_CHANGED`, button activation
synthesizes press+release). `cargo run -p pathland-render-tui-demo` and the
Java `pathland-tui-demo` (`./scripts/run-java-tui-demo.sh`) for visual checks.