# pathland-spring-boot-starter — implementation status

**Last updated:** September 12, 2026

Spring Boot auto-configuration for Pathland: SSR at any path, live deltas over the
reserved `/_pathland/ws`, per-session state. Adding the starter dependency + a
`PathlandApp` bean gives a running app.

## Implemented

- **`PathlandAutoConfiguration`** (`@AutoConfiguration`, `@ConditionalOnBean(PathlandApp.class)`
  so the starter is inert without a root view, `@EnableWebSocket` since Spring Boot 3.5 no
  longer auto-enables it) + `META-INF/spring/…/AutoConfiguration.imports`:
  - default `StateStore` bean (`@ConditionalOnMissingBean` — Redis-or-in-memory),
  - `PathlandRegistry` bean (shut down with the context),
  - `PathlandSocket` + a `WebSocketConfigurer` registering `/_pathland/ws`,
  - `PathlandIndexController` (SSR catch-all + `session` cookie + a `/_pathland/**`
    static controller serving the bundle + asset mount from `classpath:/static/_pathland/**`;
    the more-specific mapping beats `/{*path}`, and the `!Upgrade` header excludes WS).
- **SSR debug comments**: the `PathlandRegistry` bean reads the
  `pathland.debug-html` property (`@Value("${pathland.debug-html:false}")`) and forwards
  it to the registry, enabling per-node HTML comments in SSR output when set.
- **`SpringConnection`** — adapts `WebSocketSession` to `PathlandConnection`.

## App DX

```java
@SpringBootApplication
public class MyApp {
    public static void main(String[] args) { SpringApplication.run(MyApp.class, args); }
    @Bean PathlandApp pathlandApp() { return () -> new MyHomeView(); }
}
```

## Verified by

`mvn test` — `PathlandAutoConfigurationTest` (`@SpringBootTest`): a `PathlandApp` bean
activates the registry and SSR renders the root. Manual: the `pathland-spring-boot-demo`
renders `/`, `/kitchen`, `/settings` and the 404 fallback; a WebSocket handshake to
`/_pathland/ws` returns 101.