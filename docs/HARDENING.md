# Hardening Pathland deployments

Pathland has two distinct security surfaces:

1. **The protocol/client code** (`pathland-core`, `pathland-view-core`,
   `@pathland/dom-renderer`) — malformed opcodes must never panic, over-allocate,
   or execute markup/script. These bounds are fixed in code and covered by tests;
   reporting guidance lives in [SECURITY.md](../SECURITY.md).
2. **The HTTP/WebSocket host** — origin checks, response headers, message-size
   limits, and rate limiting. These are framework/deployment concerns and are
   configured through Quarkus or Spring Boot.

This guide is Quarkus-first (the primary server target) with a Spring Boot
appendix.

## Pathland defaults never override your configuration

Pathland contributes framework defaults only, never programmatic overrides:

- **Quarkus** uses `RunTimeConfigurationDefaultBuildItem`, which becomes a
  lowest-priority `DefaultValuesConfigSource` (ordinal `Integer.MIN_VALUE`).
  `application.properties`, profile-specific files, environment variables, and
  system properties all win.
- **Spring Boot** contributes `server.forward-headers-strategy=framework` through
  a lowest-precedence `EnvironmentPostProcessor` property source. An explicit
  strategy in your configuration wins.

Adding Pathland can still change behavior when a setting was previously **unset**
(that is what a default is): CORS, forwarded-header trust, WebSocket caps, and
the baseline security headers. All are listed above and every one can be
overridden with normal application configuration.

## What Pathland does not use

- **No session cookie.** The per-window state scope (`wid`) lives in
  `sessionStorage` and is sent only on the WebSocket query string (and, when
  enabled, attached to navigations by the service worker). There is therefore no
  cookie to mark `HttpOnly`/`Secure`/`SameSite`.
- **No CSRF token.** SSR is read-only (`GET`), and the only state-changing
  channel is the WebSocket. WebSocket CSRF is prevented by the origin check
  below (browsers always send `Origin` on a WebSocket handshake).

## Quarkus (secure by default)

The `pathland-quarkus` extension contributes these runtime defaults via
`RunTimeConfigurationDefaultBuildItem`; application configuration always wins.

```properties
quarkus.http.cors.enabled=true
quarkus.http.proxy.proxy-address-forwarding=true
quarkus.http.proxy.allow-x-forwarded=true
quarkus.http.proxy.enable-forwarded-host=true
quarkus.websockets-next.server.max-message-size=65536
quarkus.websockets-next.server.max-frame-size=65536
quarkus.http.header."Content-Security-Policy".value=default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; media-src 'self'; font-src 'self'; connect-src 'self' ws: wss:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; worker-src 'self'
quarkus.http.header."X-Content-Type-Options".value=nosniff
quarkus.http.header."Referrer-Policy".value=no-referrer
quarkus.http.header."X-Frame-Options".value=DENY
```

### WebSocket origin / CSRF

Quarkus's CORS filter runs before every route, including WebSocket upgrades.
With `cors.enabled=true` and **no `origins` configured**, it allows same-origin
requests only (missing `Origin` is allowed — non-browser clients). This is the
WebSocket CSRF control.

If browsers must connect from another origin, list them explicitly:

```properties
quarkus.http.cors.origins=https://app.example.com,https://staging.example.com
```

Never use `quarkus.http.cors.origins=*` for a stateful Pathland app. Quarkus has
one global CORS policy, so this also applies to non-WebSocket HTTP endpoints:
list any legitimate cross-origin API consumers, or set
`quarkus.http.cors.enabled=false` and enforce the origin check in your own
gateway/filter.

### CSP

The default policy allows same-origin scripts only. Inline **styles** are
unavoidable because the protocol emits `style` attributes and
`<style data-pathland-tokens>` / `data-pathland-fit` blocks, so `style-src`
includes `'unsafe-inline'`; scripts do not.

Override the policy (or relax it while rolling out) with application config:

```properties
quarkus.http.header."Content-Security-Policy".value=default-src 'self'; script-src 'self'; ...
```

For an initial rollout you can use `Content-Security-Policy-Report-Only`
instead. Do not add `'unsafe-eval'` or `'unsafe-inline'` to `script-src`.

### Transport limits

- The extension pins WebSocket messages/frames to 64 KiB, matching the
  server-core inbound event-batch bound.
- Add a server-wide connection cap for a coarse DoS guard:

```properties
quarkus.http.limits.max-connections=1000
```

- Per-connection event batches and bytes are rate-limited in server-core
  (`pathland.max-inbound-batches-per-second`, default 500;
  `pathland.max-inbound-bytes-per-second`, default 2 MiB). Exceeding either
  drops the batch and increments `pathland.inbound.dropped`.
- For volumetric abuse, rate-limit at the edge (see below).

### Hosted deployments (works by default)

Pathland trusts the platform's forwarded headers by default so a TLS-terminating
PaaS (Render, Fly.io, Heroku, Cloud Run, …) works with zero configuration. The
platform sends plain HTTP to the container with `X-Forwarded-Proto: https` and
`X-Forwarded-Host: <public-host>`; Pathland uses them for:

