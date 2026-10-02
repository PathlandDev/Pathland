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
    (indeterminate), `GAUGE` → `LineGauge` (`VALUE`/`MIN_VALUE`/`MAX_VALUE`).
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
- **Demo** (`pathland-render-tui-demo`): DSL → `Engine` → shared ring →
  renderer; quits on `q`/`Esc`/`Ctrl+C`.

## Not implemented / gaps

- **No input yet** (M2 planned): mouse hit-testing → `POINTER_*`, keyboard →
  `KEY_*`, `VALUE_CHANGED`/`TEXT_CHANGED` from controls, `EVENT_LISTENERS`/
  `BINDING_ID` gating, event write + wake/drain loop.
- **No grid/ZStack/ScrollView yet** (M3 planned): `GRID`/`GRID_ROW`/
  `GRID_TRACKS`, `ZSTACK` overlay + 2D alignment, `SCROLLVIEW` + `Scrollbar` +
  `SCROLL`/`WHEEL`, `PICKER`/`MENU`/`DATE_PICKER`, `SHAPE`.
- **No design-token default tables for TUI colors** yet (`SET_DESIGN_TOKEN`
  overrides decode, but `COLOR`-token refs resolve to the shared `pathland-host`
  concrete defaults; a TUI-specific table + scheme detection is a follow-up).
- **No terminal-resize reporting** (`META::ENVIRONMENT` viewport) — the app
  re-draws to `frame.area()` automatically, but the guest is not notified.
- **`IMAGE`** deferred (feature-gated `ratatui-image`, M5); **`AUDIO`/`VIDEO`**
  out of scope (status line only); effects (`SHADOW_*`/`BLUR`/`ROTATION`/
  `SCALE`/`OPACITY`) and `FONT_SIZE`/`BORDER_RADIUS` ignored (renderer fidelity).

## Verified by

`cargo test -p pathland-render-tui` — layout decisions (stack placement,
spacing gaps, filler absorption, `FILL` stretch, cross-axis alignment, padding)
and `TestBackend` buffer assertions (text, spacing, button border, fill color,
gauge ratio). `cargo run -p pathland-render-tui-demo` for a visual check.