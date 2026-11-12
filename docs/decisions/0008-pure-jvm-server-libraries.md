# ADR-0008: Pure-JVM server libraries with opt-in native interop

**Status:** Accepted
**Recorded:** 2026-11-12 (extracted existing decision; original date not established)
**Scope:** JVM modules, server hosting, native desktop interop

## Context

The JVM stack serves both native desktop hosts and Spring Boot/Quarkus servers,
including GraalVM native-image builds. Desktop requires native ring interop;
server authoring and rendering need not load Rust libraries.

## Decision

Use a pure-Java wire core and HTML renderer with a Kotlin engine and authoring
DSL. Isolate JNA/native ring integration in opt-in `pathland-native-core`.
Keep Redis/Lettuce state persistence in a separate opt-in module rather than the
server core. Java hosts mount Kotlin views through Java-friendly factories.

## Rationale

The agent guide explicitly separates native interop so server runtimes and
native-image builds do not carry JNA. The Java HTML port runs across platforms
without a cdylib. The Redis split similarly keeps Lettuce out of in-memory builds.
Shared Kotlin views avoid separate server and desktop authoring surfaces.

## Alternatives

The documented boundaries contrast pure-JVM server execution with native desktop
interop and in-memory state with opt-in Redis. A historical evaluation of other
FFI mechanisms or JVM languages is not documented.

## Consequences

The JVM implementations need conformance checks against shared Rust artifacts.
Ordinary server builds do not require Rust; desktop libraries and shared-artifact
regeneration still do. Optional dependencies must remain optional in practice.

## Sources and current contract

- [JVM module boundaries](../../AGENTS.md#jvm-libraries-libjava-kotlin-first).
- [Kotlin authoring and Java hosting](../../AGENTS.md#kotlin-jvm-authoring-dsl-devpathlandviewdsl-in-libjavapathland-view-core).
- [Cross-language conformance decision](0009-cross-language-conformance.md).
