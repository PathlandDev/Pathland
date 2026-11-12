# ADR-0003: Application-owned state and diff-based emission

**Status:** Accepted
**Recorded:** 2026-11-12 (extracted existing decision; original date not established)
**Scope:** Engine, renderers, reactivity

## Context

Pathland needs incremental updates without making each rendering target a second
application implementation or resending unchanged UI.

## Decision

Keep the canonical retained tree and application state on the application/engine
side. Emit commands only for actual property or structural changes. Renderers
may cache their output tree and maintain transient native input/rendering state,
but do not own business state or application control flow.

Initial mounts and explicit recovery snapshots still establish complete output;
the delta-only rule is not a prohibition on those protocol operations.

## Rationale

The documented model is deterministic application of the opcode stream, with
unchanged trees producing no opcodes or new frames. Signals target affected
nodes or components instead of transmitting unchanged subtrees. The README
identifies small updates and no idle traffic as central to the thin-client model.

## Alternatives

Serialized-tree updates and renderer-owned application state are explicitly
rejected in `AGENTS.md`. Output caches are explicitly allowed, not a rejected
alternative. The event specification also distinguishes active-edit state from
application state.

## Consequences

The emitter owns reconciliation and stable identity. Consumers must apply
structural and property deltas and recover through the specified snapshot path.
Transient caret, scroll, and in-flight edit state must not be mistaken for a
license to retain independent business state.

## Sources and current contract

- [Engine terms and snapshots](../../spec/ENGINE.md#2-terms).
- [Reactive emission](../../spec/ENGINE.md#5-reactive-emission) and [structural deltas](../../spec/ENGINE.md#6-structural-deltas).
- [In-flight interaction](../../spec/EVENTS.md#in-flight-interaction-must).
- [Architectural principles](../../AGENTS.md#critical-architectural-principles).
