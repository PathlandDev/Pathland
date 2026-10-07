# pathland-view (Rust DSL) — implementation status

**Last updated:** September 2, 2026

The SwiftUI-style **view DSL** (`no_std`) building `pathland_engine::Node`
trees. Protocol contract: `spec/`.

## Implemented

- **Views**: `VStack`, `HStack`, `ZStack`, `Grid`, `ScrollView`, `LazyVGrid`,
  `LazyHGrid`, `LazyVStack`, `LazyHStack`, `Text`, `Button`, `Spacer`,
  `Icon` (semantic symbols via `IconName` + `icon(…)`), `Image`, `Color` (dual identity: a layout-greedy View *and* a value passed
  into style modifiers), `Shape`, `Divider`, `ProgressView`, `Gauge`,
  `Toggle`, `Slider`, `TextField`, `TextEditor`, `Stepper`, `DatePicker`,
  `Picker`, `Menu`, `ColorPicker` (+ the `vstack!`/`hstack!` macros and
  `text`/`spacer`/`button` free functions).
- **Modifiers** (chainable, decoupled): `spacing`, `padding`, `font_size`,
  `font_weight`, `font(Font)`, `font_family(&str)`, `font_design(FontDesign)`,
  `foreground_style(Color)` (no `.color()`), `background(Color)`,
  `border(Color, width)`, `tint(Color)`, `frame(width/height/alignment)`,
  `opacity`, `hidden`, `corner_radius`, `line_limit`, `text_alignment`,
  `truncation_mode`, `offset`, `position`, `z_index`, `pointer_events`,
  `on_tap_gesture`.
- **Typography** (`Font` + `TextStyle`): `Font::large_title()`…`caption2()` emit
  the predefined `TEXT_STYLE` (renderer-owned size/weight); `Font::custom(name,
  size)`, `Font::system(size)`, `Font::system_weight(size, weight)`, and
  `Font::custom_full(name, size, weight, design)` emit the raw
  `FONT_FAMILY`/`FONT_SIZE`/`FONT_WEIGHT`/`FONT_DESIGN` axes. `FontFamily` is a
  `STRING` property via `Node::string_properties`.
- **`frame` infinity sugar**: infinite `width`/`height` hints (`f32::INFINITY`,
  SwiftUI `maxWidth/maxHeight: .infinity`) normalize to the `size::FILL`
  sentinel on emission.
- **Composition**: blanket `ViewExt::modifier` + `ViewModifier` for custom
  modifiers; `Modified<V, M>` wrapper.
- **Alignment enum** (`Align`): Leading/Center/Trailing/Fill.
- **`Color` rules**: no `.color()` modifier; foreground is `.foreground_style(_:)`.
- **Design tokens**: `Color` is `Literal(u32)` | `Token(&'static str)` —
  `Color::token("color.primary")` (and `dark.*` paths) usable as a View and in
  every color-taking modifier (`foreground_style`, `background`, `border`,
  `tint`); token refs emit as the `DESIGN_TOKEN` value type via
  `Node::token_properties` (spec/TOKENS.md).
- **`Theme` / `AdaptiveTheme` (global overrides)**: re-exports
  `pathland_engine::Theme` (single-value builders `color`/`f32`/`string`/… for
  one scheme) and `AdaptiveTheme { light, dark }`. Emit once at mount via
  `Engine::apply_theme` (light) / `Engine::apply_adaptive_theme` (light + the
  `dark.`-prefixed dark theme; STRING values arena-alloc'd).

## Not implemented / gaps

- **`SizeThatFits` (SIZE_THAT_FITS 0x17 / FIT_QUERY LIST / FIT_CHANGED) is not yet
  authored** — the retained `Component` variant + LIST property emission + a static
  `SizeThatFits`/`Fit` surface (fit index fixed) is the next step; the Java DSL
  parity surface is implemented.
- `TextField`'s `PROMPT` and `Color`/`Image` sources are stored as placeholder
  values only (STRING props are supported via `Node::string_properties`, but
  these builders don't set them yet).
- No `ACTION_ID`/`BINDING_ID` helpers, no `TOGGLE_STYLE`-typed API (raw u8).

## Verified by

`cargo test -p pathland-view` — builder, modifier chaining, `assign_ids`,
tap-gesture listener wiring, new-component building.