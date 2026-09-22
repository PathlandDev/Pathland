# pathland-server-core — implementation status

**Last updated:** September 12, 2026

The transport-agnostic Pathland server runtime: one app instance per session (SSR +
live 16-byte opcode deltas + environment) and the session registry, shared by the Spring
Boot and Quarkus starters. No web-framework dependency — framework glue lives in the
starters.

## Implemented

- **`PathlandApp`** — the app's root-view factory (`View newRoot()` + optional
  `theme()`); the single app-specific piece a starter needs.
- **`PathlandConnection`** — the transport seam (`send(byte[])`/`isOpen()`); each starter
  adapts its WebSocket type to it.
- **`PathlandSession`** — per-session: builds the `Platform.ACTIVE_PATH` signal, mounts
  `app.newRoot().environment(ACTIVE_PATH, activePath)`, send-on-`endFrame` sink over a
  `PathlandConnection`, `applyEnvironment` (re-route guard-aware via the bound router),
  `dispatch` (tap/nav-intent/text/value/date/NAVIGATE), `resync`, `renderHtml`, `close`.
- **`PathlandRegistry`** — session lifecycle: single actor thread, 1:1 sessions keyed by
  id, lazy creation on first environment message, pending connections,
  `open`/`environment`/`dispatch`/`resync`/`close`/`renderHtml`/`shutdown`.
- **`StateStores`** — default state store: Redis when reachable, else the supplied
  fallback.
- **SSR debug comments (property-gated)**: `PathlandSession`/`PathlandRegistry` take a
  `debugHtml` flag (constructor + `isDebugHtml()`); when set, `renderHtml` uses the
  renderer's debug mode so every rendered node is prefixed with an HTML comment naming
  its component and the modifiers applied. The Spring/Quarkus starters bind it to the
  `pathland.debug-html` property (default `false`).

## Not implemented / gaps

- No multi-node/shared sessions (each server owns its sessions in-memory).

## Verified by

`mvn test` — `PathlandSessionTest`: mounts + routes taps through the binding maps,
`applyEnvironment`/`resync`/`close` idempotent, registry SSR + lifecycle/teardown.