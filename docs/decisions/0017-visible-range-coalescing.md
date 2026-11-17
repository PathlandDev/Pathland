# ADR-0017: Latest-wins coalescing of lazy visible-range reports

**Status:** Proposed
**Recorded:** 2026-11-17
**Scope:** Lazy-window input handling, paged data sources, DOM client, server runtime
**Supersedes:** None (extends ADR-0014/ADR-0015)

## Context

A windowed lazy stack reports `EVENT::VISIBLE_RANGE` when its visible range
changes. A fast scrollbar drag can produce a report every animation frame across
a large distance. The engine applies each report to the window, and a paged data
source loads the pages covering every intermediate window. On a long list this
can load much or all of the dataset for a gesture the user never paused on,
competing with the final window's pages and delaying it.

Only the latest visible range in a continuous input burst is useful for
rendering and prefetch: intermediate ranges are already off-screen by the time
their pages arrive.

## Decision

Treat successive visible-range reports for the same target as a latest-wins
stream:

- The DOM client throttles scroll/resize-driven reports to at most one per
  ~100 ms and still sends a final report when the gesture ends. Reports after
  hydration, batch application, or connection open stay immediate.
- The server runtime MAY coalesce reports per `(session, target)` for a short
  interval (~50 ms) before dispatching to the session actor; non-range events
  dispatch immediately.
- A paged data source applies only the current requested window. A page result
  that arrives after the window moved is cached (bounded) for a later return,
  but does not bump the render version for the current window and cannot add
  rows outside it; a stale failure does not change the visible loading/error
  state.

Exact behavior lives in [EVENTS.md §Visible range](../../spec/EVENTS.md#visible-range),
[LAYOUT.md §Virtual extent](../../spec/LAYOUT.md#virtual-extent-lazy-containers),
and [DSL.md §Virtualized lists](../../spec/DSL.md#virtualized-lists-lazyvstack--lazyhstack).
The renderer event surface and wire format are unchanged.

## Rationale

- Preserves ADR-0015's engine-owned prefetch: the final/current window still
  drives the bounded, directional margin.
- Bounds page I/O by the window the user settles on, not the path taken there.
- Renderer-agnostic: a client that reports every frame is corrected by the host,
  and the data source is safe regardless of report cadence.
- Keeps the protocol unchanged: `VISIBLE_RANGE` keeps its semantics; coalescing
  is quality-of-service at the engine/host boundary.

## Alternatives

- **Final-report-only** (suppress intermediate reports): rejected — the list
  would show placeholders during the drag and lose directional prefetch.
- **Data-source suppression only**: rejected — page I/O for intermediate windows
  still runs; only rendering is avoided.
- **Client throttle only**: rejected — other renderers/hosts would still
  dispatch every report.
- **Page cancellation/priority**: rejected for now — a larger I/O scheduler;
  latest-wins plus the current-window guard is simpler and sufficient.

## Consequences

- Intermediate pages that do arrive are kept, so returning to a previous window
  reuses them instead of showing blank/placeholder rows and re-fetching. Work is
  still bounded by the windows the user actually visited.
- Client cadence is a heuristic, not a protocol guarantee; the server and data
  source guards remain authoritative.
- The ~100 ms client and ~50 ms server intervals are implementation tuning, not
  author-configured overscan.
- Tests cover the client throttle, server coalescing, stale-page caching, and
  fast back-and-forth rendering.

## Sources and current contract

- Lazy window: [LAYOUT.md §Virtual extent](../../spec/LAYOUT.md#virtual-extent-lazy-containers).
- Visible-range event: [EVENTS.md §Visible range](../../spec/EVENTS.md#visible-range).
- Authoring: [DSL.md §Virtualized lists](../../spec/DSL.md#virtualized-lists-lazyvstack--lazyhstack).
- Data sources: [docs/data-sources.md](../data-sources.md).
- Related decisions: [ADR-0014](0014-virtualized-lazy-stacks.md),
  [ADR-0015](0015-reactive-lazy-prefetch.md),
  [ADR-0016](0016-async-paged-data-sources.md).
