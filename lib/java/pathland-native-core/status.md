# pathland-native-core — implementation status

**Last updated:** November 8, 2026

Opt-in JNA interop for native desktop hosts. Kept in its own module so every
server runtime and GraalVM native-image build never carries JNA.

## Implemented

- **`PathlandCore`** (`dev.pathland.nativecore`): lazy JNA binding to the Rust
  `libpathland_core` cdylib — ring host lifetime, opcode push, arena allocation,
  frame boundaries, shared ring access, and event drain/send. Resolves the
  library from `pathland.core.lib` or `java.library.path`.
- **`RingOpcodeSink`** (`dev.pathland.nativecore`): zero-copy `OpcodeSink` that
  writes 16-byte opcodes into the Rust SPSC ring through `PathlandCore`,
  including arena-backed strings, design-token paths, float lists, attributed
  text, dates, and theme/window metadata.

## Consumers

- `pathland-desktop-demo` (GTK and Qt hosts) — the only in-repo consumer.
- External native hosts that drive `libpathland_core` directly.

## Not implemented / gaps

- No automated test loads the native library (it is built by `scripts/lib.sh`
  for the desktop demos); the module is compile/test-compile clean without it.
