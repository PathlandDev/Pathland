# ADR-0013: SwiftUI-shaped authoring with signal-based reactivity

**Status:** Accepted
**Recorded:** 2026-11-12 (extracted existing decision; original date not established)
**Scope:** Authoring DSLs, signals, component reconciliation

## Context

Different languages need idiomatic authoring surfaces that map to the same
protocol. Reactive structural composition and fine-grained property updates
must coexist without changing the wire model.

## Decision

Use SwiftUI-shaped view/modifier vocabulary with Angular-style writable signals,
computeds, effects, and bindings rather than SwiftUI property wrappers. Reads in
a component's tracked `body()` subscribe that component; direct node bindings
retain fine-grained updates. Keep modifiers decoupled from view types.

## Rationale

The DSL contract explicitly separates familiar view syntax from the reactivity
model. It allows plain host-language control flow in tracked bodies and direct
signal binding without rerunning the body. Both paths still emit only actual
changes through the same engine contract.

## Alternatives

SwiftUI `@State`/`@Binding` wrappers are explicitly distinguished from the chosen
signal model. View-type-specific modifier mechanisms are excluded by the DSL
contract. The sources do not establish a historical comparison with other
reactivity libraries.

## Consequences

Conformant language DSLs must agree on semantics and opcode output, not merely
similar names. Component bodies must respect the contract's purity rules.
Authoring syntax can adapt to language constraints without reallocating wire
IDs or changing protocol meanings.

## Sources and current contract

- [DSL guiding rules](../../spec/DSL.md#two-guiding-rules).
- [Authoring principles](../../spec/DSL.md#1-design-principles-dsl-flavored).
- [Engine emission contract](../../spec/ENGINE.md#5-reactive-emission).
