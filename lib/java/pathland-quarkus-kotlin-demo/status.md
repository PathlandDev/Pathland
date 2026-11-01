# pathland-quarkus-kotlin-demo — implementation status

**Last updated:** November 1, 2026

Kotlin-first Quarkus demo: SSR plus live 16-byte opcode deltas over WebSocket,
authored with `pathland-view-kotlin` and hosted by `pathland-quarkus-kotlin`.

## Implemented

- Kotlin `PathlandApp` CDI bean (`KotlinDemoApplication`) and a Kotlin-owned
  `KotlinView` root (`KotlinDemoView`).
- Exercises persisted state (`count`, `items`), two-way text input (`name`),
  reactive computed text, `ForEach`, and Kotlin modifier sugar.
- Quarkus Kotlin build setup: `kotlin-maven-plugin`, `all-open` for CDI/JAX-RS
  annotations, `jvmTarget=17`.
- Maven Enforcer bans `pathland-view` and `pathland-demo-views`: the demo is
  Kotlin-DSL-only.
- `KotlinDemoSmokeTest` (`@QuarkusTest`) asserts the CDI bean is discovered and
  SSR renders the Kotlin tree.
- The DOM renderer bundle + service worker are copied by
  `lib/typescript/scripts/copy-to-demos.mjs`.
- Run with `scripts/run-quarkus-kotlin-demo.sh` → `http://localhost:8080`.

## Not implemented / gaps

- No WebSocket delta integration test yet; SSR discovery is covered.
- No Quarkus codestart yet.
- Native image is not supported (Rust HTML renderer via JNA).
