# ADR-0011: Explicit Fixed/Fill/Hug allocation rather than implicit stretching

**Status:** Accepted
**Recorded:** 2026-11-12 (extracted existing decision; original date not established)
**Scope:** Cross-renderer allocation semantics

## Context

Native renderer-owned layout still needs shared meanings for size and alignment.
Toolkit defaults differ: CSS flexbox stretches cross-axis children by default,
while the documented SwiftUI/Compose model hugs their content.

## Decision

Use Fixed, Fill, and Hug as the per-axis size model. Absent size means Hug;
alignment positions rather than resizes. Only explicit Fill or the contract's
listed greedy primitives demand extra space. Fixed allocation, content fitting,
and clipping are distinct concerns.

## Rationale

The layout specification deliberately follows SwiftUI/Compose rather than CSS's
implicit stretching. Its rationale records the errors this avoids: fixed images
changing size, Fixed/Hug children growing inside Fill containers, and composite
buttons expanding despite content-sized intent.

## Alternatives

Implicit CSS cross-axis stretching and treating fixed size as a minimum/hint
are explicitly excluded. This is shared allocation semantics, not a decision to
move geometry calculation into the engine.

## Consequences

Renderer adapters must override conflicting native defaults. Greedy exceptions
and bounds must be handled explicitly, not generalized into stretch-everything
behavior. The full allocation rules remain in the layout spec.

## Sources and current contract

- [Size model](../../spec/LAYOUT.md#the-size-model).
- [Container allocation](../../spec/LAYOUT.md#container-allocation-rules).
- [Native layout ownership](0002-native-declarative-layout.md).
