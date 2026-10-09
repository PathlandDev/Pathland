# pathland-view (Rust DSL) — implementation status

**Last updated:** October 9, 2026

The SwiftUI-style **view DSL** (`no_std`) building `pathland_engine::Node`
trees. Protocol contract: `spec/`, authoring surface: `spec/DSL.md`.

## Implemented

- **The three authoring operations** (spec/DSL.md §2): every concrete view
  exposes **values** (`Type::with(|c| …)` static / `view.with(|c| …)` chained),
  **modifiers** (`Type::modifiers((A, B))` / `view.modifiers((A, B))`, single or
  tuple list) and, for content-bearing views, **children**
  (`Type::children(vec![…])` / `view.children(vec![…])`). The three may be used
  at creation or chained, in any order.
- **Per-view `Config`** (`TextConfig`, `StackConfig`, `GridConfig`,
  `RangeConfig`, …) with fluent setters, consumed by the value builders. Values
  move structural properties (`spacing`/`alignment`/grid counts) into the config
  (no longer modifiers).
- **Views**: `VStack`, `HStack`, `ZStack`, `Grid`, `ScrollView`, `LazyVGrid`,
  `LazyHGrid`, `LazyVStack`, `LazyHStack`, `Text`, `Button`, `Spacer`,
  `Icon` (semantic symbols via `IconName` + `icon(…)`), `Image` (sets
  `IMAGE_SOURCE`), `Color` (dual identity: a layout-greedy View *and* a value
  passed into style modifiers), `Shape` + named `Rectangle`/`Circle`/`Capsule`/
  `Ellipse`, `Divider`, `ProgressView`, `Gauge`, `Toggle`, `Slider`,
  `TextField` (sets `PROMPT`), `TextEditor`, `Stepper`, `DatePicker`, `Picker`,
  `Menu`, `ColorPicker` (+ the `vstack!`/`hstack!` macros and
  `text`/`spacer`/`button`/`icon`/`image` free functions).
- **Modifiers** (decoupled values applied via `modifiers(...)`): `Padding`,
  `FontSize`, `FontWeight`, `Font(Font)`, `FontFamily`, `FontDesign` (the enum
  *is* the modifier), `ForegroundStyle` (no `.color()`), `Background`, `Border`,
  `Tint`, `Frame`, `Opacity`, `Hidden`, `CornerRadius`, `LineLimit`,
  `TextAlignment`, `TruncationMode`, `Offset`, `Position`, `ZIndex`,
  `PointerEvents`, `TapGesture` (`TapGesture::new(f)`).
- **No `Mod` suffix**: modifiers are named for the modifier they apply;
  `FontDesignMod` is gone (`FontDesign` is the modifier).
- **Typography** (`Font` + `TextStyle`): `Font::large_title()`…`caption2()` emit
  the predefined `TEXT_STYLE` (renderer-owned size/weight); `Font::custom(name,
  size)`, `Font::system(size)`, `Font::system_weight(size, weight)`, and
  `Font::custom_full(name, size, weight, design)` emit the raw
  `FONT_FAMILY`/`FONT_SIZE`/`FONT_WEIGHT`/`FONT_DESIGN` axes.
- **`Frame` infinity**: infinite `width`/`height` hints (`f32::INFINITY`,
  SwiftUI `maxWidth/maxHeight: .infinity`) normalize to the `size::FILL`
  sentinel on emission.
- **Composition**: blanket `ViewExt::modifiers` + `ViewModifier` for custom
  modifiers; `Modified<V>` wrapper (applies its modifier list at `build`).
  `Modified<V>` implements `Configurable`/`Children` (delegating) so the three
  operations are order-independent.
- **Alignment enum** (`Align`): Leading/Center/Trailing.
- **Design tokens**: `Color` is `Literal(u32)` | `Token(&'static str)` —
  `Color::token("color.primary")` (and `dark.*` paths) usable as a View and in
  every color-taking modifier; token refs emit as the `DESIGN_TOKEN` value type
  via `Node::token_properties` (spec/TOKENS.md).
- **`Theme` / `AdaptiveTheme` (global overrides)**: re-exports
  `pathland_engine::Theme` and `AdaptiveTheme { light, dark }`. Emit once at
  mount via `Engine::apply_theme` / `Engine::apply_adaptive_theme`.
