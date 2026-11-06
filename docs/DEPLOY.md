# Deploying the Quarkus SSR demo

Runs the [Pathland Quarkus demo](pathland-quarkus-demo/) (server-side HTML at any
path + live `/ws` deltas) as a container — the application evidence for the Restack
proposal (issue #71, `docs/PREAPPLICATION-TODO.md`).

The image is multi-stage: it builds the Rust HTML renderer and inline-Markdown
cdylibs, embeds them into the Java `pathland-render-html` and `pathland-view-core`
jars, packages the Quarkus app, and runs it on a JRE 17 runtime.

## Build

From the repository root (build context must be the root — the embed step needs
the Rust dylib and the Java reactor sources):

```bash
docker build -f lib/java/pathland-quarkus-demo/Dockerfile -t pathland-quarkus-demo .
```

The Rust stage builds `libpathland_render_html.so`; the Java stage copies it into
`lib/rust/target/debug` so the renderer jar-embed step finds it. The JVM inline
Markdown parser is pure Java and needs no native build.

The image's Maven step selects both `pathland-quarkus-demo` and
`pathland-quarkus-deployment`. The deployment module is not a Maven dependency of
the demo, but the runtime extension descriptor validates its dependencies against
it; omitting it makes a fresh (empty `~/.m2`) build fail Quarkus extension
verification.

## Run locally

```bash
docker run --rm -p 8080:8080 pathland-quarkus-demo
# → http://localhost:8080
```

A quick check that it serves:

```bash
curl -I http://localhost:8080/          # 200, text/html
curl http://localhost:8080/ | grep -o 'data-pathland-id'   # hydrated SSR markup
```

State defaults to the in-memory store (no Redis needed for the demo). To use
Redis, pass a `REDIS_URL` and the Redis `StateStore` in `pathland-server-core` is
selected at runtime — the standalone demo runs without it.

## Push to a registry

```bash
docker tag pathland-quarkus-demo ghcr.io/<org>/pathland-quarkus-demo:latest
docker push ghcr.io/<org>/pathland-quarkus-demo:latest
```

## Deploy to a host

Any platform that runs a Docker container exposing TCP 8080 works:

- **Railway / Fly.io** — point at the image (or use a Dockerfile deploy); set the
  public port to **8080**. The app binds `0.0.0.0` (see
  `application.properties`).
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
- **Memory**: the JVM is capped at 256 MB (`-Xmx256m`); the demo is small.