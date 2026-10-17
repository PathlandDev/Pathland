# pathland-server-core — implementation status

**Last updated:** October 15, 2026

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
  `app.newRoot().modifiers(environmentBinding(e -> e.key(ACTIVE_PATH).value(activePath)))`, **delta-batched send-on-`endFrame`**
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
  of the Rust transport's `Batcher`. It owns the per-session **message sequence**
  (stamped on each flushed batch via `FrameCodec.encodeFrame(frame, sequence)`) so the
  DOM client can detect a lost batch and recover with `META::RESYNC`
  (spec/OPCODE.md §Sequence gap detection); an empty flush consumes no sequence. The
  **sender runs OUTSIDE the monitor** — a blocking/queueing send can never stall the
  actor thread's `append` or the flush scheduler. Coalescing tests cover merging +
  rebasing, a 100-frame burst collapsing to one send, incrementing sequences, and a
  blocking sender not holding the lock.
- **`PathlandConnection`** — the transport seam (`send(byte[])`/`isOpen()`); each starter
  adapts its WebSocket type to it.
- **Observability seam** — `PathlandTelemetry` (default `NOOP`): the runtime emits
  session open/close, inbound event batches, flushed batches (frames/opcodes/bytes +
  merge-encode time), SSR render latency, resync requests, and send failures; the host
  binds a pull-based active-sessions gauge. `PathlandHost`/`PathlandRegistry`/
  `PathlandSession`/`DeltaBatcher` thread it through (a host with no telemetry pays
  nothing). `pathland-observability` provides the Micrometer + OpenTelemetry adapters;
  the starters auto-wire them. Logging is **SLF4J** (both frameworks bind it) — the old
  ad-hoc `System.out` is gone. See issue #62.
