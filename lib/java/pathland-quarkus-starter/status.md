# pathland-quarkus-starter — implementation status

**Last updated:** October 3, 2026

Quarkus integration for Pathland: SSR at any path, live deltas over each app's reserved
`/{base}/ws`, per-session state. Adding the starter dependency + a `PathlandApp` CDI
bean gives a running app; `MountedApp` beans host more apps at their own subpaths
(the BFF layout).

## Implemented

- **`PathlandSocket`** — `@WebSocket("/_pathland/ws")` for the root app: 1:1 sessions
  per connection (a fresh `uiId` per connection + the client's `wid` state scope from
  the `?wid=` query param), binary events routed into the root registry of the
  `PathlandHost`. **Stateless (critical)**: quarkus-websockets-next endpoints default
  to `@Singleton` — one shared instance for all connections — so the per-connection
  ids come from `connection.id()` + the handshake and the `QuarkusConnection` adapters
  live in a map keyed by connection id. Memoizing `uiId`/`wid`/`transport` on the
  shared instance collapsed every connection onto ONE session (multiple browsers shared
  one UI model — a probe or second tab drove the first tab's session).
- **`MountedPathlandSocket`** — `@WebSocket("/{app}/_pathland/ws")` for mounted apps:
  the `{app}` path param resolves the app's registry. Mount paths are single-segment.
  Same stateless rule as the root socket.
- **`IndexResource`** — JAX-RS SSR catch-all (`/` + deep links; `/_pathland/**` and the
  Quarkus non-application root `/q/**` — health/metrics — excluded via negative lookahead)
  dispatching to the longest-mount app (prefix stripped);
  SSR renders defaults (no session cookie — per-window state arrives over the WS);
  per-app framework files (`/{app}/_pathland/...`, e.g. the mounted app's bundle) are
  served from the shared `/_pathland/**` classpath mount. Quarkus serves the global
  `/_pathland/**` bundle + asset mount from `META-INF/resources` before JAX-RS.
- **`PathlandRegistryProducer`** — CDI `@Produces @Singleton` `PathlandHost` from every
  `MountedApp` bean plus the lone `PathlandApp` bean (mounted at `/`); `@Disposes` shuts
  it down. Reads the `pathland.debug-html` config property (default `false`) to enable
  per-node HTML comments in SSR output.
- **Observability (issue #62)** — `quarkus-micrometer` (a `MeterRegistry` bean) +
  `quarkus-smallrye-health`: the producer builds a `MicrometerTelemetry` over the
  registry and decorates it with `TracingTelemetry` when a `Tracer` bean is present and
  `quarkus.otel.enabled` (default `true`; the starter never touches the OTel API when
  the extension is absent/disabled). `PathlandHealthCheck` (`@Readiness`) reports the
  host UP with `activeSessions`/`mounts` at `/q/health`; the `pathland.*` meters appear
  at `/q/metrics`.
- **`QuarkusConnection`** — adapts `WebSocketConnection` to `PathlandConnection`.
  Sends are **serialized** (one `sendBinary` subscription in flight, the rest queued),
  and a **send failure closes the connection** so the session drops it and the DOM
  client reconnects + `META::RESYNC` (recoverable) instead of silently stalling on an
  "open" socket that never delivers (the remote seekbar symptom). **Send timeout**: a
  `sendBinary` that neither completes nor fails within 10 s (a half-dead connection /
  a dropped proxy upstream leg) is marked failed and closed — a hung send would
  otherwise wedge the serialized drain forever. **Keep-alive pings**: the connection
  sends a WS-protocol ping every 15 s (browsers auto-pong) to keep the connection warm
  through middleboxes and detect a dead client; `close()` stops the keep-alive
  scheduler (the sockets' `@OnClose` calls it).
- **Heartbeat** — both sockets reply to a client `META::PING` with a `META::PONG`
  **at the transport layer** (through their `QuarkusConnection`, no actor/session
  dependency, and a PING never creates or wakes a session); spec/OPCODE.md
  §Transport heartbeat.
- **Failure handling** — `fail()` logs the reason; `close()` (socket `@OnClose`)
  no longer calls `close()` on the already-closed connection (no spurious
  "Connection already closed" noise). A **transient write failure on a connection
  that still reports open is logged, not closed** — only a confirmed-dead channel
  (or a send hung past the timeout) warrants closing. The send-timeout task is
  **cancelled when the send completes**, so a completed-but-late callback can't
  false-time-out and kill a healthy connection. Keep-alive pings are sent directly
  (Vert.x serializes writes — no sync with `sendBinary` needed).
- `META-INF/beans.xml` so Quarkus discovers the starter.

## App DX

```java
@ApplicationScoped
public class MyApp {
    @Produces @Singleton PathlandApp main() { return () -> new MyHomeView(); }
    @Produces @Singleton MountedApp support() { return MountedApp.of("/support", ...); }
}
```

## Verified by

Manual: the `pathland-quarkus-demo` renders `/`, `/kitchen`, `/settings` and the 404
fallback **and a second app at `/app2`**; a WebSocket handshake to `/_pathland/ws` and
`/app2/_pathland/ws` returns 101. Core behavior is covered by `pathland-server-core`
tests. `MultiSessionWebSocketTest` (`@QuarkusTest`) opens **100 concurrent real WS
sessions** against `/_pathland/ws` (via the shared `pathland-server-core` test-jar
probe) and asserts each connects, re-syncs, gets transport-level PONGs, stays isolated,
and is never closed by the peer.
`PathlandObservabilityTest` (`@QuarkusTest`) asserts `/q/health` reports the Pathland
check UP and `/q/metrics` exposes the `pathland.*` namespace.