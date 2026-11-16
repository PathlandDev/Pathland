# Data sources (databases, REST APIs)

Pathland lists are usually backed by a database or a REST API. The JVM library
provides framework-neutral data sources so an application supplies a loader and
the runtime handles async loading, session-actor application, loading/error
state, and (for large lists) paging and placeholders.

Decision record: [ADR-0016](./decisions/0016-async-paged-data-sources.md).

## Status

| Piece | Status |
|---|---|
| `AsyncDataSource<T>` (load-all) | Implemented |
| Kotlin coroutines adapter (`pathland-view-data-coroutines`) | Implemented |
| `PagedDataSource<T>` (offset/limit) + `ForEach(source)` | Planned (Phase 2) |
| Cursor/`hasMore` pages | Planned (Phase 3) |
| Invalidation via topics | Planned (Phase 4) |
| Rust `pathland-view-data` crate | Planned (Phase 5) |

## Async API

The core accepts a **blocking loader** or a **`CompletionStage` loader**:

```kotlin
fun interface ListLoader<T> { fun load(): List<T> }
fun interface AsyncListLoader<T> { fun load(): CompletionStage<List<T>> }
```

Why this shape:

- Java and Kotlin hosts can both use it.
- JDBC/JPA are blocking; the data source runs the loader on an executor.
- Quarkus REST Client/Mutiny and Spring `WebClient` expose `CompletionStage`;
  adapt with `uni.asCompletionStage()` / `Mono.toFuture()`.
- No coroutines, Mutiny, or Spring dependency in the core.
- Kotlin apps can use the optional coroutines adapter.

## Load-all: `AsyncDataSource<T>`

Use it for small lists (dropdowns, headers, “recently played”, a few hundred
rows).

```kotlin
class RecentTracksView(private val repo: TrackRepository) : Component() {
    private val recent = AsyncDataSource.ofBlocking { repo.recentTracks() }

    override fun body(): View = VStack {
        when {
            recent.loading.get() -> Text("Loading…")
            recent.error.get() != null -> Text("Could not load recent tracks")
            else -> ForEach(recent.items) { track, _ -> TrackRow(track) }
        }
    }
}
```

The data source exposes:

- `items: Signal<List<T>>`
- `loading: Signal<Boolean>`
- `error: Signal<Throwable?>`
- `refresh()` — reload, ignoring stale results
- `close()` — ignore in-flight results (also registered on session close)

`ForEach(items)` is the existing list overload; no engine change is needed.

### Async loader

```kotlin
val tracks = AsyncDataSource.ofAsync {
    restClient.tracks().toCompletableFuture() // or Uni.asCompletionStage()
}
```

### Coroutines adapter

Add `dev.pathland:pathland-view-data-coroutines` and use a `suspend` loader:

```kotlin
val tracks = AsyncDataSource.fromSuspend(scope) { repo.recentTracksSuspend() }
```

The adapter bridges through `CompletionStage`; the core stays coroutine-free.

## Session and SSR behavior

- The data source captures `SessionContext.current()` when constructed (the root
  view is built inside the session context).
- **Live session:** loading starts immediately; results are applied on the
  session actor via `submit`.
- **SSR session:** loading starts in the **loading state** and no async load is
  run; the SSR HTML renders the application's loading/skeleton view. The live
  session loads the data and sends the delta after hydration.
- `SessionContext.isLive()` distinguishes the two; `SessionContext.onClose(...)`
  closes data sources with the session.
- Applications that need SSR data can call a synchronous preload explicitly
  (planned); the default is the loading state.

## Paged lists (planned)

For large lists, a paged source will drive the lazy window:

```kotlin
val tracks = PagedDataSource(
    pageSize = 50,
    load = { offset, limit -> repo.page(offset, limit) }, // Page<Track>
)

LazyVStack {
    ForEach(tracks, itemExtent = 64f, placeholder = { SkeletonRow() }) { track, _ ->
        TrackRow(track)
    }
}
```

Planned behavior:

- `Page<T>(items, total, hasMore, nextCursor)`.
- Offset/limit and cursor/`hasMore` modes; a bounded page cache.
- The lazy stack calls `prefetch(first, last)` for the initial and adaptive
  windows; pages load ahead of the viewport.
- Unloaded rows render a placeholder of the same main-axis extent; the virtual
  extent and scrollbar stay correct.
- `total` may grow for cursor sources; the scrollbar extends as pages arrive.
- `refresh()`/invalidation via a topic version (Phase 4).

## Quarkus examples (planned)

- **Panache:** `query.page(offset / pageSize, pageSize).list()` plus
  `query.count()`; map to `Page`.
- **JDBC:** `limit ? offset ?` plus a count query.
- **REST Client (Mutiny):** return `Uni<PageResponse>` and adapt with
  `asCompletionStage()`.
- **Executor:** pass a Quarkus managed executor to the data source; results are
  still applied on the session actor.

The Quarkus demo will use an in-memory `TrackRepository` with a small delay to
exercise the loading/placeholder path; the docs show Panache/JDBC/REST variants.

## Rust (planned)

A `pathland-view-data` std crate will mirror the concepts for desktop hosts:
`Page<T>`, `PagedDataSource<T>`, and a `PagedList<T>` that implements
`View`/`LazySource`, using the existing `VisibleWindow`/`ForEach` window handle.
