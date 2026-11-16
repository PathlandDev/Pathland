# pathland-view-data-coroutines — implementation status

Optional Kotlin coroutines adapter for the Pathland data sources
([ADR-0016](../../../docs/decisions/0016-async-paged-data-sources.md)).

## Implemented

- `AsyncDataSource.fromSuspend(scope) { … }` bridges a `suspend` load-all loader
  to the core `CompletionStage` API via `kotlinx-coroutines-jdk8`'s `future`.
- Tested with an unconfined scope and a timeout.

## Planned

- `PagedDataSource.fromSuspend` / cursor loaders (ADR-0016 Phase 2–3).
- Flow-based paged helpers if the paged source lands.
