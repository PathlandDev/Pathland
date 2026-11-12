# ADR-0007: Portable, emitter-free protocol core

**Status:** Accepted
**Recorded:** 2026-11-12 (extracted existing decision; original date not established)
**Scope:** Rust crate boundaries, portability

## Context

Desktop is a current consumer, but the protocol is also intended for browser,
embedded, and remote projections. The wire implementation and retained-tree
emitter serve different responsibilities.

## Decision

Keep `pathland-core` focused on protocol types, memory, rings, arenas, and events;
the retained tree and emitter live in `pathland-engine`. Keep `pathland-core`
and `pathland-view` compatible with `no_std` plus `alloc` and the
`wasm32-unknown-unknown` target. Platform integration stays outside those surfaces.

## Rationale

The explicit wasm direction guard prevents the current desktop path from
turning the core into a desktop-only implementation. The engine README explains
the crate split as keeping the protocol core emitter-free.

## Alternatives

The older combined protocol/emitter arrangement is documented by the engine's
extraction from core. Desktop-only dependencies are prohibited by the direction
guard. A broader historical crate-design evaluation is not documented.

## Consequences

Changes must preserve the wasm build check and isolate platform dependencies.
`no_std` plus `alloc` does not mean all execution is allocation-free; the
steady-state emission allocation contract is a separate concern.

## Sources and current contract

- [Portability guard and verification command](../../AGENTS.md#no_std--wasm32-direction-guard-non-negotiable).
- [Protocol core README](../../lib/rust/crates/pathland-core/README.md).
- [Engine split README](../../lib/rust/crates/pathland-engine/README.md).
- [Rust workspace portability check](../../lib/rust/README.md#verify-the-no_std--wasm32-direction-guard).
