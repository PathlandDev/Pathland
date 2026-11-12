# ADR-0010: Server-side composition through ordinary protocol sessions

**Status:** Accepted
**Recorded:** 2026-11-12 (extracted existing decision; original date not established)
**Scope:** Proxy views, server composition, transport

## Context

Independently deployed Pathland applications need to compose into one document
without requiring the end renderer to manage each origin separately.

## Decision

The composing server acts as a normal host toward an origin and as a guest
toward the end client. It embeds origin output, remaps node IDs into disjoint
ranges, and routes events back. Use existing protocol operations rather than a
proxy-specific opcode or an origin-specific service API.

## Rationale

The documented model preserves one document and one client connection while
allowing independent deployments. Origins need no proxy awareness. ID remapping
is necessary because independently allocated node IDs otherwise collide.

## Alternatives

The sources explicitly contrast ordinary protocol sessions with proxy-specific
opcodes and origin awareness. They do not document a historical evaluation of
iframes or other microfrontend systems.

## Consequences

The composing server owns ID/reference translation, event routing, lifecycle,
and flow control. Reserved ranges impose explicit limits. Nested proxy behavior
is outside the current proxy contract, not implied by this architectural choice.

## Sources and current contract

- [Proxy roles](../../spec/PROXY.md#1-roles) and [namespacing](../../spec/PROXY.md#2-node-id-namespacing-must).
- [Scope and lifecycle contract](../../spec/PROXY.md).
- [Server-side microfrontend overview](../../README.md#microfrontends-composed-server-side).
