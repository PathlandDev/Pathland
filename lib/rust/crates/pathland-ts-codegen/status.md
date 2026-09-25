# pathland-ts-codegen — implementation status

**Last updated:** September 25, 2026

Generates the TypeScript DOM renderer's protocol-derived files from the canonical
Rust sources, so the TS side cannot drift silently.

## Implemented

- **`constants.ts`** (`lib/typescript/src/constants.ts`): every wire constant is
  bound to its `pathland_core::constants` source (a rename breaks compilation, a
  revalue changes the emitted file); the protocol-semantic enum codes (alignment,
  shape kind, role, state, …) are enumerated from `spec/`.
- **`generated/tokens-core.ts`** (`lib/typescript/src/generated/tokens-core.ts`):
  the design-token conventions (`tokenToCssVar`, `isDarkToken`,
  `isLengthToken`, `resolveTokenCssRef` + length-token tables) emitted from
  `pathland_render_html::token_spec` — the single source the Rust renderer and
  the DOM client share. `src/tokens.ts` imports it.
- **`npm run regen`** (`lib/typescript/scripts/regen.mjs`) runs both generators
  (and the golden-fixture emitter); CI's `rust` job runs them and fails on
  `git diff --exit-code` drift.

## Regenerate

`npm run regen` (from `lib/typescript`) or `cargo run -p pathland-ts-codegen`
(from `lib/rust`), then commit the generated files with the change that produced
them.

## Not implemented / gaps

- Only the pure-data surfaces are generated. The element-shell factory
  (`elements.ts`) and the property-handler table (`classes.ts`) are still
  hand-written — their alignment is enforced behaviorally by the
  `pathland-html-golden` conformance harness rather than generated. Extending
  the generator to those tables is the planned next step.