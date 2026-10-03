# Pathland Test Kit

Shared test infrastructure for the Pathland Java reactor.

## Implemented

- **`com.pathland.server.test.WsSession`** — a minimal JDK-`java.net.http.WebSocket`
  client that speaks the PLPL wire protocol (environment / resync / click / ping),
  collects decoded frames + peer-close codes, and tracks unexpected closes. One shared
  `HttpClient` across all connections.
- **`com.pathland.server.test.MultiSessionProbe`** — the shared 100-session (configurable
  via `PATHLAND_TEST_SESSIONS`) WS stress probe used by the Quarkus + Spring starters'
  `MultiSessionWebSocketTest`: every session connects, re-syncs, gets transport-level
  PONGs, stays isolated (only its own click counts reach its label), and is never closed
  by the peer.

The harness lives in **main scope** so `mvn package` (the Docker/Render build, which runs
`-Dmaven.test.skip=true`) resolves it as a normal jar — a `tests`-classifier test-jar
dependency previously broke the package build. The starters depend on this module with
`scope=test`.

## Verified by

The starters' `MultiSessionWebSocketTest` runs (both green in `mvn clean install`); the
full Docker image build (`docker build -f lib/java/pathland-quarkus-demo/Dockerfile .`)
succeeds.