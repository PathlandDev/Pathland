# pathland-quarkus-kotlin-demo — implementation status

**Last updated:** November 1, 2026

Kotlin-first Quarkus demo: SSR plus live 16-byte opcode deltas over WebSocket,
authored with `pathland-view-kotlin` and hosted by `pathland-quarkus-kotlin`.

## Implemented

- Kotlin `PathlandApp` CDI bean (`KotlinDemoApplication`) mounting the Kotlin
  `MusicPlayerView` at `/`.
- **Sponsored proxy** — a second `MountedApp` at `/ad` (`AdView`), embedded into
  the player's Now Playing pane via `ProxyView` (same-server loopback proxy).
- The standalone `KotlinDemoView` remains available as a small state/input
  showcase.
- Exercises persisted state (`count`, `items`), two-way text input (`name`),
  reactive computed text, `ForEach`, and Kotlin modifier sugar.
- Quarkus Kotlin build setup: `kotlin-maven-plugin`, `all-open` for CDI/JAX-RS
  annotations, `jvmTarget=17`.
- The removed Java DSL (`pathland-view`) and Java demo views are not on the
  classpath: the demo is
  Kotlin-DSL-only.
- `KotlinDemoSmokeTest` (`@QuarkusTest`) asserts the CDI bean is discovered, SSR
  renders the Kotlin tree, the `/ad` app is mounted and embedded through the proxy
  (with remapped proxy ids), and the SSR document references the content-hashed
  DOM bundle (a GET on that URL returns 200).
- The DOM renderer bundle + service worker are copied by
  `lib/typescript/scripts/copy-to-demos.mjs`.
- Run with `scripts/run-quarkus-kotlin-demo.sh` → `http://localhost:8080`.

## Not implemented / gaps

- No WebSocket delta integration test yet; SSR discovery is covered.
- No Quarkus codestart yet.
- Native image is not supported (Rust HTML renderer via JNA).
