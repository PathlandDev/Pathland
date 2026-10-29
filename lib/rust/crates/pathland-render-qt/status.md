# pathland-render-qt — implementation status

**Last updated:** October 27, 2026

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
  needs Qt6 dev libs (Homebrew: `qtbase qtdeclarative qtmultimedia`).

## Implemented

- **Window metadata**: a changed `TITLE` is forwarded to the Qt layer as `PLQT_SET_WINDOW_TITLE`; unchanged metadata emits zero commands.

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
  `VSTACK`/`HSTACK`/lazy stacks → a **wrapper `Item` holding an inner
  `ColumnLayout`/`RowLayout`** (`QtQuick.Layouts`; the wrapper's `plPad` drives
  padding + implicit size, so a stack hugs its content), `GRID`+lazy grids →
  `Grid`, `ZSTACK` → `Item` (children share one box, positioned by the 2D
  `ALIGNMENT` code on both axes, re-derived on resize; the Hug stack's own size
  is the **max child** per axis, and an "effectively FILL" child — explicit
  `WIDTH`/`HEIGHT=FILL`, a greedy primitive, or fill propagation through a
  nested stack/Spacers — stretches to the box), `SCROLLVIEW` → `ScrollView`
  (content attaches to its `contentItem`; **greedy on its `SCROLL_AXIS`**
  (vertical by default), the **cross axis hugs its content** and fill-propagates
  (a `FILL`-width content child makes the viewport full-width); content is
  **unbounded on the scroll axis** (keeps its natural size and may grow beyond
  the viewport) and **bounded to the viewport on the cross axis**; an explicit
  `WIDTH`/`HEIGHT` overrides greed and `HUG_CONTENT` sizes the viewport to the
  content), `TEXT` → `Text`, `SPACER`/
  unknown → `Item`, `DIVIDER` → `Rectangle`, `COLOR` → `Rectangle`, `SHAPE` →
  `Rectangle` with `SHAPE_KIND` geometry (Circle/Capsule/Ellipse round to
  `min(w,h)/2` — Ellipse is a uniform-radius approximation; Rectangle is
  square-cornered; RoundedRectangle keeps `BORDER_RADIUS`; `Path` unsupported),
  `IMAGE` → `Image`, `BUTTON` → `Button`, `TEXT_FIELD` → `TextField`, `SLIDER`
  → `Slider`, `TOGGLE` → `Switch` (controls embed `onX: bridge.<event>(id, …)`
  handlers), `PROGRESS_VIEW`/`GAUGE` → `ProgressBar` (fraction via `PROGRESS`,
  activity via `IS_INDETERMINATE`), `STEPPER` → `SpinBox`, `PICKER` → `ComboBox`
  (option children populate the model ordered by insertion; `SELECTION` →
  `currentIndex`; `onActivated` → `VALUE_CHANGED`), `MENU` → `Button` trigger
  (Qt 6 has no MenuButton; a popup Menu is a documented gap), `DATE_PICKER` /
  `COLOR_PICKER` → `TextField`/`Button` approximations (Qt Quick Controls has no
  native date/color pickers). Property application via generic `setProperty`
  (Qt ignores unknown props): spacing/`align`, **padding** (`PADDING_*` > `PADDING`
  > `CONTENT_MARGINS`, order-independent — stacks get real inner padding whose
  per-edge values also feed the hugged implicit size; non-stack leaves get
  margins — `Layout.*Margin` inside a layout, `anchors.*Margin` for roots/anchored
  leaves. Gap: a leaf positioned by ZStack alignment (no anchors) isn't inset),
  width/height (`-1`
  FILL → **`Layout.fillWidth`/`fillHeight` in a layout, anchors.fill elsewhere**,
  `-2` HUG → implicit), color, **font size/weight/family/style via the `font`
  `QFont` property** (dotted-path `setProperty` silently fails), opacity, visible,
  z, value/min/max/step, selected→checked, enabled,
  `IS_SECURE` → `echoMode` = `TextInput.Password` (masked), progress/indeterminate,
  selection→currentIndex, prompt/label→placeholder, image source.
  **`OFFSET_X`/`OFFSET_Y`** are a post-layout QML `Translate` transform (composes
  with layout/alignment instead of being overwritten). **`ALLOWS_HIT_TESTING`**
  disables the item's mouse acceptance and any attached pointer MouseArea without
  hiding it. **Stack alignment**: a stack's `ALIGNMENT` positions each child on
  the cross axis via
  its `Layout` attached properties (Start/Center/End; `Fill`(3)/absent = default
  HUG/start, never a stretch — spec/LAYOUT.md).
  **Roots are anchored to fill the window** (GTK hexpand/vexpand parity).
  `RESET_NODE` resets renderer-owned style defaults (value props untouched so a
  slider/toggle doesn't lose position on echo).
- **Diff ordering** (`src/renderer.rs`): `QtRenderer::diff` emits child inserts
  *before* property deltas, so `WIDTH`/`HEIGHT`/`ALIGNMENT` land after a widget is
  attached to its final parent — FILL resolves against the real parent instead of
  the create-time contentItem placeholder.
- **Events** (spec/EVENTS.md guards): the `Bridge` reports control inputs
  (`tap` → `POINTER_UP`, `valueChanged` → `VALUE_CHANGED`, `textChanged` →
  `TEXT_CHANGED`) to Rust, which encodes the `Event`, writes it into the event
  ring, and wakes the host (renderer never drains). Interactions are gated by
  `BINDING_ID`/`ACTION_ID` and programmatic value sets suppress echoes.
- **Pointer event streams**: an `EVENT_LISTENERS`-gated QML `MouseArea` overlay
  is attached to any node carrying pointer listener bits (down/move/up), and
  its handlers report `POINTER_DOWN`/`POINTER_MOVE`/`POINTER_UP` through the
  Bridge (bypassing the `BINDING_ID` gate — any element can emit raw inputs,
  spec/EVENTS.md). Attached/detached on listener-mask changes / `RESET` /
  `DELETE`.
- **Native back / `NAVIGATE`**: a window-level `KeyFilter` maps Escape to
  `Event::Navigate { url: None }` (global, never node-keyed), matching the GTK
  renderer. BackSpace is deferred (needs text-focus detection).
- **Color-scheme detection (spec/TOKENS.md)**: `QStyleHints::colorSchemeChanged`
  fires a scheme event to Rust, which calls `QtRenderer::set_scheme` — the
  shared core re-resolves design tokens and the diff re-emits the changed
  concrete values (`SET_PROPERTY`) with no host wake. Initial scheme applied at
  init.
- **Design tokens / theming**: `PARAMETER::SET_DESIGN_TOKEN` overrides and
  `DESIGN_TOKEN` property references are decoded by the shared
  `pathland-render-core` tree and diffed to QML like any other property;
  `META::SET_COLOR_SCHEME` forces platform/light/dark (the platform signal is
  stored but ignored while forced) and `META::RESET_THEME` clears app
  overrides. Explicit `dark.` references and unknown-token fallback follow the
  shared resolver. Qt-specific defaults are the shared concrete Tier-1 tables
  (no `QPalette` enrichment yet).
- **`META::RESET`**: both pump paths scan each frame for `META::RESET` and call
  `QtRenderer::reset()` (clears the decoded tree, sent-state cache, and the
  C++ scene incl. listener overlays).
- **Qt shell**: init/run split (headless tests drive `init` + `apply` without
  an event loop); `pathland_qt_layer_run` owns the window + a zero-millisecond
  idle `QTimer` that ticks Rust's pump (Qt analogue of GTK's idle pump).
- **Asset root** (`pathland_qt_set_asset_root` /
  `pathland_qt_layer_set_asset_root`): web-style `/_pathland/...` image/media
  sources resolve against a host-extracted local directory (the Qt analogue of
  `pathland_gtk_set_asset_root`); the Java Qt host extracts `DemoAssets` and
  sets it before `runRing`.
- **C ABI for foreign hosts** (`src/capi.rs`): `pathland_qt_run(host,
  on_event)` (over a `pathland-view-native` `NativeHost`) and
  `pathland_qt_run_ring(ring, on_event, width, height)` (over a borrowed
  `libpathland_core` ring), both mirroring the GTK renderer's contract. Passes
  a bare program name to Qt.
- **Rust demo ergonomics** (`run`): `pathland_qt::run(app_id, title,
  Rc<RefCell<RingTransport>>, on_event)` mirrors `pathland_render_gtk::run`;
  the pump borrow is held across frame application (zero-copy batch view of the
  shared ring).

- **Navigation adapter (spec DSL.md §4.5)**: a VSTACK/HSTACK slot carrying the
  `ROUTE` property promotes to the renderer's nav container and reconciles a
  page stack **by depth** (`NAV_DEPTH`): pop down to the app's depth, then
  `Refresh` (same route), `Push` (deeper + new route), or `Replace` (same
  depth, new route) — mirroring the GTK `AdwNavigationView` adapter. `PlatformDefault`
  chrome wraps each page in a header with a native back button (`→`
  `navigateBack` → `Event::Navigate { url: None }`); `NAV_CHROME=1` (`Custom`)
  pushes the bare destination. The back-stack stays app-owned; the page stack
  is the renderer's rendered-output cache. **Implementation note:** Qt Quick
  has no native navigation container and `StackView`'s `push`/`pop` are
  QML-callable-only (not invokable from C++ via `QMetaObject`), so the nav
  container is a plain `Item` whose pages the renderer manages (only the top
  visible) — a renderer-owned page cache, same statelessness contract.
  **Page sizing:** each page wrapper is anchored to fill the nav container
  (`placeDestination`), so the header/content/destination chain is sized and the
  destination subtree fills the content area (previously the page wrapper was
  attached unsized, collapsing the content to 0×0 and showing only an
  overflowing title).

- **Post-merge protocol parity (October 2026)**: the crate was merged onto the
  shared `PARAMETER` rename and now maps the protocol surface added on `main`:
  - `ICON` (0x0B) → a `Text` item whose glyph is resolved from `ICON_NAME` by a
    renderer-owned canonical-name map (unknown names fall back to a dot), with
    `COLOR`/`FONT_SIZE` reuse (spec/ICONS.md).
  - `MEDIA_CONTROLLER` (0x30) → **non-visual** (layout-transparent: created but
    never attached) hosting a QML `MediaPlayer` + `AudioOutput`
    (spec/MEDIA.md).
  - `VIDEO` (0x0A) → a `VideoOutput` surface; `MEDIA_CONTROLLER_ID` binds its
    `source` to the referenced controller's `MediaPlayer` (a reference, not a
    parent/child edge).
  - `PROXY_VIEW` (0x1E) → a transparent vertical container.
  - `SIZE_THAT_FITS` (0x17) → a greedy two-axis slot; its one selected child
    fills it.
  - `GRID_ROW` (0x1D) → a row-grouping container whose cells flatten into
    the parent Grid.
