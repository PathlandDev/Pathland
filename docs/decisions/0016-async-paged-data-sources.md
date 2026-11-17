# ADR-0016: Framework-neutral async and paged data sources for lists

**Status:** Proposed
**Recorded:** 2026-11-16
**Scope:** Authoring/runtime data sources, lazy-window prefetch, session lifecycle, SSR
**Supersedes:** None (extends ADR-0014/ADR-0015 for external data)

## Context

Applications commonly render lists from a database or a REST API. Today
`ForEach` accepts only a `List<T>` or a `Signal<List<T>>`, and the lazy window
needs a finite item count plus random access. Wiring a database or REST list
therefore requires the application to fetch, materialize, marshal to the
session actor, and refresh manually — repeated for every list and framework.

The runtime already provides the necessary pieces:

- a per-session actor and `SessionContext.submit` for external results,
- topic subscriptions (`PathlandSignalHub`) for invalidation,
- a lazy window that reports the visible range and can prefetch,
- a per-session root factory (`PathlandApp.newRoot`) built inside the session
  context.

## Decision

Provide framework-neutral data sources in the JVM library, with optional
adapters:

- **`AsyncDataSource<T>`** for small lists: loads all items once, exposes
  `items`/`loading`/`error`, and supports `refresh`/`close`.
- **`PagedDataSource<T>`** for large lists: offset/limit and cursor/hasMore
  pages, a bounded page cache, `total`/`loading`/`error`, and
  `ensureLoaded(first, last)` driven by the lazy window.
- **Async API:** blocking loaders and `CompletionStage` loaders in the core; an
  optional Kotlin coroutines adapter module. No coroutines, Mutiny, or Spring
  dependency in the core.
- **`ForEach(source)`** for paged sources: loaded items render normally,
  unloaded rows render a placeholder of the same main-axis extent.
- **SSR loading state:** data sources start in the loading state; the SSR HTML
  renders the application's loading/skeleton view. The SSR session does not run
  async page loads (it is discarded); the live session loads and sends deltas.
  Applications may opt into a synchronous preload later.
- **Session lifecycle:** data sources capture the session context, apply results
  on the actor, detect SSR vs live (`isLive`), and close via `onClose`.
- **Rust:** a `pathland-view-data` std crate provides the same concepts for the
  desktop hosts, using the existing `VisibleWindow`/`ForEach` window handle.

Exact requirements live in `spec/DSL.md` §Virtualized lists,
`spec/LAYOUT.md` §Virtual extent, and `docs/data-sources.md`.

## Rationale

- The application should describe *what* data a list shows, not the async,
  paging, placeholder, and lifecycle plumbing for each list.
- A blocking + `CompletionStage` core works for JDBC/JPA (blocking) and REST
  clients (`CompletionStage`/`Uni`) from both Java and Kotlin hosts without a
  new core dependency.
- The session actor already provides the single-threaded signal contract;
  applying external results through `submit` keeps renderers and effects
  unchanged.
- SSR cannot await arbitrary async work after the session closes; a loading
  state is honest and lets the live session fill the data.
- Offset/limit is simple and supports random access/counts; cursor/keyset is
  more performant and stable for large or append-only feeds. Supporting both
  lets the application choose per data source.
- A placeholder row preserves the virtual extent and scrollbar while pages
  load, so the list never collapses.

## Alternatives

- **Require applications to materialize a `Signal<List<T>>` manually.** Rejected
  as the primary path: it repeats paging/placeholder/SSR/lifecycle code in every
  app and has no window-driven prefetch.
- **A Kotlin-only `suspend`/`Flow` core API.** Rejected: the library is used by
  Java hosts and the Quarkus extension; coroutines remain an optional adapter.
- **A Mutiny/Reactor-specific core.** Rejected: framework coupling; adapters
  belong at the edges.
- **SSR blocking preload as the default.** Rejected: an arbitrary REST/DB call
  would block the SSR request; the loading state is the default and preload is
  opt-in.
- **A generic infinite-stream abstraction without a count.** Rejected for v1:
  the protocol's `ITEM_COUNT` and the lazy window need a finite, addressable
  range; cursor sources grow the count as pages arrive.

## Consequences

- `ForEach` gains a data-source overload; the lazy stack/grid call
  `prefetch(first, last)` on the initial and adaptive windows.
- `SessionContext` gains `isLive()` and `onClose(Runnable)`; the server session
  runs cleanup callbacks on close.
- The core gains a small `dev.pathland.view.data` package; an optional
  `pathland-view-data-coroutines` module adds `suspend` loaders.
- The Quarkus demo gains an in-memory `TrackRepository`; the MusicPlayer library
  uses a paged source. Docs show Panache/JDBC/REST examples.
- SSR renders a loading state; applications that need SSR data can opt into a
  preload.
- The protocol is unchanged.

## Sources and current contract

- Lazy window: [LAYOUT.md §Virtual extent](../../spec/LAYOUT.md#virtual-extent-lazy-containers).
- Authoring rules: [DSL.md §Virtualized lists](../../spec/DSL.md#virtualized-lists-lazyvstack--lazyhstack).
- External updates: [docs/external-updates.md](../external-updates.md).
- Related decisions: [ADR-0014](0014-virtualized-lazy-stacks.md),
  [ADR-0015](0015-reactive-lazy-prefetch.md).
