# pathland-quarkus — implementation status

**Last updated:** November 8, 2026

Quarkus extension for Pathland: SSR at any path, live deltas over each
app's reserved `/{base}/ws`, per-session state. Adding this extension + a
`PathlandApp` CDI bean gives a running app; `MountedApp` beans host more apps at
their own subpaths (the BFF layout). Author the root view with
`pathland-view-core`'s Kotlin DSL; Java hosts return Kotlin views from a
`PathlandApp` bean. The extension also provides the `dev.pathland.quarkus.kotlin`
capability and pulls in `quarkus-kotlin`.

## Implemented

- **Dev UI (M1)** — the extension registers a **Pathland** card in the Quarkus Dev
  UI (local development only). A **Sessions** page lists every live session per
  mount (route, state scope, connection state, viewport, age,
  batches/opcodes/bytes/sequence, subscriptions, proxies, retained tree size) and
  offers resync (single/all) and close actions; the card links to the running app,
  `/q/health`, and `/q/metrics`, and shows a live app/session summary. Backed by
  read-only `PathlandHost`/`PathlandRegistry`/`PathlandSession` snapshots and the
  dev-only `PathlandDevUiRpc` (`overview`, `sessions`, `resync`, `resyncAll`,
  `closeSession`), registered via `JsonRPCProvidersBuildItem`. Both build steps
  are gated by `IsLocalDevelopment`, so production/native builds carry no card,
  component JS, or Dev UI bean. Verified in dev mode (the component is served at
  `/q/dev-ui/pathland-quarkus/qwc-pathland-sessions.js` and
  `pathland-quarkus_sessions` returns live sessions) and in a packaged prod app
  (`/q/dev-ui` is 404; health/metrics unchanged).

