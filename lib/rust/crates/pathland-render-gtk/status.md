# pathland-render-gtk — implementation status

**Last updated:** September 14, 2026

The **GTK4 renderer**: maps opcode frames incrementally onto native GTK widgets
(shared-memory desktop path). Protocol contract: `spec/`. Design-token contract:
`spec/TOKENS.md`.

## Implemented

- **Components → widgets** (`widget_kind`/`build_widget`):
  - `VSTACK`/`HSTACK`/`LAZY_VSTACK`/`LAZY_HSTACK` → `GtkBox` (spacing/alignment/
    padding/content-margins),
  - `TEXT` → `GtkLabel` (text, `FONT_SIZE`/`FONT_WEIGHT`/`COLOR` via Pango),
  - `BUTTON` → `GtkButton` (label; composite body when it has children),
  - `GRID`/`LAZY_VGRID`/`LAZY_HGRID` → `GtkGrid` (row-major cells from child
    index + `WIDTH` column count),
  - `SCROLLVIEW` → `GtkScrolledWindow` (single child),
  - `ZSTACK` → `GtkOverlay`,
  - `SPACER` → expanding box, `IMAGE` → `GtkPicture` (`IMAGE_SOURCE`),
  - `TOGGLE` → `GtkSwitch`/`GtkCheckButton`/`GtkToggleButton` by `TOGGLE_STYLE`
    (`SELECTED` ↔ active),
  - `SLIDER` → `GtkScale` (`VALUE`/`MIN`/`MAX`/`STEP`),
  - `TEXT_FIELD` → `GtkEntry` (`LABEL`/`PROMPT`/`IS_SECURE`),
  - `TEXT_EDITOR` → `GtkTextView` (multi-line),
  - `COLOR` → `GtkDrawingArea` (solid fill), `SHAPE` → `GtkDrawingArea`
    (`SHAPE_KIND`), `DIVIDER` → `GtkSeparator`,
  - `PROGRESS_VIEW` → `GtkProgressBar`/`GtkSpinner`,
  - `GAUGE` → `GtkLevelBar`, `STEPPER` → `GtkSpinButton`,
  - `DATE_PICKER` → `GtkCalendar`, `PICKER` → `GtkDropDown` (option children +
    `SELECTION`), `MENU` → `GtkMenuButton`, `COLOR_PICKER` → `GtkColorButton`.
  Unknown components → blank `GtkLabel`.
- **Container + composite reconciliation** (diff-guarded): stacks/grid/scroll/
  overlay children, and **Composite Override Mode** for `BUTTON`/`TOGGLE`/`MENU`
  with children (custom body wrapped in the native button shell).
- **Style properties**: `VISIBLE`, `OPACITY`, `WIDTH`/`HEIGHT`,
  `CONTENT_MARGINS`, `PADDING` + per-edge, `BACKGROUND_COLOR`, `BORDER_WIDTH`/
  `COLOR`/`RADIUS`, `FONT_FAMILY`/`FONT_WEIGHT` (CSS provider), `COLOR`,
  `FONT_SIZE`.
- **Events**: pointer `POINTER_DOWN`/`MOVE`/`UP` via `GestureClick`/
  `EventControllerMotion` (`EVENT_LISTENERS`-driven), plus **value/text events**
  (`VALUE_CHANGED` from toggle/slider/picker, `TEXT_CHANGED` from text field)
  **gated by `BINDING_ID`** (transport-aware event guards), sent through the
  two-way event arena.
- **Platform back / `NAVIGATE`**: a window-level `EventControllerKey`
  (capture phase, attached once in `run_with_pump`) maps Escape — and
  BackSpace when no text entry has focus — to `Event::Navigate { url: None }`
  through the shared event sink. `NAVIGATE` is global (never node-keyed),
  matching spec/EVENTS.md.
- **Native navigation slot (`AdwNavigationView`)**: a stack node carrying the
  `ROUTE` property (a `NavigationContainer`, spec/DSL.md §4.5) renders as an
  `AdwNavigationView` instead of a `GtkBox`. The adapter reconciles its page
  stack **by depth** (`NAV_DEPTH` 0x201A, U32): it pops pages down to the
  app's depth, then **pushes** when the route is deeper (normal push / deep
  link), **replaces the top page** when the depth is unchanged and the route
  differs (a guard redirect / `replace()`), and **refreshes in place** when the
  visible page already shows the route (a signal update, or a re-emit after a
  user-initiated back). In the default chrome mode (`NAV_CHROME` 0x201B
  `PlatformDefault`, or missing) each page wraps the destination in a
  `ToolbarView` + `HeaderBar` (`show-back-button`) — the native top bar and
  back button; in `Custom` chrome mode (`Chrome.CUSTOM` → `NAV_CHROME=1`) the
  page child is the bare destination (the developer owns all nav UI). The
  header-bar back button's `popped` signal drops the
  page from the adapter's mirror and emits `Event::Navigate { url: None }`
  (suppressed during renderer-driven pops), so native back and Escape both flow
  into the app's `router.pop()`. The back-stack stays app-owned; the
  `AdwNavigationView` is only the renderer's rendered-output cache.
  `libadwaita` (0.7, `v1_4` feature) is a new native dependency.
