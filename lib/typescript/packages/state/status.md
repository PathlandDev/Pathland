# @pathland/state — status

**Last updated:** October 22, 2026

## Implemented

- `StateStore` contract and `MemoryStateStore`.
- `State<T>` field holder with explicit or field-name keys.
- `PersistentState`:
  - loads a scope map before mount;
  - auto-connects `State` fields (and nested view objects);
  - creates memoized signals;
  - saves through the store on change, best-effort.
- Async `StateStore.load` is supported by the server's preload path
  (`PathlandSession.create`).
- Tests cover preloading, save-on-change, scoping, field connection, explicit
  keys, and teardown.

## Not implemented / gaps

- Redis store (`@pathland/state-redis`) is documented but deferred.
- No TTL or idle-state cleanup yet.
