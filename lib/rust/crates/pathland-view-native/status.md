# pathland-view-native — implementation status

**Last updated:** August 28, 2026

The **native C-ABI shim + `NativeHost`**: a flat world over the shared-memory
ring for foreign hosts (Swift/Java/C# via FFI). Protocol contract: `spec/`.

## Implemented

- **`NativeHost`**: owns the shared ring, `create_node`/`set_property`/
  `set_text`, frame emission, `drain_events`.
- **Component mapping** (`component_from_id`/`component_id`): the full
  component set — stacks, `Text`, `Button`, `Spacer`, and all drawing/
  container/semantic-control components, using the renumbered ids.
- **Property value types** (`property_value_type`): COLOR (incl.
  `COLOR_VALUE`/`COLOR_MULTIPLY`/`SHADOW_COLOR`), U32 (`EVENT_LISTENERS`/
  `BORDER_EDGES`/`ACTION_ID`/`BINDING_ID`/`LINE_LIMIT`/`SELECTION`), STRING
  (`LABEL`/`PROMPT`/`FONT_FAMILY`/`IMAGE_SOURCE`), U8 (`VISIBLE`/`ENABLED`/
  `SELECTED`/`CLIPS_TO_BOUNDS`/`UNDERLINE`/`STRIKETHROUGH`/`COLOR_INVERT`/
  `ALLOWS_HIT_TESTING`/`IS_SECURE`/`IS_INDETERMINATE`/`FIXED_SIZE_*`), else F32.
- **Signals** C ABI (`signal_*`, `node_bind_text`/`node_bind_property`).

## Not implemented / gaps

- STRING-valued properties are typed as STRING but the flat world has no
  arena-alloc helper to emit them (callers must `pathland_core_arena_alloc`).
- **Media**: `MEDIA_CONTROLLER`/`VIDEO` are mapped in `component_from_id`/
  `component_id`, but the flat world has no media surface of its own — a
  `MediaController` is an engine-side `Rc` state object (the Rust DSL's `Video` +
  `attach()` and the Java `MediaController` both own their emitters), so there is
  no `node_media_controller`/`node_bind_string_property` C ABI yet. Native hosts
  that link only the flat surface cannot author media.

## Verified by

`cargo test -p pathland-view-native` — flat-world emission, component mapping,
signal deltas.