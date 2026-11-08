# Security Policy

Pathland is a network-facing binary protocol: the byte decoders and the C ABI
are security-critical surfaces. This policy covers how vulnerabilities are
reported and handled.

## Reporting a vulnerability

**Do not open a public issue for a security vulnerability.** Report it privately
to the maintainers:

- Email: `michael@apaq.dk`
- Subject prefix: `[Pathland security]`

Please include:

- The affected crate/module and version (or commit).
- A description of the vulnerability and its impact.
- A minimal reproduction (payload bytes or code) where possible.
- Any suggested fix, if you have one.

We aim to acknowledge reports within **5 business days** and to ship a fix (or a
mitigation) as soon as a coordinated patch is ready. We will credit reporters in
the release notes unless anonymity is requested.

## Scope

The following are in scope for this policy:

- `lib/rust/crates/pathland-core` — the `no_std` protocol core (opcode decode,
  ring buffer, arena, memory layout).
- `lib/rust/crates/pathland-core-transport` — PLPL batch encode/decode.
- `lib/rust/crates/pathland-core-capi` — the `unsafe extern "C"` ring ABI
  (`libpathland_core`): pointer-argument handling, zero-copy reads, event drain.
- `lib/java/pathland-core` `transport` — `FrameCodec` (PLPL encode/decode).
- The `@pathland/dom-renderer` web client (PLPL batch decode + hydration in
  `lib/typescript/src`, built to `dist/pathland-dom-renderer.js` and shipped in
  the Spring Boot starter + Quarkus extension jars).

Out of scope: dependencies (report those to their respective projects),
development-only tooling, and the demo application code.

## Security-sensitive guarantees

- Every 16-byte opcode decoder must **reject malformed/truncated input
  gracefully** (no panics, no out-of-bounds reads, no hangs) — see the hardening
  issues (#37, #38).
- The C ABI entry points (`pathland_ring_buffer_push`,
  `pathland_core_arena_alloc`, `pathland_core_drain_events`, …) treat
  caller-provided pointers as trusted by contract; the bounds guarantees must be
  upheld and are audited via Miri/sanitizers in CI.
- No secrets or tokens may ever be committed; see
  [CONTRIBUTING.md](./CONTRIBUTING.md).

## Deployment hardening

Framework- and deployment-level controls (WebSocket origin checks, CSP and
security headers, transport/message limits, rate limiting, TLS/reverse-proxy
setup) are documented in [docs/HARDENING.md](./docs/HARDENING.md). The Quarkus
extension supplies secure defaults; the Spring Boot starter requires the
`pathland.allowed-origins` property for cross-origin hosts.

## Security testing

Coverage is tracked by the hardening milestone (issues #37–#39): fuzzing for the
Rust/TypeScript/Java decoders, Miri + ASan/UBSan on the C ABI, and bounds fixes.
Results will be linked here as they land.

## Supported versions

The project is pre-1.0 (`wire protocol version 1`). Security fixes land on the
default branch and are backported only when a tagged release is actively used;
see the [Versioning](README.md#versioning) section.