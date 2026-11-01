# pathland-engine-golden — implementation status

**Last updated:** November 1, 2026

Emits shared engine trace fixtures from the canonical Rust engine. Each fixture
is a self-contained PLPL batch (opcodes + string table) stored as hex under
`spec/engine-traces/`.

## Implemented

- `mount_text_stack`: mount a `VStack(spacing: 8)` containing `Text("Hello")`.
- `signal_text_delta`: mount a bound text signal, then change it to `"World"`.
- `property_signal_delta`: mount a bound `SPACING` signal, then change `8 → 12`.
- `styled_text_mount`: styled text with `FONT_SIZE` and `SET_TEXT`, proving the
  canonical property-before-text order.
- `--emit` regenerates the committed fixtures.
- `--check` compares the committed fixtures with current engine output.
- CI runs `--check` (via regeneration + `git diff --exit-code`).

The Java engine test (`EngineTraceTest`) builds the same scenarios through the
Java DSL and compares its encoded frames byte-for-byte.

## Not implemented / gaps

- Scenario construction is still duplicated between this Rust generator and the
  JVM test; a neutral scenario DSL is future work.
- Only mount + simple signal deltas are covered. Structural deltas, zero-op
  recompute, tokens/lists, fit, media, navigation, and window metadata are next.
