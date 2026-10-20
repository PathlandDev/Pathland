# pathland-core

Pathland's protocol core — the fixed **16-byte opcode** instruction set, the SPSC
ring buffer, the bump arena, the event surface, and the linear-memory layout.

`#![no_std]` + `alloc`; compiles for `wasm32-unknown-unknown`.

Part of [Pathland](https://github.com/PathlandDev/Pathland) — a cross-platform,
cross-language UI protocol where renderers are pure functions of a binary opcode
stream.

## License

Apache-2.0.
