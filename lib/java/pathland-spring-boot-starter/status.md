# pathland-spring-boot-starter — implementation status

**Last updated:** November 7, 2026

Spring Boot auto-configuration for Pathland: SSR at any path, live deltas over each
app's reserved `/{base}/ws`, per-session state. Adding the starter dependency + a
`PathlandApp` bean gives a running app; `MountedApp` beans host more apps at their own
subpaths (the BFF layout).

## Implemented

- **Security hardening (issue #63)**: the WebSocket handlers no longer hardcode
  `setAllowedOrigins("*")`. The default is Spring's same-origin handshake check
  (the WebSocket CSRF control); cross-origin hosts must be listed explicitly in
  `pathland.allowed-origins` (comma-separated). The handshake `wid` is validated
  by server-core (`WindowIds`). SSR inherits SRI (`integrity="sha384-…"`) from
  `PathlandSession`. CSP/security headers and transport limits are documented in
  `docs/HARDENING.md` (Quarkus supplies secure defaults; Spring apps add them
  through Spring Security/server configuration).
- **Hosted deployments work by default (issue #45 follow-up)**: a
  lowest-precedence `EnvironmentPostProcessor` contributes
  `server.forward-headers-strategy=framework`, so a TLS-terminating PaaS gets a
  correct WebSocket same-origin check and external SSR canonical URL with zero
  configuration. An explicit `server.forward-headers-strategy` in application
  config always wins (`ForwardedHeaderOverrideTest` pins this).
- **Topic hub bean**: `PathlandAutoConfiguration` produces a `PathlandSignalHub`
  bean (`host.signalHub()`: root mount, else the first mount) for external UI
  updates. `TopicSubscriptionsTest` mounts a `/topics` app and proves a
  background-thread publish reaches only the session subscribed for that subject.
- **Java host / Kotlin views**: `KotlinViewMountTest` proves a Java-only
  `@SpringBootApplication` bean returning `PathlandApp` mounts a Kotlin-authored
  fixture view, renders it through SSR, and receives a live signal delta.

- **Crawler endpoints**: `/robots.txt` (text/plain) and `/sitemap.xml` (application/xml) are real routes before the SPA catch-all, so Lighthouse does not parse the HTML shell as robots.txt.
- **SEO document head (issue #45)**: the SSR controller derives the canonical
  URL from the request and passes host-default `PageMetadata` to the registry;
  `PathlandApp.pageMetadata(...)` may override it per route before the first
  paint. Canonical/OG/Twitter/JSON-LD are on by default and configurable via
  `pathland.seo.{enabled,site-name,image,og-type,twitter-card,twitter-site,structured-data}`.

- **`PathlandAutoConfiguration`** (`@AutoConfiguration`, `@EnableWebSocket` since Spring
  Boot 3.5 no longer auto-enables it) + `META-INF/spring/…/AutoConfiguration.imports`:
  - default `StateStore` bean (`@ConditionalOnMissingBean` — Redis-or-in-memory),
  - `PathlandHost` bean (shut down with the context): every `MountedApp` bean plus the
    lone `PathlandApp` bean mounted at `/` (unless an explicit `MountedApp` claims the
    root). Without any app the host is empty and the starter is inert.
  - a `WebSocketConfigurer` registering **one handler per app** at its framework base
    (`/_pathland/ws` for the root, `/{mount}/_pathland/ws` otherwise). Each connection
    gets a fresh `uiId` (its UI model) + the client's `wid` (per-window state scope)
    from the WS `?wid=` query param (kept in `sessionStorage`, never in the page URL),
  - `PathlandIndexController` (SSR catch-all dispatching to the longest-mount app with
    the prefix stripped + a framework static controller serving the bundle + asset mount
    from `classpath:/static/_pathland/**` at both `/_pathland/**` and `/{app}/_pathland/**`;
    the more-specific mapping beats `/{*path}`, and the `!Upgrade` header excludes WS).
    **The starter jar ships the DOM client bundle** (`static/_pathland/dom-renderer-<hash>.js`
    + the `dom-renderer.current` pointer + the service worker), so a consuming application
    adds the dependency and needs no static resources of its own; an app-supplied
    `static/_pathland` copy still wins by classpath order.
    When no static file matches, the controller serves renderer-owned assets from the
    shared Rust renderer: the content-hashed base stylesheet
    (`HtmlRenderer.baseCss()` at `baseCssName()`) as `text/css` with
    `Cache-Control: public, max-age=31536000, immutable`, and
    `/_pathland/icons/{name}.svg` glyphs (`HtmlRenderer.iconSvg` →
    `pathland_html_icon_svg`) with an `immutable` cache header — no static CSS/icon
    files on any host, and a host can still override either with a file. SSR links
    the CSS via `PathlandSession` → `renderLinked`.
    The framework controller honors byte-range requests (`206 Partial Content` +
    `Content-Range`, `416` for unsatisfiable ranges, `Accept-Ranges` on the full
    response) — a `<video>` (a `MediaController`'s playback) seeking beyond its
    buffer issues a `Range`
    request, and answering it with a full `200` would make browsers restart the media
    at byte 0.
    SSR renders defaults (no session cookie — per-window state arrives over the WS).
- **SSR debug comments**: the host reads the `pathland.debug-html` property
  (`@Value("${pathland.debug-html:false}")`) and forwards it to each registry, enabling
  per-node HTML comments in SSR output when set.
- **Per-window-id service worker (opt-in, issue #155)**: `pathland.service-worker`
  (default `false`) forwards to the host; the SSR emits `data-pathland-service-worker`
  and the controller serves `{base}/sw.js` with `Service-Worker-Allowed: /` +
  `Cache-Control: no-cache` (a stable, uncached script the browser byte-compares).
- **`SpringConnection`** — adapts `WebSocketSession` to `PathlandConnection`.
  `AutoCloseable`: a failed send surfaces as a close (the session's
  `closeConnection` closes the underlying session) so the DOM client reconnects +
  `META::RESYNC` instead of silently stalling on an open socket.
- **Heartbeat** — the socket replies to a client `META::PING` with a `META::PONG`
  **at the transport layer** (through its `SpringConnection`, no actor/session
  dependency; spec/OPCODE.md §Transport heartbeat).
- **Observability (issue #62)** — `spring-boot-starter-actuator` supplies the
  `MeterRegistry`; auto-config builds a `MicrometerTelemetry` over it (decorated with
  `TracingTelemetry` when an `OpenTelemetry` bean is present — add
  `micrometer-tracing-bridge-otel` to opt in) and a `HealthIndicator` reporting the host
  UP with `activeSessions`/`mounts` at `/actuator/health`. The `pathland.*` meters are
  exposed at `/actuator/prometheus` (add `micrometer-registry-prometheus`).

## App DX

```java
@SpringBootApplication
public class MyApp {
    public static void main(String[] args) { SpringApplication.run(MyApp.class, args); }
    @Bean PathlandApp pathlandApp() { return () -> new MyHomeView(); }
    @Bean MountedApp support() { return MountedApp.of("/support", ...); } // optional 2nd app
}
```

## Verified by

`mvn test` — `PathlandAutoConfigurationTest` (`@SpringBootTest`): a `PathlandApp` bean
mounts at `/` and SSR renders the root. `MultiSessionWebSocketTest`
(`@SpringBootTest(RANDOM_PORT)`) opens **100 concurrent real WS sessions** against
`/_pathland/ws` (via the shared `pathland-server-core` test-jar probe) and asserts each
connects, re-syncs, gets transport-level PONGs, stays isolated, and is never closed by
the peer. `PathlandObservabilityTest` (`@SpringBootTest` + `MockMvc`) asserts
`/actuator/health` reports the `pathland` component UP and the auto-configured
`PathlandTelemetry` is a `MicrometerTelemetry` registering `pathland.*` meters.
`BaseCssHeadersTest` (`@SpringBootTest` + `MockMvc`) asserts the linked SSR
`<link>`, the immutable `text/css` response at the global and mounted bases, and
a 404 for an unknown hash.
Manual: the `pathland-spring-boot-demo` renders `/`, `/kitchen`, `/settings`
and the 404 fallback **and a second app at `/app2`**; a WebSocket handshake to
`/_pathland/ws` and `/app2/_pathland/ws` returns 101.