- **Security defaults (issue #63)**: the deployment module contributes
  overridable runtime defaults via `RunTimeConfigurationDefaultBuildItem`:
  `quarkus.http.cors.enabled=true` (no origins → same-origin, which covers
  WebSocket upgrades because the CORS filter runs before routes),
  `quarkus.websockets-next.server.max-message-size`/`max-frame-size=65536`, and
  CSP + `X-Content-Type-Options`/`Referrer-Policy`/`X-Frame-Options` headers via
  `quarkus.http.header.*`. The CSP default is contributed in **test and
  production only**: local dev omits it so the Quarkus Dev UI (inline import
  map/scripts + Google Fonts) can boot; an app can still set its own dev CSP.
  See `docs/HARDENING.md` for overrides, CSP rollout,
  reverse-proxy/TLS guidance, and edge rate limiting. The `wid` handshake query
  is validated by server-core (`WindowIds`). **HSTS is deliberately not an
  extension default** — it is a whole-origin, persistent browser policy that
  also covers other apps on the same server, so it is owned by the application
  or edge; the Quarkus demo opts in via `application.properties`.
  `docs/HARDENING.md` documents the directives and override guidance.
- **Hosted deployments work by default**: the same defaults set
  `quarkus.http.proxy.proxy-address-forwarding`, `allow-x-forwarded`, and
  `enable-forwarded-host` to `true`, so a TLS-terminating PaaS (Render, Fly,
  Heroku, Cloud Run) gets a correct same-origin WebSocket check and an external
  SSR canonical URL with zero configuration. These are lowest-priority defaults;
  application config (including
  `quarkus.http.proxy.proxy-address-forwarding=false`) always wins.
- **Topic hub bean**: the extension produces a `PathlandSignalHub` singleton
  (`host.signalHub()`: root mount, else the first mount) for external UI updates.
  `TopicSubscriptionsTest` mounts a `/topics` app and proves a background-thread
  publish reaches only the session subscribed for that subject.
- **Java host / Kotlin views**: `CounterApp` is a Java CDI bean whose
  `newRoot()` returns a Kotlin-authored fixture view; the multi-session
  WebSocket tests prove SSR + live deltas work through that mount.

- **Crawler endpoints**: `/robots.txt` (text/plain) and `/sitemap.xml` (application/xml) are real routes before the SPA catch-all, so Lighthouse does not parse the HTML shell as robots.txt.
- **SEO document head (issue #45)**: `IndexResource` derives the canonical URL
  from `UriInfo` and passes host-default `PageMetadata` to the registry;
  `PathlandApp.pageMetadata(...)` may override it per route before the first
  paint. Canonical/OG/Twitter/JSON-LD are on by default and configurable via
  `pathland.seo.{enabled,site-name,image,og-type,twitter-card,twitter-site,structured-data}`.

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
  **The extension jar ships the DOM client bundle** (`META-INF/resources/_pathland/
  dom-renderer-<hash>.js` + the `dom-renderer.current` pointer + the service worker), so
  a consuming application adds the extension and needs no static resources of its own;
  the mounted-base `frameworkFile` path resolves the app's classpath copy first (TCCL),
  then the extension jar, and adds `immutable` caching for the content-hashed bundle. A
  `/_pathland/{file}` JAX-RS route is a fallback for deployments where Quarkus's static
  handler is unavailable (it normally wins).
  **`/_pathland/{name}.css`** (global JAX-RS route + the mounted-base
  `frameworkFile` path) serves the renderer-owned content-hashed base design
  (`HtmlRenderer.baseCss()` at `baseCssName()`) as `text/css` with
  `Cache-Control: public, max-age=31536000, immutable`. **`/_pathland/icons/{name}.svg`**
  serves the renderer-owned icon glyphs from the shared Rust renderer
  (`HtmlRenderer.iconSvg` → `pathland_html_icon_svg`) with an `immutable` cache
  header — no static CSS/icon files on any host. SSR links the CSS via
  `PathlandSession` → `renderLinked`.
- **`PathlandRegistryProducer`** — CDI `@Produces @Singleton` `PathlandHost` from every
  `MountedApp` bean plus the lone `PathlandApp` bean (mounted at `/`); `@Disposes` shuts
  it down. Reads the `pathland.debug-html` config property (default `false`) to enable
  per-node HTML comments in SSR output, and `pathland.service-worker` (default `false`)
  to opt the DOM client into the per-window-id service worker (issue #155) — served at
  `{base}/sw.js` with `Service-Worker-Allowed: /` + `Cache-Control: no-cache`.
- **`ServiceWorkerFilter`** — serves `/_pathland/sw.js` (root and mounted bases) with
  `Service-Worker-Allowed: /` + `Cache-Control: no-cache`, so the client's root-scoped
  registration succeeds. A `Filters` handler always runs before routes, so it wins over
  Quarkus's static-resource handler (which sends neither header, and whose configured
  max-age would otherwise cache the script and delay update detection). The script is
  resolved through the thread-context classloader first, then the extension's own
  loader, then the system loader: extension runtime classes live in Quarkus's base
  runtime classloader and cannot see the application's `META-INF/resources` copy. The
  extension jar bundles the worker as a fallback (the TS copy script writes it), and
  `pathland-quarkus-demo`'s integration test asserts the headers on both the root and a
  mounted base — the packaged-app classloader path the module-local test could not see.
- **Observability (issue #62)** — `quarkus-micrometer` (a `MeterRegistry` bean) +
  `quarkus-smallrye-health`: the producer builds a `MicrometerTelemetry` over the
  registry and decorates it with `TracingTelemetry` when a `Tracer` bean is present and
  `quarkus.otel.enabled` (default `true`; the extension never touches the OTel API when
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
- `META-INF/beans.xml` so Quarkus discovers the extension.
- **Native image support** — the deployment module registers the renderer-owned
  `dev/pathland/render/html/base.css` + `.name` resources via
  `NativeImageResourceBuildItem`, so `HtmlRenderer.baseCss()` works in a GraalVM
  native executable without app-level `quarkus.native.resources.includes`.
  JNA is not on the server classpath (the ring interop lives in
  `pathland-native-core`), and Redis/Lettuce is opt-in via
  `pathland-state-redis`, so the demo's native image carries neither.
  The demo's Dockerfile builds with the Mandrel 25 builder image and runs the
  resulting executable on the UBI9 Quarkus micro image.
- **Quarkus extension metadata** — runtime + `pathland-quarkus-deployment`; the
  runtime provides the `dev.pathland.quarkus` capability. The runtime has no
  dependency on the authoring DSL. Tests use Kotlin-authored fixture views to prove
  a Java CDI bean can mount them.

## App DX

```java
@ApplicationScoped
public class MyApp {
    @Produces @Singleton PathlandApp main() { return () -> new MyHomeView(); }
    @Produces @Singleton MountedApp support() { return MountedApp.of("/support", ...); }
}
```

## Verified by

Manual: the `pathland-quarkus-demo` renders `/` (the music player, whose Now
Playing pane embeds the small `/ad` app via a **same-server `ProxyView`**,
spec/PROXY.md) and `/ad` (the ad app); a WebSocket handshake to `/_pathland/ws`
and `/ad/_pathland/ws` returns 101. `ProxyDemoTest` (`@QuarkusTest`) asserts
`GET /` embeds the `/ad` app's subtree with remapped node ids
(`data-pathland-id="1073741825"` — proxy block 0 + origin id 1). Core behavior is
covered by `pathland-server-core` tests. `MultiSessionWebSocketTest` (`@QuarkusTest`) opens **100 concurrent real WS
sessions** against `/_pathland/ws` (via the shared `pathland-server-core` test-jar
probe) and asserts each connects, re-syncs, gets transport-level PONGs, stays isolated,
and is never closed by the peer.
`PathlandObservabilityTest` (`@QuarkusTest`) asserts `/q/health` reports the Pathland
check UP and `/q/metrics` exposes the `pathland.*` namespace. `BaseCssTest`
(`@QuarkusTest`) asserts the linked SSR `<link>`, the immutable `text/css`
response at the global and mounted bases, and a 404 for an unknown hash.
`PathlandDevUiRpcTest` (unit, over a real `PathlandHost`) asserts the overview,
the live session list, resync (single + all), and close actions. Dev-mode manual
verification confirms the Dev UI card/component registration and a live
`pathland-quarkus_sessions` response; a packaged prod app serves `/q/dev-ui` as
404 with health/metrics unchanged.