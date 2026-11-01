# pathland-quarkus-java — implementation status

**Last updated:** November 1, 2026

Quarkus extension for the Java authoring DSL. It depends on the DSL-neutral
`pathland-quarkus` extension and adds `pathland-view`.

## Implemented

- Runtime module `pathland-quarkus-java` + deployment module
  `pathland-quarkus-java-deployment`.
- Provides the `dev.pathland.quarkus.java` capability.
- No server/runtime logic of its own; SSR, WebSocket, sessions, state and assets
  stay in `pathland-quarkus`.
- `pathland-quarkus-starter` is a deprecated compatibility alias for this
  extension.

## Transitional status

The Java DSL remains first-class while the Kotlin DSL becomes the primary JVM
authoring surface. When the Java DSL is retired, this extension and the starter
alias can be deleted without touching `pathland-quarkus` or
`pathland-quarkus-kotlin`.
