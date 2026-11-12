# ADR-0002: Declarative structure and native renderer-owned layout

**Status:** Accepted
**Recorded:** 2026-11-12 (extracted existing decision; original date not established)
**Scope:** Engine, renderers, layout

## Context

The same UI declaration must project onto different native platforms without
binding the engine to one toolkit's geometry or drawing model.

## Decision

The engine describes structure and constraints, not positions or rectangles.
Renderers map those declarations to native elements whose layout systems perform
allocation. Use a generic canvas only where a platform has no native equivalent.

## Rationale

The README explicitly connects native elements with platform layout, text
rendering, and accessibility. The engine specification keeps layout outside the
emitter, while the layout specification defines shared meanings for constraints
without prescribing a device-pixel layout implementation.

## Alternatives

Engine-computed rectangles and generic canvas rendering are explicitly excluded
by the opcode principles and agent guardrails. No historical comparison of
individual layout engines is documented.

## Consequences

Each renderer must implement the allocation contract using its own native
elements. Consistency requires shared semantics, not centrally emitted geometry;
native toolkit behavior alone is not a substitute for the contract.

## Sources and current contract

- [Opcode core principles](../../spec/OPCODE.md#core-principles).
- [Engine scope](../../spec/ENGINE.md#1-scope).
- [Layout purpose](../../spec/LAYOUT.md#purpose).
- [Native rendering rationale](../../README.md#why-its-smooth).
