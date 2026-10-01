# pathland-render-gtk — implementation status

**Last updated:** September 30, 2026

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
  with children (custom body wrapped in the native button shell). A `BUTTON`
  composite body is a **horizontal row** with centered children (matching the
  HTML renderer's `.pathland-button` `inline-flex`); `TOGGLE`/`MENU` bodies stay
  vertical (HTML wraps those as block/inline-block). A composite control **sizes
  to its content on the main axis** (the button's main-axis `valign`/`halign` is
  `Center`, mirroring HTML's `inline-flex`) unless the node is explicitly
  `FILL`-sized — otherwise the enclosing box's default `Fill` alignment stretches
  it (e.g. a library row button absorbs the viewport height, inflating
  fixed-size images).
- **Style properties**: `VISIBLE`, `OPACITY`, `WIDTH`/`HEIGHT`,
  `CONTENT_MARGINS`, `PADDING` + per-edge, `BACKGROUND_COLOR`, `BORDER_WIDTH`/
  `COLOR`/`RADIUS`, `FONT_FAMILY`/`FONT_WEIGHT` (CSS provider), `COLOR`,
  `FONT_SIZE`.
- **Fixed-size images scale to the points box**: a `WIDTH`×`HEIGHT` on an
  `IMAGE` is a **logical-points box** (spec/OPCODE.md §units), and `GtkPicture`'s
  natural size is its content's intrinsic pixels — so a finite box was only a
  *minimum* request and the cover rendered at its intrinsic size. A fixed-size
  image now decodes and scales the content into the box (via gdk-pixbuf), making
  the picture's natural size equal the requested points (the bar cover renders
  36pt, the sidebar 220pt, the bar stays 76pt tall). `CONTENT_MODE` is honored:
  `Fit` → scale-to-fit + centered on a transparent canvas (letterbox, like
  `object-fit:contain`), `Fill` → scale-to-cover + center-crop (like
  `object-fit:cover`); absent → Fit. The scaled box is cached per node (steady
  state doesn't re-decode). A non-fixed (`FILL`/single-axis) image keeps the
  native `set_filename` loader. Retina *crispness* (decoding at the display
  scale factor) is a documented follow-up — layout is points-correct on every
  display.
- **`FILL` expansion**: a `WIDTH`/`HEIGHT` of `FILL` (-1.0, SwiftUI
  `maxWidth/maxHeight: .infinity`) now sets `hexpand`/`vexpand` + `Fill` axis
  alignment so arbitrary elements truly expand to the available space; a finite
  value is a size request and `HUG_CONTENT` leaves the natural size.
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
- **App-driven media (spec/EVENTS.md Media)**: an `AUDIO`/`VIDEO` node — or any
  node carrying a media source (a custom `AudioStyle`/`VideoStyle` body keeps its
  own component, e.g. a `VStack`) — gets a hidden **GStreamer `playbin` pipeline**
  driven by the node's media control properties: `AUDIO_SOURCE`/`VIDEO_SOURCE`
  sets the source URI (re-created on change, resuming if the app was playing),
  `PLAYBACK_STATE` → pipeline `Playing`/`Paused`, `MEDIA_POSITION` → seek
  (**echo-guarded**: a near-identical write — the app's `MEDIA_TIME_UPDATED` echo
  — does not seek), `MEDIA_VOLUME` → playbin volume. Media events report back
  through the shared event sink/ring: `MEDIA_TIME_UPDATED` (a 250 ms reporter
  polls `query_position` while the app requested playing and reports only when
  the position advanced ~1 second — the same cadence as the web client — 
  suppressed briefly after a seek), `MEDIA_ENDED` and error → play-state-false
  from polling the pipeline bus (`EOS`/`Error` messages, on the main thread).
  Playback is
  **GStreamer-direct** because GTK4's own media backend (`GtkMediaFile`) is
  compiled out of some builds (Homebrew's `gtk4` ships with
  `-Dmedia-gstreamer=disabled`); the app owns all playback state, the pipeline is
  the renderer's rendered output (the desktop analog of the web client's hidden
  `<audio>`).
- **Asset root**: `pathland_gtk_set_asset_root(const char*)` sets a directory
  that web-style `/_pathland/...` media/image source paths resolve against
  (`/_pathland/assets/x` → `<root>/assets/x`), so desktop apps reference the same
  asset paths as the web demos. Applied to `IMAGE_SOURCE`, `AUDIO_SOURCE`, and
  `VIDEO_SOURCE`.
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

- **Layout contract (spec/LAYOUT.md)**: aligned — stacks apply the per-child
  rule on **both** axes (main axis: only `FILL`/greedy children stretch and
  leftover goes to them, everything else positioned at the start; cross axis:
  only `FILL`/greedy children stretch, otherwise positioned by `ALIGNMENT`
  defaulting to Leading), grid cells apply the same rule, and the greedy
  primitives (`DIVIDER` cross-axis, `SPACER` main-axis, `COLOR`/`SCROLLVIEW`
  both) fill by nature. The old composite-button main-axis hack was retired
  (superseded by the parent stack's rule). Conformance C1–C6 covered by the
  pure decision tests in `layout.rs`.
- **Per-child expansion is explicit** (`sync_stack_children`): each child's
  `hexpand`/`vexpand` is set from the same effective-`FILL` decision as its
  alignment, so a Hug main-axis child (`valign=Start`, `vexpand=false`) can
  never claim leftover — a `GtkBox` otherwise hands extra space to children
  left at GTK's default `Align::Fill`, which spread e.g. a library's rows
  vertically instead of pinning them to the leading edge (the trailing
  `SPACER` absorbs the leftover). This also makes `SPACER` cross-axis
  non-expanding.
- **Fill propagation (spec/PRIMITIVES.md §stack layout model)**: implemented —
  `effective_fill` in `layout.rs` resolves a Hug-sized stack/ZStack that
  contains a `FILL` child / `Spacer` / greedy primitive on an axis as `FILL` on
  that axis (SwiftUI/Compose parity), so it fills its parent's proposal.
  Covered by `fill_propagates_through_a_hug_stack`.
- **ZStack (spec/PRIMITIVES.md)**: implemented — every child is an overlay
  (later = on top) that keeps its own size unless effectively `FILL`, positioned
  by the ZStack `ALIGNMENT` on **both** axes (`sync_overlay_children`); the
  overlay hugs to its largest child; fill propagation applies. The old
  first-child-pinned-to-fill `GtkOverlay` main-child behavior is gone.
- **CONTENT_MARGINS vs PADDING precedence**: implemented — `CONTENT_MARGINS` is
  folded into the margins by `apply_padding` as the **lowest-precedence base**
  (per-edge `PADDING_*` > `PADDING` > `CONTENT_MARGINS`, spec PRIMITIVES.md §stack
  layout model) and no longer applied in `apply_style` (where it previously
  overrode the higher-precedence `PADDING`). Matches the HTML renderer.
  Covered by `content_margins_are_the_lowest_precedence_base`.
- **SPACING** (spec/PRIMITIVES.md): applied on stacks (`GtkBox` spacing,
  re-set every frame), on **grids** (`row_spacing`/`column_spacing` from
  `SPACING`, `sync_grid_children`), and on **composite-control bodies**
  (`sync_composite_children` reads the control node's `SPACING` — a control
  whose label flattened a stack, e.g. `Button.of(HStack, …)`, carries the
  stack's `SPACING`, and its body box reproduces the gap like the HTML
  renderer's `gap`). Covered by `spacing_clamps_and_rounds_for_any_node`.
- **`CLIPS_TO_BOUNDS`**: implemented — applied in `apply_style` via
  `set_overflow(Overflow::Hidden)` (SwiftUI `.clipped()` parity, spec LAYOUT.md /
  PRIMITIVES.md: a Fixed box constrains layout but only clips when set).
- **Text layout (spec LAYOUT.md §content fitting)**: aligned — a Fixed `WIDTH`
  wraps the label within the box (`set_wrap` + `WordChar`); `LINE_LIMIT` clamps
  the line count (`set_lines`) and truncates with an ellipsis positioned by
  `TRUNCATION_MODE` (Tail default). `TRUNCATION_MODE` **alone** has no observable
  effect (`apply_text_style` no longer forces single-line). Renderer-owned
  fidelity: `ellipsize_from` maps Head/Middle/Tail → `EllipsizeMode::Start/
  Middle/End` — reliable for single-line labels, best-effort on a multi-line
  clamp.
- **Known GTK limitation**: a GTK Fixed box (`set_size_request`) is a **minimum**
  request — GTK has no max-size API, so a widget whose natural size exceeds its
  Fixed box (e.g. a stack wider than its box) is allocated its natural size
  rather than clipped to the box. Text wraps within its box (above) and
  `CLIPS_TO_BOUNDS` clips overflow; other content may still exceed a Fixed box.
- `SHAPE` `Path`/rounded rendering is an approximation (rectangle/circle fill).
- `MENU` renders a menu button without a popover item list.
- No `ACTION_ID`-only gating (events require `BINDING_ID`).
- Composite bodies attach to button-like controls only; other controls ignore
  children.
- `LAZY_*` renders eagerly (no GTK windowing).
- **Media**: a `VIDEO` node plays its audio through the shared GStreamer
  `playbin` machinery, but has no native video surface yet (a native `GtkVideo`
  widget additionally needs a GTK4 build with `media-gstreamer` enabled) — video
  rendering is a follow-up. Playback requires GStreamer with the mp3 decoding
  plugins installed (the brew `gstreamer` formula bundles them).
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