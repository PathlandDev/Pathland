# ADR-0015: Engine-derived reactive prefetch for lazy windows

**Status:** Accepted
**Recorded:** 2026-11-16
**Scope:** Authoring DSL, engine lazy-window policy, renderer visible-range reporting
**Supersedes:** None (refines the lazy-window policy described in `spec/LAYOUT.md`)

## Context

A windowed lazy stack/grid renders only the rows the application emitted. The
renderer reports `VISIBLE_RANGE` after scrolling, the engine re-renders the
window, and the delta travels back to the renderer. With a fixed two-row
overscan, a fast scroll can move farther than the prefetch buffer before the
round trip completes, leaving a blank region where rows have not arrived yet.

Making overscan an author-facing setting would push a renderer-latency concern
into application code. The engine already receives a stream of visible-range
reports and already owns the window signal, so it can derive the prefetch
itself.

## Decision

The engine derives a bounded, directional prefetch margin from the observed
visible-range velocity. No author-facing overscan setting and no new protocol
property.

- The renderer continues to report the visible range (`VISIBLE_RANGE`).
- The engine keeps the previous report `(first, count)` and its arrival time.
- On each report it computes `velocity = |delta| / elapsed`, then
  `extra = ceil(velocity * latencyBudget)`, clamped to a bounded maximum.
- Extra rows are prefetched in the direction of travel; a small base margin is
  kept on both sides.
- A report with no movement returns the window to the base margin.
- Renderers SHOULD report a final range when a scroll gesture ends so the
  prefetch decays promptly.
- The application may still emit a larger window when it has its own reasons.

Exact requirements live in `spec/LAYOUT.md` §Virtual extent, `spec/DSL.md`
§Virtualized lists, and `spec/EVENTS.md` §Visible range.

## Rationale

- The blank region is a renderer/transport latency problem, not an application
  layout choice; the engine is the component that owns the window and sees the
  report cadence.
- The velocity signal is already implicit in successive visible ranges, so the
  change adds no protocol surface and works for every renderer.
- The base margin preserves the existing behavior for the first report and for
  slow scrolling.
- The bounded maximum prevents a large scrollbar jump from emitting an
  unbounded number of rows.

## Alternatives

- **Author-configured overscan.** Rejected by the maintainer: it moves a
  renderer-latency tuning knob into application code and would need a DSL
  surface for every lazy container.
- **Renderer-reported prefetch margin.** Rejected: it would conflate “visible”
  with “prefetched” in `VISIBLE_RANGE`, and renderers do not own the window
  policy.
- **Placeholder/skeleton rows.** Rejected: avoids blank space but shows fake
  content; the goal is to render real rows ahead of the viewport.
- **Keep evicted rows mounted until replacements arrive.** Rejected: the
  application owns the retained tree; retaining deleted rows violates renderer
  statelessness and can show stale content.

## Consequences

- `spec/LAYOUT.md` changes overscan ownership from the application to an
  engine-owned adaptive margin (the application may still emit a larger window).
- The JVM engine and the Rust host helper implement the same formula; the
  renderer event surface is unchanged.
- Renderers should send a final range on scroll end (DOM: `scrollend` plus a
  debounce fallback).
- The prefetch is bounded; a scrollbar jump larger than the bound may still
  briefly blank, but wheel/flick scrolling is covered.
- The first report always uses the base margin; adaptive prefetch starts on the
  second report.

## Sources and current contract

- Lazy window placement: [LAYOUT.md §Virtual extent](../../spec/LAYOUT.md#virtual-extent-lazy-containers).
- Authoring rules: [DSL.md §Virtualized lists](../../spec/DSL.md#virtualized-lists-lazyvstack--lazyhstack).
- Visible-range event: [EVENTS.md §Visible range](../../spec/EVENTS.md#visible-range).
- Related decisions: [ADR-0014](0014-virtualized-lazy-stacks.md).