- **Concurrency stress** — `RegistryConcurrencyTest`: 100 sessions driven through the
  registry concurrently (clicks on each session's button) while SSR-style requests
  construct throwaway sessions on request threads — the shared-actor / global-`Scheduler`
  cross-session corruption guard. Each session must reach exactly its own click count
  (no lost effects, no cross-session values).
- **Shared WS test harness** — `dev.pathland.server.test.{WsSession,MultiSessionProbe}`
  (a JDK-WebSocket client speaking the PLPL protocol: env/resync/click/ping) lives in the
  **`pathland-test-kit`** module (main scope, so package builds resolve it as a normal
  jar); the starters' 100-session stress tests reuse it.
- **`StateStores`** — default state store: Redis when reachable, else the supplied
  fallback.
- **SSR debug comments (property-gated)**: `PathlandSession`/`PathlandRegistry` take a
  `debugHtml` flag (constructor + `isDebugHtml()`); when set, `renderHtml` uses the
  renderer's debug mode so every rendered node is prefixed with an HTML comment naming
  its component and the modifiers applied. The Spring/Quarkus starters bind it to the
  `pathland.debug-html` property (default `false`).
- **Reserved framework root**: `PathlandSession.PATHLAND_BASE` (`/_pathland`) — the
  SSR page carries the app's base as `data-pathland-base` and the DOM-client bundle is
  injected at `{base}/dom-renderer-<sha256>.js` (`PathlandAssets` reads the current
  name from the `_pathland/dom-renderer.current` manifest on the classpath, falling
  back to the literal `dom-renderer.js` when absent) so the client's WebSocket and
  asset refs resolve from the same reserved prefix (spec DSL.md). The content-hashed
  name busts the immutable asset cache with a fresh URL per build. Multi-app hosts
  give each mount its own base.
- **Per-window-id service worker (opt-in, issue #155)**: `PathlandHost` carries a
  host-level `serviceWorker` flag (default `false`; one registration covers the
  whole origin) threaded to every `PathlandRegistry`; `PathlandSession.renderHtml`
  emits `data-pathland-service-worker="true"` on `<html>` when set, and the DOM
  client registers `{base}/sw.js` (root scope) / unregisters when absent (the kill
  switch). `PathlandRegistry.serviceWorkerUrl()` names the served script.

## Not implemented / gaps

- No multi-node/shared sessions (each server owns its sessions in-memory).
- Quarkus mounted-app WebSocket endpoints require a **single-segment** mount path
  (the `/{app}` path param); Spring supports any depth.

## Proxy views (spec/PROXY.md) — M1 (live)

- **`proxy.ProxyIdMap`** — node-id namespacing: the reserved high range
  `[0x4000_0000, 0x8000_0000)`, 1024 blocks of `2^20`, the block math, and the
  opcode remap in both directions (`TREE` node ids + the node-id-bearing
  `PARAMETER` ops `SET_TEXT`/`SET_PROPERTY`/`SET_DATE` on ingress; `EVENT` targets
  on egress; `META` untouched). `SET_DESIGN_TOKEN` is **not** remapped — its `A`
  is a token-path arena offset, not a node id (remapping it produced a huge
  out-of-bounds string offset → the client's RESYNC loop; regression-tested).
  Pure + unit-tested.
- **`proxy.ProxyTransport`** + **`proxy.JdkWebSocketTransport`** — the outbound
  connection seam (default: the JDK `HttpClient` WebSocket client, no new
  dependency) with a `Factory` so a starter can swap a native client.
- **`proxy.ProxySession`** — one origin connection: sends `META::ENVIRONMENT` +
  `META::RESYNC`, **remaps** the origin snapshot into the proxy's block and
  attaches the origin root under the proxy node, forwards live deltas remapped,
  routes client events back (target unmapped), replies `PONG` to origin `PING`,
  translates origin `META::RESET` into a **scoped** subtree teardown (never a
  global reset), tears the subtree down on disconnect (the origin session is
  per-connection), and reconnects with exponential backoff. The service
  credential + a cycle-guard instance id ride the handshake headers.
  **Backpressure** (spec/PROXY.md §8): a "chatty origin" breaker (a per-second
  batch cap → drop + `RESYNC`) and event coalescing (pointer-moves coalesce by
  target; discrete events flush immediately).
- **`proxy.ProxyHost`** — one `ProxySession` per `PROXY_VIEW` node, each with its
  own block; routes inbound events whose target is in a proxy range to the owning
  origin connection; owns the proxy scheduler. `reconcile(wanted)` starts a
  session for every `PROXY_VIEW` node and closes the session of every node that
  left the tree; released id blocks go to a **free-block pool** and are reused, so
  repeated removal/re-add cycles never exhaust the 1024-block range.
- **`PathlandSession`** integration — captures `RenderResult.proxyViews()` and
  registers the emitter's structural-reconcile callback
  (`Emitter.onStructureReconciled`) to `reconcileProxies()`, so a proxy lives
  **exactly while its node is in the retained tree** (spec/PROXY.md §7): a
  `SizeThatFits` slot dropping the candidate that held it closes the origin
  connection, and re-adding it opens a fresh one (re-seeded by `META::RESYNC`).
  The host's frames feed the session's `DeltaBatcher` (a single unified sequence);
  `dispatch` splits proxy-range events to the origin and everything else to the
  app's own bindings; `close` tears the proxies down. `proxyTransportFactory(...)`
  lets a host supply a framework transport.
- **`PathlandTelemetry`** — `proxyConnected`/`proxyDisconnected`/`proxyBatchDropped`/
  `proxyResyncRequested` (all default no-ops).

**SSR embed (M2).** `renderHtml` starts the proxies and **blocks** for each
origin's snapshot (`ProxySession.awaitSnapshot`, bounded by a 2s timeout), then
splices the remapped origin frames into the session's mount frame
(`FrameMerger`, reusing the batcher's string rebasing via `FrameRebase`) so the
renderer emits **one** document — the origin subtree plus its fit band rules in
the same head. The origin's **global** token overrides (`SET_DESIGN_TOKEN`) are
**dropped** (live + SSR): a proxied app renders under the **host's** theme, while
its token *references* resolve against that theme (spec/PROXY.md §9). A proxy
whose origin does not answer in time
renders empty (a placeholder) and hydrates over the live connection. A client
`META::RESYNC` also replays A's tree **and** asks every origin for a fresh
snapshot (`ProxyHost.resyncAll`).

**Gaps (M3):** nested proxies and per-proxy theme scoping are deferred (the
origin's global overrides are dropped, not scoped, spec/PROXY.md §9). A tiny
orphan window remains on removal: an origin batch
already queued in the `DeltaBatcher` (≤20 ms) when the proxy node is removed can
still create an unattached element in the client's `byId` (not in the DOM);
eliminating it needs the `ProxySession` to track the ids it emitted and `DELETE`
them on close.

## Verified by

`mvn test` — `PathlandSessionTest`: mounts + routes taps through the binding maps,
`applyEnvironment`/`resync`/`close` idempotent, registry SSR + lifecycle/teardown, and
**two windows with the same `wid` still get separate sessions** (a tap to one never
reaches the other).
`PathlandHostTest`: longest-prefix dispatch + route stripping, mount-boundary
respect, no-root-mount 404, per-app base + state scope, duplicate-mount rejection,
normalization, SSR emits the mounted app's base.
`PathlandTelemetryTest`: a recording telemetry double asserts the registry emits
session/event/resync/flush/SSR/close events, the host binds the active-sessions
gauge, a failed send reports `connectionFailed`, and `DeltaBatcher` reports the
flushed batch.
`proxy.ProxyIdMapTest`: the reserved range, block round-trips, overflow rejection,
and the opcode remap (TREE/PARAMETER node ids remapped, APPEND/META untouched,
EVENT targets unmapped).
`proxy.ProxySessionTest` (a fake origin transport): the ENVIRONMENT+RESYNC
handshake + handshake headers, snapshot remap + root attachment, delta remap with
the string section passed through verbatim, event egress unmapped, pointer-move
coalescing, `META::RESET` → scoped teardown, disconnect teardown, and the
chatty-origin drop+resync breaker.
`proxy.ProxyIntegrationTest`: a `ProxyView` in the app tree opens an origin
connection on `connect`, the remapped origin snapshot reaches the client (with the
root attached), a client event in the proxy range routes back to the origin
unmapped, and the SSR document embeds the origin subtree (remapped ids) in one
render.
`FrameMergerTest`: splicing a proxy's origin frame into an SSR frame rebases its
string offsets (SET_TEXT / LIST / STRING operands) and returns the base unchanged
when there is nothing to append.