- **Reactive signals (Phase 1)**: a shared `Runtime`
  (`Rc<RefCell<Store>>`, `no_std` — no thread-local) with typed handles
  (`Signal<T>`/`WritableSignal<T>`, `SignalValueKind`/`IntoSignalId`), value
  signals, **computed** (lazy, memoized, equality-suppressed, chained) and
  **effect** (runs immediately, then on dependency change) signals, and
  `untracked` reads; `Engine::signal`/`computed`/`effect`/`read`/`set`. The DSL
  binds a node's **text** (`Text::with(|t| { t.text_signal(sig); })`) and a
  **property** (`FontSize::bound(sig)`, `FontWeight::bound`,
  `ForegroundStyle::bound`, `Background::bound`, `Opacity::bound`, or generic
  `Bound::new(prop, sig)`) to a signal, so a change re-emits only that node's
  `SET_TEXT`/`SET_PROPERTY` (a computed's change re-emits its bound nodes too).
- **Two-way bindings + actions (Phase 2)**: a control binds a writable signal
  and records an app-side **input sink** (a `Gesture::TextInput`/`ValueInput`/
  `DateInput` on the node, never serialized) plus the `BINDING_ID` event gate —
  `TextField`/`TextEditor` (`t.text(sig)`), `Toggle` (`t.is_on(sig)`),
  `Slider`/`Stepper` (`s.bind(sig)`, initial from the signal), `Picker`
  (`p.bind(sig)`). `Button::action(f)` wires the tap. A host collects the sinks
  with `collect_input_handlers` and routes `TEXT_CHANGED`/`VALUE_CHANGED`/
  `DATE_CHANGED` into them (the sink writes the signal; the host re-emits the
  tree to flush the delta).
- **Styles + environment (Phase 3)**: an [`Environment`] threaded through
  `View::build_env` (the `no_std` equivalent of the Java thread-local) scopes a
  subtree; `ButtonStyle` (`make_body(&ButtonConfig) -> Option<Box<dyn View>>`)
  supplies a button's content (Composite Override Mode), applied with
  `ViewExt::button_style(style)`. Built-ins `PlainButtonStyle` (native path) and
  `BorderedButtonStyle`; custom styles implement the trait.
- **Structural reactivity + navigation (Phase 4)**: `Conditional::when(signal,
  then, else)` builds the selected branch (the host rebuilds + re-emits; the
  engine reconciles into `TREE` deltas). Navigation: `Params` + `RouteTable`
  (`/users/:id` matching + fallback), `Router` (path signal + back-stack:
  `navigate`/`push`/`pop`/`replace`), `NavigationContainer` (builds the current
  destination and carries `ROUTE`/`NAV_DEPTH`/`NAV_CHROME` on a container slot),
  and `NavigationLink` (a button that changes the route via a router).

## Not implemented / gaps

- **Renderer coverage**: the GTK renderer reports `VALUE_CHANGED`/`TEXT_CHANGED`
  for Toggle/Slider/TextField/Picker; Stepper/TextEditor/DatePicker/ColorPicker
  sink wiring lands in the DSL but the renderer side for some controls is still
  to come. Date/color two-way is not wired (needs `SET_DATE`).
- **Styles**: only `ButtonStyle` (there is no `Label`/`Audio`/`Video` view in the
  Rust DSL, so `LabelStyle`/`AudioStyle`/`VideoStyle` have no target). The
  environment carries only the button-style slot (no arbitrary typed keys).
- **Navigation**: links hold a router explicitly (no nearest-enclosing-router
  resolution / `NavigationIntent`); `NAVIGATE` event routing is app-side; no
  `Label`/`GridRow` components.
- **`SizeThatFits` (SIZE_THAT_FITS 0x17 / FIT_QUERY LIST / FIT_CHANGED)** is
  **authored** (`SizeThatFits::new(Vec<Fit>)` + `Fit::new(view, minWidth)` /
  `Fit::any`) emitting the slot + its single selected child + the `FIT_QUERY`
  list; **reactive selection is fixed** — only the Java DSL's structural slot
  reacts to `FIT_CHANGED`.
- No `ACTION_ID`/`BINDING_ID` helpers; `Toggle`/`Picker`/`Menu` lack typed
  value APIs (raw tokens only).

## Verified by

`cargo test -p pathland-view` — the three operations (static + chained, any
order), per-view config, tuple/single modifier lists, custom modifiers,
`assign_ids`, tap-gesture listener wiring, token refs, grid counts, named
shapes, `SizeThatFits`. Workspace `cargo test` green; `lib/rust/check-wasm.sh`
(`no_std` preserved).
