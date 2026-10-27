# @pathland/engine — status

**Last updated:** October 22, 2026

## Implemented

- Deterministic pre-order node ids starting at 1.
- Retained tree + `FrameOpcodeSink` over `BatchWriter`.
- Canonical emission order: create, config properties, modifiers, binding id,
  text, child subtree + insert.
- Text/property binding effects (including reactive string/color/number
  properties and `MEDIA_*` controller properties); unchanged signals emit zero
  opcodes.
- `renderFull()` for resync.
- `RenderResult` + `InputDispatcher` for taps, text/value changes, and
  navigation.
- `TextField` binds `SET_TEXT` + `BINDING_ID`; `Slider` binds `VALUE` and routes
  value events; `Button` emits pointer listener bits and routes taps.
- `MediaController` materialization: one `MEDIA_CONTROLLER` node with source,
  playback state, position, volume, mute, loop, rate; `MEDIA_*` reports route to
  the controller.
- Tests cover snapshot opcodes, delta-only updates, zero-op unchanged writes,
  full re-emit, text input routing, and button taps.

## Not implemented / gaps

- No structural reconciliation (`CREATE`/`DELETE`/`INSERT`/`MOVE` for dynamic
  subtrees); the retained tree is static after mount.
- `VIDEO` surfaces and size-that-fits slots are not implemented.