- **Shared decode parity** (`pathland-render-core`): `LIST`-typed properties
  (`FIT_QUERY`) decode into `HostNode::lists`, and `PARAMETER::SET_DATE` into
  `HostNode::date` — so the Qt layer can measure fit slots and format dates.
- **`SIZE_THAT_FITS` fit measurement**: the slot's `FIT_QUERY` thresholds reach
  the C++ layer as CSV; a width change derives the fit index (largest
  `minWidth` ≤ allocated width; equal thresholds keep the lower index; empty
  table → 0) and reports `FIT_CHANGED` once after the first measure, then only
  when the index changes. The report is a layout result, not a value echo, so
  it is not suppressed while a frame's properties are applied. The slot shows
  exactly one selected child, which fills the slot.
- **`GRID_ROW` flattening**: a row's cells attach directly to the parent Grid
  (row-major), so columns align across rows; the row item takes no cell.
- **`DATE_PICKER`**: `SET_DATE` (days since epoch + millis of day) renders as an
  ISO date (with time when nonzero) in a QML text field — a QML approximation
  (Qt Quick has no native date picker).
- **Events** (spec/EVENTS.md): pointer `hover`/`leave` (`POINTER_MOVE`
  hovering/leaving) from the listener `MouseArea`; `KEY_DOWN`/`KEY_UP` for the
  focused text input (component-type/key-listener gated); `FOCUS_CHANGED` /
  `EDITING_CHANGED` / `SUBMIT` for text inputs (gated by the matching
  `EVENT_LISTENERS` bits); BackSpace is native back unless a text input is
  focused (focus-aware, no longer deferred).
