# ADR-0001: Fixed-width binary instructions and transport-independent frames

**Status:** Accepted
**Recorded:** 2026-11-12 (extracted existing decision; original date not established)
**Scope:** Protocol, transport

## Context

Pathland is a cross-language UI protocol, not a single framework or rendering
target. Native peers can share memory; network peers cannot.

## Decision

Represent commands as fixed 16-byte binary instructions with numeric category,
command, component, and property IDs. Store variable-length payloads in an arena
referenced by offset. Carry frames through a shared-memory SPSC ring or network
batches, with the same frame consumption seam for renderers.

## Rationale

The opcode specification explicitly targets deterministic cursor-based decoding,
zero-copy shared-memory consumption, and zero dynamic allocation during
steady-state emission. Four instructions fit a 64-byte cache line. The common
frame seam separates rendering from the transport mechanism; these are design
goals and structural properties, not a comparative performance benchmark.

## Alternatives

The specification says this replaces a historical variable-length instruction
format. `AGENTS.md` explicitly chooses instruction bytecode over schema-driven
serialization and numeric IDs over string command/property names. A historical
evaluation of particular serialization libraries is not documented.

## Consequences

Variable-length content needs explicit arena lifetime and offset handling.
Network transport must package data that shared-memory peers access directly.
Wire changes require coordinated protocol and binding updates.

## Sources and current contract

- [Opcode overview, goals, and principles](../../spec/OPCODE.md#overview).
- [Wire format](../../spec/OPCODE.md#opcode-format) and [transport](../../spec/OPCODE.md#transport).
- [Original architectural principles](../../AGENTS.md#critical-architectural-principles).
- [Protocol-first project overview](../../README.md#pathland-is-a-protocol).
