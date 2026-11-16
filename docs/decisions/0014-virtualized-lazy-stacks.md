# ADR-0014: Virtualized lazy stacks through app-owned data sources

**Status:** Accepted
**Recorded:** 2026-11-12
**Scope:** Authoring DSL, retained-tree emission, lazy-container layout, renderer realization, SSR first paint
**Supersedes:** None (refines the lazy-container realization wording in `spec/PRIMITIVES.md`)

## Context

Lazy stack and grid component IDs exist (`LAZY_VSTACK`, `LAZY_HSTACK`,
`LAZY_VGRID`, `LAZY_HGRID`), but the protocol only says a renderer MAY defer
realizing off-screen children. No item count, extent, visible-range request, or
window placement exists, so every implementation treats lazy containers as eager
aliases. Two constraints make the current wording insufficient:

- The app emits the full child list, and the desktop ring (4096 slots) and arena
  (1 MiB) cannot carry thousands of rows.
- A renderer cannot position or size an unrealized row without a size model, so
  it cannot compute scroll extent or decide what is visible.

The authoring surface must remain SwiftUI-shaped (ADR-0013):
`LazyVStack(spacing:) { ForEach(items) { … } }`, not a parallel
`VirtualVStack`.

## Decision

Virtualize lazy stacks through an app-owned data source and a renderer-reported
visible range.

- `ForEach` inside a `LAZY_VSTACK`/`LAZY_HSTACK` is a data source, not an eager
  child list. Outside a lazy container it keeps eager behavior.
- The app supplies the item collection and row builder; the engine owns the
  visible window and materializes only the window's rows.
- Fixed uniform extent: the engine derives `ITEM_EXTENT` from the row's fixed
  main-axis frame (`.frame(height:)` / `.frame(width:)`), or from an explicit
  `itemExtent` when no fixed frame exists.
- The wire carries `ITEM_COUNT`, `ITEM_EXTENT`, and `FIRST_ITEM_INDEX` on the
  lazy container; the renderer reports `VISIBLE_RANGE` (first index + count) for
  nodes that declare listener bit 10.
- The lazy container occupies the full virtual main-axis extent
  (`N*E + max(0,N-1)*S`); emitted child `j` is placed at
  `(FIRST_ITEM_INDEX+j)*(E+S)`.
- Window children are reconciled by absolute index (or an optional item key), so
  ids stay stable while scrolling.
- SSR renders a conservative first window plus the full virtual extent; the
  client corrects the window after `META::ENVIRONMENT`.
- v1 covers `LAZY_VSTACK`/`LAZY_HSTACK`; lazy grids follow the same model in a
  later change.

Exact requirements live in `spec/PRIMITIVES.md`, `spec/LAYOUT.md`,
`spec/EVENTS.md`, `spec/MODIFIERS.md`, and `spec/DSL.md`.

## Rationale

- Only the window costs app nodes, wire bytes, renderer model memory, native
  elements, and SSR HTML; the full list can be arbitrarily large.
- The renderer stays stateless: it reports a measured/derived range, like
  `FIT_CHANGED`, and the app decides what to emit.
- The app-owned data source keeps the SwiftUI shape and avoids a second lazy
  authoring vocabulary.
- Fixed extent gives exact scroll extent and window math without measurement
  passes, matching the fixed-row-height pattern that avoids layout work.
- Windowed SSR avoids O(N) HTML while the full virtual extent keeps the
  scrollbar correct before JavaScript runs.

## Alternatives

- **Renderer-owned windowing with all children transmitted.** Rejected: O(N) app
  tree, wire, and renderer memory; overflows the desktop ring; cannot size
  unrealized rows; O(N) SSR.
- **Renderer pull by index.** Rejected for v1: adds a request/response handshake
  and renderer-owned item identity without removing the app's data-source work.
- **A separate `VirtualVStack`/`VirtualHStack` authoring API.** Rejected:
  confusing alongside `LazyVStack`, and contrary to the SwiftUI-shaped surface.
- **Variable-height first.** Deferred: needs estimation, measurement caching,
  and scroll anchoring; fixed extent covers the initial large-list use case.
- **Full SSR for every list.** Rejected as the default: O(N) HTML; remains
  available as an explicit host option for SEO-critical lists.

## Consequences

- New protocol properties (`ITEM_COUNT`, `ITEM_EXTENT`, `FIRST_ITEM_INDEX`),
  event (`VISIBLE_RANGE`), and listener bit (10) require Rust/Java/TS constants,
  manifest, codec, and conformance updates.
- The engine gains lazy-node window state and a keyed window reconcile;
  renderers gain virtual-extent placement and range reporting.
- Row-local component state is scoped by the `ForEach` item key and survives
  window eviction (follow-up implemented; spec/DSL.md §Virtualized lists).
- Lazy grids and variable-height rows are implemented; native recycling, scroll
  anchoring, and focused-row pinning remain follow-ups.
- The app must keep a focused row inside its window; the renderer does not
  auto-pin it.

## Sources and current contract

- Lazy container allocation: [PRIMITIVES.md §LazyVStack](../../spec/PRIMITIVES.md#lazyvstack--lazy_vstack-0x1b).
- Virtual extent and placement: [LAYOUT.md §Virtual extent](../../spec/LAYOUT.md#virtual-extent-lazy-containers).
- Event and listener: [EVENTS.md §Visible range](../../spec/EVENTS.md#visible-range), [§Event listeners](../../spec/EVENTS.md#event-listeners-event_listeners).
- Properties: [MODIFIERS.md §Layout](../../spec/MODIFIERS.md#1-layout).
- Authoring surface: [DSL.md §Virtualized lists](../../spec/DSL.md#virtualized-lists-lazyvstack--lazyhstack).
- Decisions: [ADR-0002](0002-native-declarative-layout.md),
  [ADR-0003](0003-application-state-and-delta-emission.md),
  [ADR-0011](0011-explicit-layout-allocation.md),
  [ADR-0013](0013-swiftui-shaped-signal-dsl.md).
