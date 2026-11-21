# ADR-0018: App-driven focus requests for keyboard navigation

**Status:** Accepted
**Recorded:** 2026-11-20
**Scope:** Wire protocol (`META`), JVM/Rust/TypeScript bindings, authoring DSL, HTML/DOM renderers, lazy-stack focus pinning
**Supersedes:** None (extends ADR-0004 and ADR-0014)

## Context

Pathland has raw keyboard events and focus observation, but no way for an
application to move renderer focus. A focused row can report `KEY_DOWN` and
`FOCUS_CHANGED`, yet an app-side handler cannot move focus to the next row.
Composed lists therefore cannot implement the keyboard navigation that a native
SwiftUI `List(selection:)` gets from its control implementation.

The music-player demo's library is `ScrollView` + `LazyVStack` with rows built
from `HStack` + `onTapGesture`. The rows are not focusable and the host drops
`KEY_DOWN`/`FOCUS_CHANGED`. Tabbing can reach the scrollable container in some
browsers, but the list has no roving row focus, no arrow navigation, and no way
to keep a focused row mounted while the lazy window slides.

Alternative approaches were considered but are not the selected model here:

- Let renderers interpret `ROLE=List` and implement arrow-key focus traversal
  themselves. This makes semantic structure behavioral and puts list navigation
  policy in every renderer.
- Represent focus as an app-owned `FOCUSED` property. Focus is renderer state;
  a one-shot request is clearer than a two-way state property.
- Add a `SCROLL_TO`/reveal command only. It can scroll a selection into view but
  does not establish focus, native focus rings, or accessible roving focus.
- Make every row tabbable and use only Tab. This does not provide arrow-key list
  navigation and creates an unbounded tab order for large lists.

## Decision

Add a guest-to-host focus request and keep focus itself renderer-owned:

- `META::SET_FOCUS` requests focus for one node (`A` = node id; `B` = 1 to focus,
  0 to clear; `A = 0`, `B = 0` clears the current focus). It follows the
  target's structural and property deltas in the emitted frame so the target
  can be materialized and focusable first; renderers apply commands in stream
  order.
- Focus remains renderer state. Renderers continue to report actual focus with
  `EVENT::FOCUS_CHANGED` for nodes that declared the `FOCUS` listener bit.
- Declaring the `FOCUS` listener bit makes a node focusable/tabbable; the app
  uses that to provide a single roving tab stop on the selected row.
- A `.focused` binding going from true to false emits a clear request for that
  node; an app may also request an explicit global clear.
- Apps observe focus with `.onFocusChanged`, handle keys with `.onKeyDown`, and
  request focus by binding `.focused(...)` to the target row.
- Canonical key codes are defined by the specification; every renderer that
  reports `KEY_DOWN`/`KEY_UP` maps its platform keys onto that set.
- ADR-0014's focused-row pinning keeps the focused row in the lazy window. Apps
  must bound the pin so a far-away focused row cannot defeat virtualization.

Exact requirements belong in the specifications (see "Sources and current
contract"); this record only fixes the architectural direction.

## Rationale

- The protocol already models raw input and focus observation; a focus request
  completes the same renderer-owned interaction loop without making renderers
  own application selection or list policy.
- A one-shot request matches the existing imperative `META` control commands and
  avoids a retained two-way focus property that would need reconciliation with
  native Tab traversal.
- A single roving tab stop mirrors the SwiftUI `List`/`@FocusState` authoring
  model: the app owns the selected key, native focus follows it, and the
  renderer owns the focus ring.
- Keeping the focus target mounted through ADR-0014's window union prevents the
  focused native element from being deleted and recreated while focus moves.
  Renderers must also preserve native focus when a structural batch moves or
  re-inserts that element; a reorder is not a focus change.

The SwiftUI `List` vs `ScrollView`/`LazyVStack` behavior and the demo's current
structure are documented evidence. The choice of a focus request over the listed
alternatives is recorded here, not inferred from historical code.

## Alternatives

- **Renderer-owned list navigation:** fewer moving parts for app authors, but
  makes `ROLE` behavioral, duplicates navigation policy in every renderer, and
  limits app control over selection and activation.
- **`FOCUSED` property:** declarative, but focus is exclusive renderer state and
  would create a two-way property/Tab-focus reconciliation problem.
- **`SCROLL_TO` only:** simpler scroll reveal, but no focus, no native focus
  ring, and no assistive-technology focus movement.
- **Tab-only traversal:** no new wire surface, but not the requested behavior and
  impractical for long virtualized lists.

## Consequences

- Adds one `META` command and a canonical key-code contract across language
  bindings, with the usual manifest, conformance, spec, and status updates.
- Requires the JVM engine, Rust core, DOM client, and HTML renderers to agree on
  focusability and key delivery; GTK and Qt parity are follow-ups.
- Lazy stacks gain an app-supplied pin and a bounded union policy. Without the
  bound, pinning a far-away row emits the whole O(gap) window.
- Focus clear is part of the request contract; a binding that becomes false must
  emit a clear rather than relying on a later structural update.
- Apps must keep focus and playback/selection distinct where they differ.

## Sources and current contract

- Keyboard and focus events: [EVENTS.md §Keyboard](../../spec/EVENTS.md#keyboard),
  [§Text/FOCUS_CHANGED](../../spec/EVENTS.md#text).
- Control commands: [OPCODE.md §META commands](../../spec/OPCODE.md#meta-0x04).
- Focusable/list authoring: [DSL.md §Interaction & state](../../spec/DSL.md#55-interaction--state),
  [MODIFIERS.md §5 Interaction & State](../../spec/MODIFIERS.md#5-interaction--state).
- List composition: [PRIMITIVES.md §Composite views](../../spec/PRIMITIVES.md#composite-views-not-primitives).
- Lazy window: [LAYOUT.md §Virtual extent](../../spec/LAYOUT.md#virtual-extent-lazy-containers),
  [ADR-0014](0014-virtualized-lazy-stacks.md).
- Input history: [ADR-0004](0004-binary-input-events.md).
- Follow-up: [PathlandDev/Pathland#204](https://github.com/PathlandDev/Pathland/issues/204).