- **Visual styling layer** (parity with the web/GTK demos):
  - `BACKGROUND_COLOR` / `BORDER_*` / `BORDER_RADIUS` render on a background
    `Rectangle` behind a container's content (stacks reuse an injected
    `pl<id>bg`; plain containers create one lazily); `Rectangle`-backed nodes
    set them directly. `RESET_NODE` clears them.
  - **Composite control mode** (spec/PRIMITIVES.md §2): a `Button` that gains
    children is promoted from the native widget to a bare `Item`+`ColumnLayout`
    shell that renders the app's `ButtonStyle` body, so `PlainButtonStyle` /
    `BorderedButtonStyle` buttons look like the web/GTK ones. Leaf buttons stay
    native.
  - **Foreground inheritance** (CSS `color`): a container's `COLOR` is inherited
    by descendant `Text`/`Icon` that don't set their own.
  - **Layout defaults (spec/LAYOUT.md)**: the cross axis **hugs** by default
    (never CSS `align-items:stretch`); only an *effectively FILL* child stretches
    — explicit `WIDTH`/`HEIGHT=FILL`, a layout-greedy primitive (`SPACER` main,
    `DIVIDER` cross, `COLOR`/`SIZE_THAT_FITS` both, `SCROLLVIEW` its
    `SCROLL_AXIS` only), or **fill
    propagation** (a Hug stack/ZStack/proxy whose subtree carries a FILL
    child/greedy filler). An explicit fixed size **or `HUG_CONTENT`**
    (`PL_SIZE_HUG`) overrides a primitive's natural greed. `ALIGNMENT` only
    positions (`Fill(3)` = default hug).
    Recomputed at each frame boundary (`recomputeLayout`), so `RESET_NODE`'s
    style reset can't wipe it. Plus `CONTENT_MARGINS`.
  - **`TEXT_STYLE`** applies a base typography (size + weight) that raw
    `FONT_SIZE`/`FONT_WEIGHT` override; **`SHADOW_*`** draws a best-effort
    offset shadow layer.
