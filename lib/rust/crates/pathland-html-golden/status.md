# pathland-html-golden — implementation status

**Last updated:** September 25, 2026

Cross-renderer **golden conformance fixtures** for the HTML renderers.

## Implemented

- A battery of self-contained `PLPL` scenario batches (counter, form,
  composite controls, layout, tokens, **semantics** — semantic roles mapping to
  native elements/ARIA, **media** — image alt/content-mode + video/audio native
  controls, and a base→delta pair whose change includes a `ROLE`
  change exercising the semantic-tag retag), each rendered by
  the canonical Rust SSR renderer into committed fixtures under
  `lib/typescript/test/fixtures/ssr/` (`{name}.plpl` batch bytes + `{name}.html`
  canonical `render_fragment`).
- `--emit` (regenerate fixtures) / `--check` (verify) CLI.
- `tests/guard.rs`: `cargo test -p pathland-html-golden` fails if the committed
  fixtures no longer match the current renderer output.
- The TypeScript DOM client consumes the same fixtures in
  `lib/typescript/test/ssr-conformance.test.ts` (fresh-DOM + hydrate-then-delta),
  so the Rust SSR output is the single contract both renderers must reproduce.

## Regenerate

`cargo run -p pathland-html-golden -- --emit` (or `npm run regen` from
`lib/typescript`), then commit the fixtures with the change that produced them.

## Not implemented / gaps

- Scenario coverage is a representative slice (the core shells + tokens); it is
  not exhaustive over every property/component combination.