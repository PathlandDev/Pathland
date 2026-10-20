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
- **Signals** C ABI (`signal_*`, `node_bind_text`/`node_bind_property`/
  `node_bind_string_property`).
- **Media C ABI** (`media_controller_create`/`_signal`/`_source`,
  `node_media_controller`) — spec/MEDIA.md.

## Not implemented / gaps

- STRING-valued properties are typed as STRING but the flat world has no
  arena-alloc helper to emit them (callers must `pathland_core_arena_alloc`).
- **Media**: `MEDIA_CONTROLLER`/`VIDEO` are mapped in `component_from_id`/
  `component_id`, and the flat world **authors media**: `media_controller_create`
  returns a handle (the controller is an engine-side `Rc` held by the host),
  `media_controller_signal(handle, slot)` addresses each transport slot (driven
  with `signal_set_*`), `media_controller_source` sets a static resource;
  `node_media_controller(node, handle)` makes a node the controller's own
  `MEDIA_CONTROLLER` node or one of its `VIDEO` surfaces, and the generic
  `node_bind_string_property` binds a reactive `MEDIA_SOURCE`. Materialization
  links each surface's `MEDIA_CONTROLLER_ID` to the controller's host-supplied
  node id. (Hosts that supply ids themselves need no pinning.)

## Verified by

`cargo test -p pathland-view-native` — flat-world emission, component mapping,
signal deltas.