- **Media transport** (spec/MEDIA.md): `MEDIA_SOURCE`, `PLAYBACK_STATE`
  (play/pause), `MEDIA_POSITION` (seek, seconds→ms), `MEDIA_VOLUME` /
  `MEDIA_MUTED` (via `AudioOutput`), `MEDIA_LOOP`, `MEDIA_RATE`, and the
  `MEDIA_PLAY_STATE_CHANGED` / `MEDIA_TIME_UPDATED` / `MEDIA_ENDED` /
  `MEDIA_VOLUME_CHANGED` / `MEDIA_DURATION_CHANGED` reports (reported by the
  controller's component type, not gated by `BINDING_ID`; programmatic sets are
  echo-suppressed). `MEDIA_PLAY_STATE_CHANGED` is reported **only on a media
  error** (`InvalidMedia`), never for app-driven play/pause or the natural end —
  matching the DOM client (reports play/pause only with native controls) and GTK
  (error only). There are no renderer-native audio controls, so a play-state echo
  would otherwise write the app's `PLAYBACK_STATE` command to pause and stop the
  next track (advanced on `MEDIA_ENDED`) from playing. A play/pause declared
  before the source is re-applied when the source lands.
  **Idempotent transport**: `MEDIA_SOURCE` reloads only when the resolved
  resource changes and `MEDIA_POSITION` seeks only when the command target
  changes, so the diff's full-property re-send (e.g. a volume drag on the
  controller) never restarts the stream or jumps to a stale position — mirrors
  `pathland-render-gtk`.
- **Layout/property parity**: `OFFSET_X/Y`, `POSITION_X/Y`, `MIN/IDEAL/MAX_*`,
  `CONTENT_MODE` (image fit/fill), `CLIPS_TO_BOUNDS`, `BORDER_COLOR` /
  `BORDER_WIDTH` / `BORDER_RADIUS`, `TEXT_ALIGNMENT`, `LINE_LIMIT`,
  `TRUNCATION_MODE`, and `GRID_COLUMNS` (via the `Layout` attached properties
  where applicable).
- **Headless test hang fixed**: Qt adds the executable's directory to QML's
  implicit import path; a `cargo test` binary lives in a huge `target/debug/deps`
  and plugin resolution scanned it for minutes. The shell now drops the
  application directory from the engine's import path at init, so
  `cargo test -p pathland-render-qt` completes in ~2s (no copy-to-temp workaround).

## Not implemented / gaps

- **`MENU` popup item list**: Qt 6 has no `MenuButton`; the trigger is a
  `Button` and the action-item popup is not built (documented gap).
- **`COLOR_PICKER`** remains a `Button` approximation (a QML color-swatch popup
  is a follow-up); `DATE_PICKER` is an ISO-text QML approximation, not a native
  dialog.
- **`PICKER` option styling** and `SHAPE` path approximation. Native **leaf**
  controls keep Qt Quick Controls' own chrome (background/border); the styling
  layer covers containers, composite controls, and `Rectangle`-backed nodes.
  Per-component design tokens (`button.*`, `toggle.*`, `slider.*`, …) are
  therefore not applied to native leaf chrome automatically — an app can still
  emit explicit token-referenced properties.
