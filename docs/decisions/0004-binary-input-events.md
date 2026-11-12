# ADR-0004: Binary input events rather than payload callbacks

**Status:** Accepted
**Recorded:** 2026-11-12 (extracted existing decision; original date not established)
**Scope:** Events, native interop, network transport

## Context

Input must return from renderers to applications across the same language and
platform boundaries as view updates.

## Decision

Encode renderer input as EVENT-category 16-byte opcodes. Shared-memory input
uses the event ring and event arena; network input uses binary batches. Native
callbacks may wake a consumer but do not deliver event payloads out of band.
Renderers report the input surface; application-side handlers and recognizers
compose actions and gestures.

## Rationale

The existing architecture explicitly makes both protocol directions binary to
keep the wire language- and platform-agnostic. Event guards avoid serializing
interactions the application has not requested.

## Alternatives

Payload-bearing side-channel callbacks are explicitly excluded in the native
interop documentation. High-level gestures are composed application-side rather
than added as the core raw-event surface; native control value/text reports are
still part of that surface.

## Consequences

Foreign hosts drain events through the protocol rather than implementing a
second payload ABI. Renderers need listener/binding guards and echo suppression;
applications own dispatch and gesture recognition.

## Sources and current contract

- [Event model and guards](../../spec/EVENTS.md#event-model).
- [Opcode category table](../../spec/OPCODE.md#category-table).
- [Native event write/wake/drain path](../../AGENTS.md#gtk4-renderer-pathland-render-gtk).
