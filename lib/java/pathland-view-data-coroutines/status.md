# pathland-view-data-coroutines — implementation status

Optional Kotlin coroutines adapter for the Pathland data sources
([ADR-0016](../../../docs/decisions/0016-async-paged-data-sources.md)).

## Implemented

- `AsyncDataSource.fromSuspend(scope) { … }` bridges a `suspend` load-all loader
  to the core `CompletionStage` API via `kotlinx-coroutines-jdk8`'s `future`.
- `PagedDataSource.fromSuspend(scope, pageSize) { offset, limit -> … }` bridges a
  `suspend` page loader.
- Tested with an unconfined scope and a timeout.

## Planned

- Cursor loaders (ADR-0016 Phase 3).
- Flow-based paged helpers.