- the WebSocket same-origin check (otherwise the browser's `https` origin never
  matches the internal `http` hop, and the handshake is rejected with 403);
- the SSR canonical / Open Graph URLs.

```properties
quarkus.http.proxy.proxy-address-forwarding=true   # default
quarkus.http.proxy.allow-x-forwarded=true          # default
quarkus.http.proxy.enable-forwarded-host=true      # default
```

These are **defaults only** (Quarkus `RunTimeConfigurationDefaultBuildItem`).
Every application setting in `application.properties`, a profile, environment
variables, or system properties takes precedence. Pathland never overrides an
explicit value.

### Directly exposed deployments

Trusting forwarded headers is safe when the container is only reachable through
the platform proxy (the normal hosted topology). If the app is directly exposed
to the internet, disable forwarding or restrict which proxies are trusted:

```properties
# Option A: ignore forwarded headers entirely
quarkus.http.proxy.proxy-address-forwarding=false

# Option B: only trust private/loopback proxies
quarkus.http.proxy.trusted-proxies=10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.1/32,::1/128
```

If you terminate TLS yourself (or at a proxy that does not set the headers),
leave forwarding disabled. Ensure any edge proxy strips client-supplied
`Forwarded` / `X-Forwarded-*` headers.

### Reverse proxy / TLS

Terminate TLS at the proxy and forward `X-Forwarded-Proto` / `X-Forwarded-Host`.
Enable Quarkus's proxy forwarding only when the proxy is trusted (it is enabled
by default for hosted platforms, as described above).

```properties
quarkus.http.proxy.proxy-address-forwarding=true
quarkus.http.proxy.allow-x-forwarded=true
quarkus.http.proxy.enable-forwarded-host=true
```

## Subresource Integrity

The SSR layer reads `dom-renderer.current`, which now has two lines:

```text
dom-renderer-<sha256-12>.js
sha384-<base64>
```

The script tag is emitted with `integrity="sha384-…"`. The bundle is same-origin,
content-hashed, and served `immutable`; SRI is defense-in-depth. If the second
line is absent (an older manifest), no `integrity` attribute is emitted.

Regenerate the bundle and manifests after a web-client change:

```bash
cd lib/typescript && npm run build:all
```

## Spring Boot appendix

The Spring Boot starter is supported, but Quarkus owns the secure defaults.
At minimum, set the WebSocket origin policy explicitly:

```properties
# Same-origin is the default when this is unset (Spring's handshake check).
# List explicit cross-origin hosts; never use '*'.
pathland.allowed-origins=https://app.example.com
```

Add CSP and the standard headers through Spring Security (or a small app-level
`OncePerRequestFilter`). Example security config:

```java
http.headers(headers -> headers
    .contentSecurityPolicy(csp -> csp.policyDirectives(
        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
        + "img-src 'self' data:; media-src 'self'; font-src 'self'; "
        + "connect-src 'self' ws: wss:; object-src 'none'; base-uri 'self'; "
        + "form-action 'self'; frame-ancestors 'none'; worker-src 'self'"))
    .frameOptions(frame -> frame.deny())
    .referrerPolicy(referrer -> referrer.policy(
        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
    .contentTypeOptions(options -> {}));
```

Limit WebSocket payloads with a `ServletServerContainerFactoryBean`:

```java
@Bean
ServletServerContainerFactoryBean webSocketContainer() {
    var container = new ServletServerContainerFactoryBean();
    container.setMaxBinaryMessageBufferSize(64 * 1024);
    container.setMaxTextMessageBufferSize(64 * 1024);
    return container;
}
```

Configure Tomcat/server connection limits as needed (for example
`server.tomcat.max-connections`).

The Spring starter enables forwarded-header support by default via a
lowest-precedence `EnvironmentPostProcessor` that contributes
`server.forward-headers-strategy=framework`. A hosted TLS terminator therefore
works with zero configuration, and SSR canonical URLs / the WebSocket same-origin
check use the external origin. An explicit `server.forward-headers-strategy` in
your configuration always wins; set it to `none` (or `native`) to disable or
replace Pathland's default.

## Edge rate limiting

The in-process limiter protects a single connection from flooding the session
actor. For volumetric or per-IP abuse, add a gateway/edge limit, for example:

```nginx
limit_req_zone $binary_remote_addr zone=pathland_ws:10m rate=10r/s;

location /_pathland/ws {
    limit_req zone=pathland_ws burst=20 nodelay;
    proxy_pass http://app;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

## Verification checklist

- `curl -I https://host/` → `Content-Security-Policy`, `X-Content-Type-Options`,
  `Referrer-Policy`, `X-Frame-Options`.
- SSR `<script>` carries `integrity="sha384-…"`.
- A WebSocket handshake with `Origin: https://evil.example` is rejected (403);
  same-origin succeeds.
- With `X-Forwarded-Proto: https` + `X-Forwarded-Host: example.com`, a handshake
  from `Origin: https://example.com` succeeds and the SSR canonical URL uses
  `https://example.com/...`.
- An event batch larger than 64 KiB is rejected without reaching the session.
- `pathland.inbound.dropped` stays at zero under normal interaction.
