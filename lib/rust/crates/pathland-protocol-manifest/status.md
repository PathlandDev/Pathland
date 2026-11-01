# pathland-protocol-manifest — implementation status

**Last updated:** November 1, 2026

Emits the target-neutral wire protocol manifest (`spec/protocol.tsv`) from the
canonical Rust constants (`pathland_core::constants`). It emits only data:
`kind`, `group`, `name`, `value`. It contains no target-language source,
package names, class structure, or formatting.

## Implemented

- Categories, components, commands, field ids, flags, listener bits, size
  sentinels, value types, property ids, and `property → value_type` mappings.
- Deterministic tab-separated output with a generated-file header.
- `cargo run -p pathland-protocol-manifest` regenerates the committed manifest.
- CI regenerates and fails on `spec/protocol.tsv` drift.

## Not implemented / gaps

- Enum wire-code tables (alignment, shape kind, control state, …) are not yet in
  the manifest; they remain hand-mirrored in the language bindings.
- The manifest is emitted from explicit Rust constant bindings rather than
  reflection; adding a new Rust constant deliberately requires adding its
  manifest row so the output stays ordered and documented.
