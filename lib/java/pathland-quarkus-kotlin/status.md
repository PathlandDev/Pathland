# pathland-quarkus-kotlin — implementation status

**Last updated:** November 1, 2026

Quarkus extension for the Kotlin authoring DSL. It depends on the DSL-neutral
`pathland-quarkus` extension, `pathland-view-kotlin`, and `quarkus-kotlin`.

## Implemented

- Runtime module `pathland-quarkus-kotlin` + deployment module
  `pathland-quarkus-kotlin-deployment`.
- Provides the `dev.pathland.quarkus.kotlin` capability.
- No server/runtime logic of its own; SSR, WebSocket, sessions, state and assets
  stay in `pathland-quarkus`.
- **No Java DSL dependency**: the removed Java DSL/processor are not in the
  dependency tree; Kotlin is the only authoring surface.

## Build contract

The extension can enable Quarkus Kotlin support, but it cannot add a Kotlin
compiler plugin to an application. Kotlin apps must configure
`kotlin-maven-plugin` (or the Gradle Kotlin plugin), including `all-open` for
CDI/JAX-RS annotations. The Kotlin demo and (later) the codestart carry that
configuration.

## Not implemented / gaps

- No Quarkus codestart yet.
- Native image is not supported; the Rust HTML renderer is loaded through JNA.
