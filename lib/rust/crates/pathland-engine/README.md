# pathland-engine

The retained view tree (`Node`/`Component`) and the diff-based reactive emitter
that turns a tree + signals into a stream of 16-byte opcodes. Split out of
`pathland-core` so the protocol core stays emitter-free.

Part of [Pathland](https://github.com/PathlandDev/Pathland). Depends on
[`pathland-core`](https://crates.io/crates/pathland-core).

## License

Apache-2.0.
