# Contributing to Pathland

Thanks for your interest in Pathland — an open, cross-platform, cross-language
UI protocol. This guide covers how to build, test, and contribute.

## Code of conduct

This project is governed by the [Contributor Covenant](CODE_OF_CONDUCT.md). By
participating you agree to abide by its terms.

## Security

Found a vulnerability? **Do not open a public issue** — report it privately per
[SECURITY.md](SECURITY.md).

## Project map

- `lib/rust/` — the Rust workspace: `pathland-core` (protocol), `pathland-engine`
  (emitter/signals), `pathland-view` (DSL), `pathland-core-transport`,
  `pathland-core-capi` (C ABI), `pathland-render-gtk`, `pathland-render-html`,
  `pathland-view-native`, `pathland-render-gtk-demo`.
- `lib/java/` — the Maven reactor: `pathland-view-core`
  (`dev.pathland.view.core*` + `dev.pathland.view.engine`), `pathland-view`
  (the Java DSL, `dev.pathland.view`), `pathland-view-processor`,
  `pathland-view-kotlin` (`dev.pathland.view.dsl`), `pathland-render-html`,
  `pathland-state-redis`, `pathland-demo-views`, the Quarkus/Spring demos, and
  the TypeScript DOM renderer (`lib/typescript`).
- `spec/` — the protocol contract: `OPCODE.md` (primary), `PRIMITIVES.md`,
  `MODIFIERS.md`, `EVENTS.md`, `CONFORMANCE.md` (golden vectors), `DSL.md`
  (authoring surface).

Implementation status per project lives in each crate/module's `status.md`.

## Prerequisites

- **Rust**: stable toolchain (`rustup`); `wasm32-unknown-unknown` target for the
  `no_std` direction guard.
- **Java**: JDK 17+ (Maven `release=17`; the whole stack runs on every LTS from 17).
- **GTK4** (Linux) for `pathland-render-gtk`.

## Build & test

```bash
# Rust workspace
cd lib/rust && cargo test

# no_std / wasm32 direction guard (requires: rustup target add wasm32-unknown-unknown)
cd lib/rust && ./check-wasm.sh

# C ABI (cdylib) used by Java JNA / native hosts
cd lib/rust && cargo build -p pathland-core-capi

# Java reactor (the core libraries; the Quarkus/Spring demos are excluded from
# CI but buildable with: cd lib/java && mvn -q install)
cd lib/java && mvn -q -B test -pl '!pathland-quarkus-demo,!pathland-spring-boot-demo'
```

CI runs exactly these commands (`.github/workflows/ci.yml`).

## Making changes

1. Open an issue first (or pick an open one) so the work is visible. Issues are
   labelled by epic: `hardening`, `benchmarking`, `tooling`, `a11y-seo`,
   `packaging`, and grouped into milestones `1 · Hardening & Memory Safety`
   through `5 · Packaging & Grant Readiness`.
2. Create a focused branch (`fix/…`, `feat/…`, `docs/…`).
3. Keep changes small and reviewable; add/update tests with every change.
4. Update the relevant `status.md` file when you land protocol or renderer work
   (the specs describe the contract; implementation status lives in `status.md`).
5. Keep commits atomic with clear messages (see below). No DCO sign-off is
   required.

## Commit style

- Short, imperative subject line (`feat(view): …`, `fix(transport): …`,
  `docs(spec): …`, `refactor(…): …`).
- Follow the repo's existing history as the style guide.
- Reference the issue where useful (`Fixes #NN`).

## Protocol changes (important)

- Update `spec/OPCODE.md` **first**, plus the affected companion catalog and the
  golden vectors in `spec/CONFORMANCE.md` **before** implementations depend on
  the change.
- Keep the 16-byte opcode format stable; wire-format changes must bump the
  `version` field (currently 1) per `spec/OPCODE.md`.
- New components/properties: allocate IDs in the specs + `constants.rs`
  (`lib/rust/crates/pathland-core/src/constants.rs`), then mirror across
  `lib/java/…/Components.java`/`Properties.java` and `lib/typescript/src/constants.ts`,
  and update each renderer + its `status.md`.

## Non-negotiable principles

- Renderers are **stateless pure functions** of the opcode stream.
- The engine emits **structure + constraints**, never positions/rects.
- Emission is **diff-based**: only changed nodes emit; unchanged trees emit zero
  opcodes.
- Binary both ways: tree mutations (app → renderer) and raw-input events
  (renderer → app) share the 16-byte opcode format.

## Communication

- Issues/PRs are the primary channel.
- Contact the maintainers at `michael@apaq.dk` for private matters.