- **Design tokens / theming (spec/TOKENS.md renderer contract)**:
  - `STYLE::SET_DESIGN_TOKEN` overrides are stored (`host.rs`), base + `dark.*`
    split by path prefix; STRING-valued overrides resolve the value string from
    the arena.
  - `DESIGN_TOKEN`-typed `SET_PROPERTY` values record the token path
    (`HostNode::token_refs`) and **resolve at apply time** against the theme,
    the active scheme, and the parent-fallback chain into concrete
    `properties`/`strings` (`RenderTree::resolve_tokens`, reusing
    `pathland_core::tokens`).
  - **Tier-1 default tables** (light + dark) in `host.rs`
    (`concrete_default_tables`) — concrete platform-appropriate fallbacks,
    **enriched with GTK-native theme colors** (`tokens.rs`:
    `StyleContext::lookup_color` of `@theme_*`/`@borders`/`@success_color`…)
    from the first widget's style context (headless → concrete fallback).
  - **Scheme detection** from the native GTK variant
    (`gtk::Settings` `prefer-dark` + `-dark` theme name); a notify handler
    calls `GtkRenderer::set_scheme`, which re-enriches native defaults and
    re-resolves + re-applies every token-referencing widget. Scheme is never
    carried by the protocol.
  - The generative `space.<N>` family resolves `space.base` × N.
  - Since GTK CSS has no custom properties, tokens resolve to **concrete**
    values (rgba/px/Pango) before the existing CSS-provider / text-style paths.
- **C ABI for foreign hosts (`capi.rs`)**:
  - `pathland_gtk_run(host, on_event)` — pump a `pathland-view-native`
    `NativeHost`'s ring in-process.
  - `pathland_gtk_run_ring(ring, on_event, width, height)` — pump a
    **`libpathland_core` ring borrowed in-process** (from
    `pathland_core_ring_mut`): the route for hosts that emit opcodes themselves
    (the Java DSL writes into the ring via `RingOpcodeSink` and the renderer
    pumps the same ring in place — zero-copy, no `pathland-view-native` host).
    `width`/`height` size the default window.
  - Both block until the GTK main loop exits and share `run_with_pump_sized`
    (the 420×220 default lives in `run_with_pump`).
- **Robustness fixes (exercised by the Java `SplitNavDemo`, which uses controls
  the Rust demo never did)**:
  - **Slider step**: GTK's `gtk_scale_new_with_range` asserts a non-zero step;
    the renderer previously passed `0.0` unconditionally (a `SLIDER` crashed the
    process). A `STEP_VALUE` of 0 ("continuous" in the DSL) now derives a small
    increment (`(max-min)/100`, min `0.001`).
  - **Lazy stacks**: `LAZY_VSTACK`/`LAZY_HSTACK` map to `WidgetKind::Stack`, but
    `stack_orientation` only accepted `VSTACK`/`HSTACK` — a lazy stack panicked
    at `stack_widget`. They now map to the same eager `GtkBox` orientations.
  - **Re-entrant control signals**: applying a frame borrows the pump
    zero-copy; a *programmatic* value change (e.g. `GtkScale::set_value`) fires
    the connected GTK signal synchronously and re-entered `emit`, double-borrowing
    the pump (`RefCell already borrowed`) and panicking. Such events are now
    **deferred to the idle pump** (`flush_pending` before each frame), which also
    prevents a control-echo loop when the host writes the same value back.

## Not implemented / gaps

- `SHAPE` `Path`/rounded rendering is an approximation (rectangle/circle fill).
- `MENU` renders a menu button without a popover item list.
- No `ACTION_ID`-only gating (events require `BINDING_ID`).
- Composite bodies attach to button-like controls only; other controls ignore
  children.
- `LAZY_*` renders eagerly (no GTK windowing).
- The `AdwNavigationView` adapter does not (yet) reflect the `TRANSITION`
  (0x1031) hint into a native animation choice — libadwaita animates its
  standard push/pop; per-transition styling is a follow-up.
- **Duplicate same-route pushes collapse**: pages are keyed by the current
  route tag on top — a `push` to a route that already sits on top refreshes in
  place instead of stacking a second identical page (the app's back-stack is
  the source of truth for depth; the native stack is only its rendered cache).
- A **multi-step jump deeper in one frame** (the app emitting only the final
  destination) pushes a single page; intermediate pages are not fabricated.

## Verified by

`cargo test -p pathland-render-gtk` — layout mapping, `widget_kind` (full
component map), container/composite kinds, string resolution, value type
mapping (headless; widget construction is exercised without a display), the
nav-slot detection + `nav_action` depth decision + `NAV_CHROME` mode
(`is_custom_chrome`), and design tokens: `DESIGN_TOKEN` property refs resolve
against concrete defaults, `SET_DESIGN_TOKEN` overrides + `dark.*` + scheme
change re-resolve, generative `space.N`, and the GTK-native default enrichment
(fallback path headless).