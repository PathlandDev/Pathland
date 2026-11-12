# ADR-0005: Application intent and renderer-owned appearance

**Status:** Accepted
**Recorded:** 2026-11-12 (extracted existing decision; original date not established)
**Scope:** Tokens, themes, icons, renderers

## Context

Applications need a portable vocabulary for appearance without prescribing one
platform's native defaults, glyphs, or interaction styling to every renderer.

## Decision

Applications supply semantic token references, theme overrides, literal property
values, and canonical icon names. Renderers own token defaults and resolution,
platform scheme detection, interaction styling, and native icon mappings. Token
names use an open dotted-path namespace, not separately allocated numeric IDs.

## Rationale

The token specification explicitly aims to preserve native look-and-feel by
default while supporting cross-renderer consistency through overrides. The icon
specification applies the same intent/visual split to native symbols. Renderer
resolution lets token and scheme changes update appearance without re-emitting
every referencing node.

## Alternatives

Protocol descriptions of hover/click styles and interaction-state layout tuning
are explicitly excluded. Icons are semantic names rather than image URLs;
ordinary image assets remain a separate primitive. No historical evaluation of
particular token or icon libraries is documented.

## Consequences

Renderers must supply the catalog's required defaults and fallback behavior.
Applications cannot assume specific default values or identical platform glyphs.
Theme override state is rendered-output configuration, not business state.

## Sources and current contract

- [Token ownership](../../spec/TOKENS.md#core-principle) and [identification](../../spec/TOKENS.md#token-identification).
- [Token references in properties](../../spec/OPCODE.md#references-in-properties).
- [Icon vocabulary and ownership](../../spec/ICONS.md).
