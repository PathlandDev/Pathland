# pathland-spring-boot-starter — implementation status

**Last updated:** September 27, 2026

Spring Boot auto-configuration for Pathland: SSR at any path, live deltas over each
app's reserved `/{base}/ws`, per-session state. Adding the starter dependency + a
`PathlandApp` bean gives a running app; `MountedApp` beans host more apps at their own
subpaths (the BFF layout).

## Implemented

- **`PathlandAutoConfiguration`** (`@AutoConfiguration`, `@EnableWebSocket` since Spring
  Boot 3.5 no longer auto-enables it) + `META-INF/spring/…/AutoConfiguration.imports`:
  - default `StateStore` bean (`@ConditionalOnMissingBean` — Redis-or-in-memory),
  - `PathlandHost` bean (shut down with the context): every `MountedApp` bean plus the
    lone `PathlandApp` bean mounted at `/` (unless an explicit `MountedApp` claims the
    root). Without any app the host is empty and the starter is inert.
  - a `WebSocketConfigurer` registering **one handler per app** at its framework base
    (`/_pathland/ws` for the root, `/{mount}/_pathland/ws` otherwise). Each connection
    gets a fresh `uiId` (its UI model) + the client's `wid` (per-window state scope)
    from the `?wid=` query param,
  - `PathlandIndexController` (SSR catch-all dispatching to the longest-mount app with
    the prefix stripped + a framework static controller serving the bundle + asset mount
    from `classpath:/static/_pathland/**` at both `/_pathland/**` and `/{app}/_pathland/**`;
    the more-specific mapping beats `/{*path}`, and the `!Upgrade` header excludes WS).
    The framework controller honors byte-range requests (`206 Partial Content` +
    `Content-Range`, `416` for unsatisfiable ranges, `Accept-Ranges` on the full
    response) — an `<audio>`/`<video>` seeking beyond its buffer issues a `Range`
    request, and answering it with a full `200` would make browsers restart the media
    at byte 0.
    SSR renders defaults (no session cookie — per-window state arrives over the WS).
- **SSR debug comments**: the host reads the `pathland.debug-html` property
  (`@Value("${pathland.debug-html:false}")`) and forwards it to each registry, enabling
  per-node HTML comments in SSR output when set.
- **`SpringConnection`** — adapts `WebSocketSession` to `PathlandConnection`.

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
mounts at `/` and SSR renders the root. Manual: the `pathland-spring-boot-demo` renders
`/`, `/kitchen`, `/settings` and the 404 fallback **and a second app at `/app2`**; a
WebSocket handshake to `/_pathland/ws` and `/app2/_pathland/ws` returns 101.