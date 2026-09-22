# pathland-quarkus-starter — implementation status

**Last updated:** September 12, 2026

Quarkus integration for Pathland: SSR at any path, live deltas over `/ws`, per-session
state. Adding the starter dependency + a `PathlandApp` CDI bean gives a running app.

## Implemented

- **`PathlandSocket`** — `@WebSocket("/ws")`: 1:1 sessions (the `session` cookie id,
  memoized per connection), binary events routed into the `PathlandRegistry`.
- **`IndexResource`** — JAX-RS SSR catch-all (`/` + deep links, `/ws` excluded via
  negative lookahead) + `session` cookie.
- **`PathlandRegistryProducer`** — CDI `@Produces @Singleton` `PathlandRegistry` from the
  app's `PathlandApp` bean + default store (Redis-or-in-memory); `@Disposes` shuts it down.
  Reads the `pathland.debug-html` config property (default `false`) to enable per-node
  HTML comments in SSR output.
- **`QuarkusConnection`** — adapts `WebSocketConnection` to `PathlandConnection`.
- `META-INF/beans.xml` so Quarkus discovers the starter.

## App DX

```java
@ApplicationScoped
public class MyApp implements PathlandApp {
    public View newRoot() { return new MyHomeView(); }
}
```

## Verified by

Manual: the `pathland-quarkus-demo` renders `/`, `/kitchen`, `/settings` and the 404
fallback; a WebSocket handshake to `/ws` returns 101. Core behavior is covered by
`pathland-server-core` tests.