# ADR-0009: Language-owned bindings checked by shared conformance data

**Status:** Accepted
**Recorded:** 2026-11-12 (extracted existing decision; original date not established)
**Scope:** Protocol bindings, engines, HTML rendering, verification

## Context

Independent language implementations must agree on wire IDs and engine behavior
without making every language use Rust source representations or native runtime
dependencies.

## Decision

Publish numeric IDs as a target-neutral manifest from Rust constants. Keep the
JVM constants hand-written and validate them against the manifest. Check wire
encoding with golden vectors, engine behavior with shared Rust-produced frame
traces, and Java HTML output with canonical Rust SSR fixtures.

## Rationale

The manifest contains data only, so each language owns its representation.
Byte-for-byte fixtures provide an executable agreement beyond descriptive
prose or individual numeric constants. The independent JVM renderer supports
the pure-JVM boundary recorded in ADR-0008.

## Alternatives

Rust-to-JVM source generation is explicitly not used. Sharing a native runtime
is not required for server execution. The sources do not establish a historical
comparison of specific generators or testing frameworks.

## Consequences

Protocol and emission changes require coordinated manifest/fixture regeneration
and binding changes. Passing a fixture proves its scenario, not exhaustive
semantic coverage; the prose contract remains necessary.

## Sources and current contract

- [Manifest authority](../../spec/OPCODE.md#overview).
- [Shared engine traces](../../spec/ENGINE.md#8-shared-engine-traces).
- [Fixture regeneration instructions](../../spec/engine-traces/README.md).
- [Manifest and JVM checks](../../AGENTS.md#machine-readable-protocol-manifest).
- [JVM HTML conformance boundary](../../AGENTS.md#jvm-libraries-libjava-kotlin-first).