- **Qt-native token defaults**: no `QPalette` → Tier-1 default enrichment yet;
  the renderer uses the shared concrete fallback table.
- **Icons** are renderer-owned glyphs (the canonical `ICON_NAME` → a Unicode
  symbol), not the web's SVG/Lucide set — recognizable but not pixel-identical.
- **Shadows** are an offset rectangle approximation, not a blurred
  `MultiEffect` shadow.
- **Keys/events beyond the focused text input**: keys are reported for a focused
  text input; keys on other focused controls, `SCROLL`/`WHEEL`, and
  `DATE_CHANGED` are not yet reported. Hover/leave use `POINTER_MOVE` (the
  listener mask bit).
- **FILL inside plain positioners**: `GRID` (a `QQuickGrid`) rejects
  `anchors.fill` on its children, so a `WIDTH`/`HEIGHT` `FILL` inside a Grid
  defers and is skipped (the positioner sizes it). Stacks use `RowLayout`/
  `ColumnLayout` so FILL children stretch properly.
- The Rust DSL does not yet expose signal property bindings (the Java DSL
  does), so the demo marks the slider bound via a `BINDING_ID` on the retained
  tree; a real `.value(Signal)` DSL binding is a DSL/engine feature.
- The Rust DSL has no `NavigationContainer` (the spec table is aspirational) —
  the renderer consumes ROUTE/NAV_DEPTH (exercised by the nav adapter tests).
- `TRANSITION`-hint animation, back-swipe gestures, multi-step deep-link
  intermediate pages (same gaps as the GTK renderer).
- **Java JNA demo** (`lib/java/pathland-qt-demo`) runs the shared
  `MusicPlayerView` (app-driven media) via `pathland_qt_run_ring`.
- Lazy views render eagerly.

## Verified by

