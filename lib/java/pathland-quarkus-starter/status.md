# pathland-quarkus-starter — implementation status

**Last updated:** September 27, 2026

Quarkus integration for Pathland: SSR at any path, live deltas over each app's reserved
`/{base}/ws`, per-session state. Adding the starter dependency + a `PathlandApp` CDI
bean gives a running app; `MountedApp` beans host more apps at their own subpaths
(the BFF layout).

## Implemented

- **`PathlandSocket`** — `@WebSocket("/_pathland/ws")` for the root app: 1:1 sessions
  per connection (a fresh `uiId` per connection + the client's `wid` state scope from
  the `?wid=` query param), binary events routed into the root registry of the
  `PathlandHost`.
- **`MountedPathlandSocket`** — `@WebSocket("/{app}/_pathland/ws")` for mounted apps:
  the `{app}` path param resolves the app's registry. Mount paths are single-segment.
- **`IndexResource`** — JAX-RS SSR catch-all (`/` + deep links, `/_pathland/**` excluded
  via negative lookahead) dispatching to the longest-mount app (prefix stripped);
  SSR renders defaults (no session cookie — per-window state arrives over the WS);
  per-app framework files (`/{app}/_pathland/...`, e.g. the mounted app's bundle) are
  served from the shared `/_pathland/**` classpath mount. Quarkus serves the global
  `/_pathland/**` bundle + asset mount from `META-INF/resources` before JAX-RS.
- **`PathlandRegistryProducer`** — CDI `@Produces @Singleton` `PathlandHost` from every
  `MountedApp` bean plus the lone `PathlandApp` bean (mounted at `/`); `@Disposes` shuts
  it down. Reads the `pathland.debug-html` config property (default `false`) to enable
  per-node HTML comments in SSR output.
- **`QuarkusConnection`** — adapts `WebSocketConnection` to `PathlandConnection`.
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
tests.