# pathland-server-core — implementation status

**Last updated:** September 27, 2026

The transport-agnostic Pathland server runtime: one app instance per session (SSR +
live 16-byte opcode deltas + environment), the session registry, and the **multi-app
host** (the BFF), shared by the Spring Boot and Quarkus starters. No web-framework
dependency — framework glue lives in the starters.

## Implemented

- **`PathlandApp`** — the app's root-view factory (`View newRoot()` + optional
  `theme()`); the single app-specific piece a starter needs.
- **`MountedApp`** — `record MountedApp(String path, PathlandApp app)`: one app mounted
  at a subpath prefix (normalized; `/` = root). Hosts any number of them.
- **`PathlandHost`** — the multi-app dispatcher: one `PathlandRegistry` per mounted app
  (own actor thread, own sessions). `match(requestUri)` resolves the owning registry by
  **longest mount-prefix match** and reports the **mount-stripped route** (a root mount
  claims every unclaimed path). Duplicate mount paths are rejected at construction.
- **`PathlandRegistry`** — session lifecycle per app: single actor thread, 1:1 sessions
  keyed by a **per-connection `uiId`** (a fresh id per WebSocket connection, so two
  windows of the same browser never share a UI model), lazy creation on first
  environment message, pending connections (each carrying the mount-prefixed state
  scope from the client's per-window `wid`),
  `open(uiId, wid, conn)`/`environment`/`dispatch`/`resync`/`close`/`renderHtml`/`shutdown`. New:
  - `mountPath()`/`base()` — each app's framework base is `/<mount>/_pathland`
    (`/_pathland` for the root), emitted as `data-pathland-base` in SSR;
  - `stateScope(windowId)` — app-qualified state-store scope (`mount:wid`) so apps and
    windows sharing a store never collide;
  - `stripRoute(route)` — strips the mount prefix from SSR routes, WS environment
    ROUTE fields, **and inbound `NAVIGATE` URLs** (browser back/forward sends the real
    address, e.g. `http://host/app2/home`), so a mounted app always sees its own route
    space (`/app2/home` → `/home`). `Router.pathOf` is reused for URL→path extraction.
- **Per-window isolation (no session cookie)**: session <b>identity</b> (the `uiId`
  map key) is deliberately separate from the <b>persisted-state scope</b> (the client's
  `wid`, carried only on the WebSocket URL — never in the page URL, so the address bar
  stays clean). The SSR request therefore has no `wid`: `renderHtml(route, wid)` with
  a null `wid` renders <b>defaults</b>; the client's live session is scoped by the WS
  `wid`, and a same-tab reload re-syncs that window's persisted state over the
  WebSocket. The old `session` cookie is removed.
- **`PathlandSession`** — per-session: builds the `Platform.ACTIVE_PATH` signal, mounts
  `app.newRoot().environment(ACTIVE_PATH, activePath)`, **delta-batched send-on-`endFrame`**
  (`DeltaBatcher` coalesces per-signal frames into one batch — flush ~20 ms or 16 KB,
  string offsets rebased — so a continuous input burst can't overflow a remote WebSocket
  send queue), `applyEnvironment` (re-route guard-aware via the bound router),
  `dispatch` (tap/nav-intent/text/value/date/NAVIGATE), `resync`, `renderHtml`, `close`.
  `sendBatch` drops the connection on any send failure (a client reconnect then
  re-syncs via `META::RESYNC` instead of silently stalling on an "open" socket);
  `closeConnection` best-effort closes an `AutoCloseable` adapter (the starters now
  close their underlying WebSocket on failure).
  Now takes the app's `base` and `stateScope` explicitly (the registry supplies them).
- **`DeltaBatcher`** — merges session delta frames (opcodes + a single rebased string
  section) and flushes as one network batch on a timer/byte threshold; the Java analogue
  of the Rust transport's `Batcher`. The **sender runs OUTSIDE the monitor** — a
  blocking/queueing send can never stall the actor thread's `append` or the flush
  scheduler. Coalescing tests cover merging + rebasing, a 100-frame burst collapsing to
  one send, and a blocking sender not holding the lock.
- **`PathlandConnection`** — the transport seam (`send(byte[])`/`isOpen()`); each starter
  adapts its WebSocket type to it.
- **`StateStores`** — default state store: Redis when reachable, else the supplied
  fallback.
- **SSR debug comments (property-gated)**: `PathlandSession`/`PathlandRegistry` take a
  `debugHtml` flag (constructor + `isDebugHtml()`); when set, `renderHtml` uses the
  renderer's debug mode so every rendered node is prefixed with an HTML comment naming
  its component and the modifiers applied. The Spring/Quarkus starters bind it to the
  `pathland.debug-html` property (default `false`).
- **Reserved framework root**: `PathlandSession.PATHLAND_BASE` (`/_pathland`) — the
  SSR page carries the app's base as `data-pathland-base` and the DOM-client bundle is
  injected at `{base}/dom-renderer.js`, so the client's WebSocket and asset refs resolve
  from the same reserved prefix (spec DSL.md). Multi-app hosts give each mount its own
  base.

## Not implemented / gaps

- No multi-node/shared sessions (each server owns its sessions in-memory).
- Quarkus mounted-app WebSocket endpoints require a **single-segment** mount path
  (the `/{app}` path param); Spring supports any depth.

## Verified by

`mvn test` — `PathlandSessionTest`: mounts + routes taps through the binding maps,
`applyEnvironment`/`resync`/`close` idempotent, registry SSR + lifecycle/teardown, and
**two windows with the same `wid` still get separate sessions** (a tap to one never
reaches the other).
`PathlandHostTest`: longest-prefix dispatch + route stripping, mount-boundary
respect, no-root-mount 404, per-app base + state scope, duplicate-mount rejection,
normalization, SSR emits the mounted app's base.