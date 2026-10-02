# pathland-engine — implementation status

**Last updated:** September 2, 2026

The retained-tree **emitter**: the application's canonical UI tree and the
diff-based reactive emission into `TREE`/`PARAMETER` opcodes. Protocol contract:
`spec/`.

## Implemented

- **Retained tree** (`node.rs`): `Node` (id, component, children, properties,
  `token_properties`, text/property signal bindings, app-side gestures).
- **Components** (`Component` enum): the full spec set — `VStack`, `HStack`,
  `ZStack`, `Text`, `Button`, `Spacer`, `Image`, `Color`, `Shape`, `Divider`,
  `ProgressView`, `Gauge`, `Grid`, `ScrollView`, `LazyVGrid`, `LazyHGrid`,
  `LazyVStack`, `LazyHStack`, `Toggle`, `Slider`, `TextField`, `TextEditor`,
  `Stepper`, `DatePicker`, `Picker`, `Menu`, `ColorPicker` — all mapped to
  `component_type` ids via `component_type_id`.
- **Diff emitter** (`engine.rs`): create/delete/insert/move deltas,
  property/text deltas, steady-state zero emission.
- **Design-token property refs**: `Node::token_properties` (property → token
  path) emit as `SET_PROPERTY` with the `DESIGN_TOKEN` value type; unchanged
  refs reuse their arena offset across passes — **steady-state emission stays
  zero-alloc with token refs present** (proven by test).
- **`Engine::set_design_token`** — emits a global `PARAMETER::SET_DESIGN_TOKEN`
  override (base or `dark.`-prefixed) into the current frame.
- **Global theme overrides** (`theme.rs`): `Theme` — single-value builders
  (`color`/`f32`/`u32`/`u8`/`string`) for one scheme; `AdaptiveTheme { light,
  dark }` composes a light + dark pair. `Engine::apply_theme` (light-only) and
  `Engine::apply_adaptive_theme` (light as base, dark with the `dark.` prefix;
  STRING values arena-alloc'd) emit the overrides once at mount — they never
  re-emit nodes.
- **Reactive signals** (`signal.rs`): writable signals bound to node
  text/properties; `set_signal` re-emits only the bound nodes
  (`SET_TEXT`/`SET_PROPERTY`).
- **Gestures**: `Tap` (app-side callback, never serialized).
- **`assign_ids`** (pre-order stable ids).

## Not implemented / gaps

- Only `Tap` gesture; no computed-signal effects in the engine (Java has them).
- No `strings` map on `Node` for STRING-valued properties (`LABEL`/`PROMPT`/
  `FONT_FAMILY`/`IMAGE_SOURCE`) — the DSL cannot yet emit them.
- Signal-bound properties cannot carry token refs (a token ref is a static path).

## Verified by

`cargo test -p pathland-engine` — emitter deltas, signals, `component_type_id`.