# Deploying the Quarkus SSR demo

Runs the [Pathland Quarkus demo](pathland-quarkus-demo/) (server-side HTML at any
path + live `/ws` deltas) as a container — the application evidence for the Restack
proposal (issue #71, `docs/PREAPPLICATION-TODO.md`).

The image is multi-stage: it builds the Java reactor with the Quarkus
Mandrel 25 builder image, compiles the demo to a **GraalVM native executable**
(no JVM at runtime), and copies only the native binary into the UBI9 Quarkus
micro image. No Rust toolchain or native library is needed.

## Requirements

- Docker with BuildKit (or Podman)
- Roughly **6–8 GB RAM** available to the build (native-image is memory-hungry)
- No local GraalVM/Mandrel installation — the builder image provides it
- **x86_64** target (`linux/amd64`); build with `--platform linux/amd64` when
  building on Apple Silicon
- Quarkus 3.39 requires **GraalVM/Mandrel 25+**; the Dockerfile pins the
  `jdk-25` Mandrel builder image

## Build

From the repository root (build context must be the root — the Java reactor plus
the generated base-design assets under
`lib/rust/crates/pathland-render-html/assets/`):

```bash
docker buildx build --platform linux/amd64 \
  -f lib/java/pathland-quarkus-demo/Dockerfile \
  -t pathland-quarkus-demo .
```

The image's Maven step selects both `pathland-quarkus-demo` and
`pathland-quarkus-deployment`. The deployment module is not a Maven dependency of
the demo, but the runtime extension descriptor validates its dependencies against
it; omitting it makes a fresh (empty `~/.m2`) build fail Quarkus extension
verification.

The Mandrel builder image does not ship Maven, so the build uses the committed
`lib/java/mvnw` wrapper. The first build downloads the Maven distribution and all
dependencies; subsequent builds reuse the BuildKit Maven cache. Native
compilation itself typically takes several minutes.

## Run locally

```bash
docker run --rm -p 8080:8080 pathland-quarkus-demo
# → http://localhost:8080
```

A quick check that it serves:

```bash
curl -I http://localhost:8080/          # 200, text/html
curl http://localhost:8080/ | grep -o 'data-pathland-id'   # hydrated SSR markup
curl -sSI https://<host>/ | grep -i '^strict-transport-security'   # HSTS
```

Also verify the native-specific paths:

```bash
curl -I http://localhost:8080/_pathland/assets/audio/track1.mp3   # Range/seek
curl -I http://localhost:8080/_pathland/sw.js                     # Service-Worker-Allowed
curl -I http://localhost:8080/_pathland/base-<hash>.css           # linked base design
curl -sS http://localhost:8080/q/health
curl -sS http://localhost:8080/q/metrics | head
```

The player at `/` embeds the `/ad` app through a same-server `ProxyView`. The
proxy uses the JDK `java.net.http.WebSocket` client; confirm the sponsored slot is
interactive in the native container. If it is not, add
`--initialize-at-run-time=java.net.http,jdk.internal.net.http` to
`quarkus.native.additional-build-args` or switch the Quarkus starter to a
WebSockets Next client.

HSTS is app-owned (the extension does not default it): the demo sets
`max-age=31536000; includeSubDomains` in its `application.properties`. Because
that file is baked into the native image, changing it requires a rebuild/redeploy
— a plain restart of an existing container will not pick it up.

State defaults to the in-memory store (no Redis needed for the demo). Redis is
opt-in: add the `pathland-state-redis` dependency and provide a `StateStore` bean,
for example:

```java
@Produces @Singleton
StateStore stateStore() {
    return RedisStateStores.redisOrFallback(new InMemoryStateStore());
}
```

The module ships native-image metadata for Lettuce's static Netty DNS resolver
holders. If you use Redis with non-JDK state types in a native image, add GraalVM
serialization reachability metadata for those types.

## Push to a registry

```bash
docker tag pathland-quarkus-demo ghcr.io/<org>/pathland-quarkus-demo:latest
docker push ghcr.io/<org>/pathland-quarkus-demo:latest
```

## Deploy to a host

Any platform that runs a Docker container exposing TCP 8080 works:

- **Render / Railway / Fly.io** — point at the image (or use a Dockerfile deploy);
  set the public port to **8080**. The app binds `0.0.0.0` (see
  `application.properties`). Build the image for `linux/amd64`.
- **Hetzner / any VPS** — `docker run -d -p 8080:8080 --name pathland pathland-quarkus-demo`
  (optionally behind Caddy/nginx; forward `GET /` and the WebSocket `Upgrade`).
- **Platform port override** — the app honors `QUARKUS_HTTP_PORT` if a platform
  assigns a different port.

### Requirements

- **WebSocket support**: the demo keeps the first page live via `/ws` (raw-input
  events + `PLPL` deltas). The reverse proxy / load balancer must forward the
  WebSocket `Upgrade` headers; most PaaS do this by default.
- **Health check**: `GET /` returning 200 with HTML is the simplest probe. The
  container has no separate health endpoint.
- **Memory**: the native executable has a much smaller footprint than the former
  JVM image (which was capped at `-Xmx256m`); the demo is small.
