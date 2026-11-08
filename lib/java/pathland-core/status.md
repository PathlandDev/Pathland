# pathland-core (JVM opcode/wire core) — implementation status

**Last updated:** November 8, 2026

The Pathland JVM opcode and wire core, written entirely in **Java 17** with no
Kotlin source and no native dependency. It owns the 16-byte protocol constants,
the `Opcode` value type, the self-contained `PLPL` frame codec, the opcode sink
surface, raw input events, environment data, the attributed-text model, and the
pure-Java inline-Markdown parser. `pathland-view-core` and every renderer/server
module depend on this module.

## Implemented

- **Protocol** (`dev.pathland.core.protocol`): `Components`, `Properties`,
  `Commands` (incl. `SET_ATTRIBUTED_TEXT`), `Categories`, `ValueTypes`, `Opcode`,
  `ValueEncoder`, `WireValue`, `TokenPath`, `ColorValue`. `ColorValue` is the
  small SPI that lets the wire layer encode colors without importing the
  view-side `Color` type.
- **Transport** (`dev.pathland.core.transport`): `ProtocolFrame`, `FrameCodec`,
  `Event`, `EnvironmentData`, `OpcodeSink`, `FrameOpcodeSink`.
  **Hardened (issue #63)**: `FrameCodec.parse` reads the attacker-controlled
  `opcodeCount`/`stringsLen` as unsigned `long` values and validates them
  against the actual message length before allocating; string offsets are
  bounds-checked (`FrameCodec.Parsed.stringAt`). A declared `opcodeCount` that
  overflows `int` arithmetic now throws instead of triggering an OOM allocation.
- **Attributed text** (`dev.pathland.core.text`): `AttributedString`,
  `TextAttribute`, `StyleRun`, `LinkRun` — range-tracking builder (character
  indices → UTF-8 byte offsets) and payload encode/decode. `Text(AttributedString)`
  / `Text(Signal<AttributedString>)` in the Kotlin DSL emit
  `SET_ATTRIBUTED_TEXT` through the emitter and sinks.
- **Built-in inline Markdown** (`dev.pathland.core.markdown`): a pure-Java
  parser (no native library, so it runs on every platform and under GraalVM
  native-image); a behavioural port of the Rust `pathland-markdown` crate
  (pre-scan + bounded cache). Plain results use the compact `SET_TEXT` path;
  rich results emit `SET_ATTRIBUTED_TEXT`.
- **Protocol manifest validation** (`ProtocolManifestTest`): the protocol
  constants (`Components`, `Properties`, `Commands`, `Categories`, `ValueTypes`)
  are checked against `spec/protocol.tsv` — including no extra public numeric
  constants and the full `ValueTypes.forProperty` mapping. No Java source is
  generated.
- **Tests**: `pathland-core` owns the protocol-manifest, frame-codec, and
  Markdown tests.

## Boundaries

- Everything here is pure JVM Java: no Kotlin, no JNA, no native library.
- The retained-tree engine, signals, state, router, View SPI/value types, and
  the authoring DSL remain in `pathland-view-core`.
