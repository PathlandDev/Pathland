# pathland-core-capi — implementation status

**Last updated:** September 14, 2026

The **minimal ring C ABI** (`libpathland_core`) used by the Java DSL via JNA.
Protocol contract: `spec/OPCODE.md`.

## Implemented

- `pathland_core_create` / `pathland_core_destroy`.
- `pathland_ring_buffer_push` (one raw 16-byte opcode) + frame boundaries
  (`begin_frame`/`end_frame`, `frame_count`).
- `pathland_core_arena_alloc` (guest arena).
- Zero-copy `ring_ptr` / `ring_len`.
- `pathland_core_ring_mut` — borrow the underlying `RingTransport` for a
  same-process consumer (the GTK renderer pumps it in place via
  `pathland_gtk_run_ring`); null-guarded.
- Event drain / send.

## Not implemented / gaps

- No typed helpers beyond the raw ring surface (component/property constructors
  live in the Java DSL or `pathland-view-native`).

## Verified by

`cargo test -p pathland-core-capi` — ring push/read, frame count, arena.