# pathland-state-redis — implementation status

**Last updated:** November 8, 2026

Opt-in Redis `StateStore` (Lettuce) for the Pathland server runtime. Kept out of
`pathland-server-core` so deployments that use the in-memory store — and
GraalVM native-image builds such as the Quarkus demo — never carry Lettuce.

## Implemented

- **`RedisStateStore`** (`dev.pathland.state.redis`): a `StateStore` backed by
  Lettuce, storing Java-serialized values under plain keys. Supports a default
  URI (`redis://localhost:6379`), an explicit `RedisURI`, or a caller-supplied
  `RedisClient`.
- **`RedisStateStores.redisOrFallback(fallback)`**: probes Redis once and returns
  the Redis store when reachable, otherwise the supplied fallback (typically
  `InMemoryStateStore`).
- **`RedisStateStore.Provider`**: the framework-neutral `StateStoreProvider` SPI.
- **Native-image metadata**: the module ships
  `META-INF/native-image/.../native-image.properties`, keeping Lettuce's static
  Netty DNS resolver holders run-time initialized. Apps that opt into Redis in a
  native build get the workaround without app-level flags.

## Usage

```java
@Produces @Singleton
StateStore stateStore() {
    return RedisStateStores.redisOrFallback(new InMemoryStateStore());
}
```

Add the `pathland-state-redis` dependency and a `StateStore` bean; the Quarkus
and Spring starters' default in-memory bean yields to it.

## Not implemented / gaps

- Java serialization of non-JDK state types in a native image still needs
  reachability metadata for those types.
- No automated Redis integration test (the store requires a live server); the
  module builds and its provider/fallback seam is testable without one.