`cargo test -p pathland-render-qt` (needs Qt6 dev libs; offscreen for the FFI
tests) — the delta diff suite, the end-to-end FFI batch
(`command_batch_crosses_into_cpp_offscreen`), the **full pipeline**
(`renderer_drives_live_qml_scene`: DSL → engine → frame → shared decode →
delta diff → FFI → live QML `Column`/`Text`/`Button`/`Slider` with spacing/value
applied and a delta re-applied), **explicit sizing + cross-axis hug**
(`explicit_width_inside_stack_and_cross_axis_hugs`: FILL → `Layout.fillWidth`,
fixed → `Layout.preferredWidth`, and `ALIGNMENT=Fill(3)` does **not** stretch the
cross axis; `default_cross_axis_hugs_and_spacer_expands`/`explicit_fill_stretches_cross_axis`/
`greedy_primitives_fill_by_nature`: hug default, explicit cross FILL, and the
greedy `DIVIDER`/`COLOR`/`SPACER` axes; `composite_button_body_propagates_fill`:
a FILL-width label makes a composite menu row stretch;
`fill_propagation_stretches_sidebar`:
a Spacer-bearing VStack sidebar stretches full height through the real
`RESET_NODE` diff path and the content sits to its right), **stack geometry**
(`vstack_lays_out_children_vertically`
and `hstack_lays_out_children_horizontally`: a `render_once` layout pass then
asserts children stack on the main axis, are cross-axis aligned, and the
`SPACING` gap lands between them; `vstack_hugs_its_content` / `hstack_hugs_its_content`
assert the own-size hug (main = sum + spacing + padding, cross = max + padding),
`vstack_cross_axis_alignment_positions_children` /
`hstack_cross_axis_alignment_positions_children` assert leading/center/trailing
(and top/center/bottom) cross-axis positioning without resizing, and
`stack_main_axis_fill_shares_leftover_space` /
`stack_main_axis_fill_after_fixed_child` assert main-axis FILL distribution;
`zstack_overlaps_children_in_depth` asserts
ZStack children share one box at the top-leading origin with their own size,
`zstack_alignment_positions_children_on_both_axes` asserts the 2D `ALIGNMENT`
codes 0..8 position children on both axes without resizing,
`zstack_hugs_its_largest_child` asserts own size = max child per axis,
`zstack_fixed_size_makes_an_exact_box_and_does_not_clip` asserts a Fixed box is
exact and overflows without clipping, `zstack_fill_propagation_fills_a_greedy_child`
and `zstack_fill_propagation_stretches_the_child` assert fill propagation
(`ZStack { Color }` fills; a `Spacer`+content `HStack` stretches full width), and
`zstack_fill_child_stretches_and_hug_child_keeps_own_size` asserts only a Fill
child stretches, and
`zstack_draw_order_is_child_index_with_z_index_override` asserts child-index
paint order plus the `Z_INDEX` override), pointer-listener MouseArea attachment
(`pointer_listeners_attach_mousearea`), the event path
(`decode_maps_pointer_and_global_events`, `ring_event_writes_pointer_into_ring_and_wakes`,
`scheme_event_reapplies_without_waking`, `text_event_string_is_length_prefixed`,
`frame_has_reset_detects_meta_reset`), and Phase-B control coverage
(`control_widgets_construct_and_apply_props`, `picker_builds_model_from_option_children`,
`text_field_prompt_secure_and_value`, `shape_kind_sets_the_geometry`),
ScrollView coverage (`scroll_view_fills_and_holds_content`,
`scroll_view_fills_only_its_scroll_axis`, `scroll_view_fill_content_expands_cross_axis`,
`scroll_view_horizontal_axis_is_greedy_on_horizontal`,
`scroll_view_fixed_size_overrides_greed`, `scroll_view_cross_axis_alignment`,
`scroll_view_content_is_measured_unbounded`, `scroll_view_hug_content_sizes_to_content`),
and the nav adapter (`nav_slot_promotes_and_pushes_root_page`,
`nav_depth_push_and_pop`, `nav_replace_same_depth_new_route`,
`nav_custom_chrome_bare_pages`), plus post-merge parity
(`new_components_and_media_binding`: ICON glyph resolution, a layout-transparent
MEDIA_CONTROLLER, and a VIDEO surface bound to its controller;
`media_controller_audio_transport`: an audio-only controller's source/volume/
mute/loop/rate land on the MediaPlayer/AudioOutput), the demo's transport glyphs
(`icon_glyphs_resolve_for_the_music_controls`), the axis divider
(`divider_is_an_axis_line_with_color_and_thickness`), media event
decoding (`decode_maps_media_reports`), the shared decode parity
(`decodes_list_and_date_values` in `pathland-render-core`; `list_property_is_forwarded_as_csv`,
`date_change_is_forwarded_once`), `SIZE_THAT_FITS`
(`size_that_fits_derives_index_from_width`, `fit_slot_shows_only_the_selected_child`,
`fit_slot_swaps_the_selected_child`,
`fit_changed_reports_once_then_only_on_band_crossing`), `GRID_ROW`
(`grid_row_flattens_into_the_grid`), `DATE_PICKER`
(`date_picker_formats_set_date`), and the event family
(`decode_maps_hover_key_and_focus`), and the visual styling layer
(`container_background_border_and_radius`, `button_with_child_renders_composite`,
`button_with_icon_child_renders_composite`,
`foreground_color_inherits_to_text`, `default_cross_axis_hugs_and_spacer_expands`,
`text_style_and_shadow`, `text_line_limit_clamps_line_count`: `LINE_LIMIT` →
`maximumLineCount` and a sibling with the same width but no limit wraps past
it; `font_style_italic_toggles_the_font` (`FONT_STYLE` → italic);
`allows_hit_testing_gates_input` (`ALLOWS_HIT_TESTING` gates input);
`padding_insets_the_stack_content` (uniform + per-edge inner padding),
`stack_hugs_including_per_edge_padding` (per-edge padding in the hug size),
`leaf_padding_insets_a_root_view` / `leaf_padding_insets_within_a_stack`
(padding on non-stack leaf views — anchors / Layout margins);
`offset_translates_without_affecting_layout` (`OFFSET_*` post-layout);
`clips_to_bounds_sets_the_item_clip` (`CLIPS_TO_BOUNDS` → `clip`);
`image_sets_source_and_content_mode` and
`image_source_resolves_against_asset_root`: `IMAGE` source + `CONTENT_MODE`
(`PreserveAspectFit`/`Crop`) and web-style `/_pathland/...` asset-root
resolution). `cargo test -p pathland-render-qt` now
completes directly (the import-path fix). Full workspace: `cd lib/rust && cargo test`.
Java JNA demo: `scripts/run-java-qt-demo.sh` (headless `RingEventReaderTest`
runs in the Maven reactor; the live host mounts `MusicPlayerView`).
Demo: `scripts/run-rust-qt-demo.sh`.