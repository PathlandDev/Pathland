# State and Redis (design)

**Status:** Deferred design for `@pathland/state-redis`.
**Last updated:** October 22, 2026

## Why a store abstraction

The TypeScript server mounts a fresh retained tree per session but keeps
application state across reloads through a `StateStore`. Phase 1 ships
`MemoryStateStore`; the same interface serves Redis, SQLite, files, or browser
storage later.

```ts
export interface StateStore {
  load(scope: string): Promise<Record<string, unknown>> | Record<string, unknown>;
  save(scope: string, key: string, value: unknown): void | Promise<void>;
  delete(scope: string, key: string): void | Promise<void>;
}
```

`PathlandSession.create` awaits `load(scope)` **before** mounting, so a live
session's first tree already contains the persisted values. `PersistentState`
then auto-saves on every signal write (best-effort).

## Proposed Redis layout

- Package: `@pathland/state-redis`.
- One hash per session scope:
  - key: `pathland:<mount>:<wid>`
  - field: the state key (`query`, `theme`, …)
  - value: JSON-encoded (`JSON.stringify`) or a typed encoding chosen by the
    store.
- The root mount uses `<mount> = "/"`; the scope component is the client's
  per-window id (`wid`).

Examples:

```text
pathland:/:3f0c...  field "query" = "\"ali\""
pathland:/app2:81b2... field "count" = "4"
```

## Semantics

- `load(scope)` returns all fields as a plain object (missing → `{}`).
- `save(scope, key, value)` writes one field; writes do not rewrite the whole
  hash.
- `delete(scope, key)` removes one field.
- Scope TTL (proposed): a sliding 7-day expiry refreshed on write. Sessions
  themselves have a separate in-process idle timeout.
- Serialization: JSON is the default. Non-JSON values fall back to an error
  logged by the store and are not persisted (the UI keeps running).
- Failure behavior: read failures fall back to `{}` so SSR still renders;
  write failures are best-effort and never break a UI update.
- Async: `load` and writes may return promises. The registry's per-session queue
  holds environment/events until the initial mount completes.

## Not in phase 1

- The Redis implementation itself.
- Cross-session sharing/broadcast.
- Typed schema migration.
