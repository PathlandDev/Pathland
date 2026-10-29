# pathland-engine — implementation status

**Last updated:** October 27, 2026

The retained-tree **emitter**: the application's canonical UI tree and the
diff-based reactive emission into `TREE`/`PARAMETER` opcodes. Protocol contract:
`spec/`.

## Implemented

- **Window metadata bindings**: `Engine::bind_window_metadata`/`bind_window_field` bind fields to signals; mount emits the initial `META::SET_WINDOW_METADATA` fields, a signal write emits only the changed field, and unchanged values emit zero opcodes.

- **Retained tree** (`node.rs`): `Node` (id, component, children, properties,
  `string_properties`, `token_properties`, text/property signal bindings,
  app-side gestures). Components include `Icon` (→ `component_type::ICON`).
- **String-valued properties**: `Node::string_properties` (property → value,
  e.g. `FONT_FAMILY`, `LABEL`, `IMAGE_SOURCE`) emit as `SET_PROPERTY` with the
  `STRING` value type (arena-allocated); unchanged strings reuse their arena
  offset — steady-state emission stays zero-alloc with string props present
  (proven by test).
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
- **Runtime theming** (`Engine::reset_theme`, `Engine::set_color_scheme`,
  `Engine::replace_theme`, `Engine::replace_adaptive_theme`): emits
  `META::RESET_THEME` / `META::SET_COLOR_SCHEME`; `ColorSchemeMode`
  (`Platform`/`Light`/`Dark`) mirrors the wire modes. Replacement is
  reset + apply in one frame; no tree node is re-emitted.
- **Reactive runtime** (`signal.rs`): a shared `Runtime`
  (`Rc<RefCell<Store>>`, `no_std` — no thread-local; the "current computation"
  is an explicit stack in the store) owns value signals, **computed** signals
  (lazy, memoized, equality-suppressed, chained) and **effects** (run
  immediately, then on dependency change) plus `untracked` reads. Typed handles
  `Signal<T>`/`WritableSignal<T>` (`SignalValueKind`/`IntoSignalId`);
  `Engine::signal`/`computed`/`effect`/`read`/`set`. Node text/properties bound
  to a signal re-emit only the bound node on change — including a computed's
  bound nodes (`set_signal` re-emits every changed cell's node deps).
- **Signals bound to node text/properties**: `set_signal` re-emits only the
  bound nodes (`SET_TEXT`/`SET_PROPERTY`); `get_signal` returns the current value.
- **Gestures / input sinks**: `Gesture::Tap` (app-side callback) plus the
  two-way binding sinks `Gesture::ValueInput`/`TextInput`/`DateInput` (with
  `TapHandler`/`ValueInputHandler`/`TextInputHandler`/`DateInputHandler` aliases).
  `collect_tap_handlers` and `collect_input_handlers` (→ `InputHandlers`) expose
  the app-side registries; sinks are never serialized.
- **Media controller** (`media.rs`, spec/MEDIA.md): `MediaController` — the
  app-side state object owning a resource (`source`/`bind_source`) and its
  transport as signals: commands `playing`/`position`/`volume`/`muted`/
  `looping`/`rate` (each rebindable to an app-owned signal), report feeds
  `time`/`duration`/`volume_report`, `on_ended`, and the convenience transport
  (`play`/`pause`/`toggle`/`seek`/`set_volume`/`set_muted`/`set_looping`/
  `set_rate`). A node references one through the identity-compared
  `MediaControllerRef` (`Node.media_controller`); `Component::MediaController`
  (0x30, non-visual) is the controller's node and `Component::Video` is its
  surface. **Audio has no view.**
- **String property bindings** (`Node.string_bindings`): a `STRING`-valued
  property bound to a signal (e.g. a controller's `MEDIA_SOURCE`) re-emits only
  that property (`SET_PROPERTY`, `STRING`, arena-allocated) on change — a track
  change emits exactly one opcode.
- **`assign_ids`** (pre-order stable ids) — the **single id authority**: it hands
  out unique ids, satisfying the frame's **unique-node-id invariant**
  (spec/OPCODE.md §Frame Lifecycle — a frame never carries two nodes with the same
  id). **Media controllers are pinned**: a `MEDIA_CONTROLLER` node keeps the id it
  was first given, so a structural swap that shifts other ids never changes the
  playback key; the same pass links each `VIDEO` surface's `MEDIA_CONTROLLER_ID`
  to the controller node.

## Not implemented / gaps

- Only the `Tap` gesture plus value/text/date input sinks; no other gesture.
- Signal-bound properties cannot carry token refs (a token ref is a static path).
- `get_signal` returns an owned value (the store is shared via `Rc<RefCell>`).

## Verified by

`cargo test -p pathland-engine` — emitter deltas, signals (values, computed,
effects, `untracked`), `component_type_id`.