# ADR-0012: One generated, token-layered HTML base design

**Status:** Accepted
**Recorded:** 2026-11-12 (extracted existing decision; original date not established)
**Scope:** HTML renderer assets, design tokens, build pipeline

## Context

The HTML renderer supplies default appearance shared by Rust SSR, JVM SSR, and
the DOM client. The token catalog includes paths that are both leaves and
groups, such as `control.border` and `control.border.width`.

## Decision

Maintain one built-in HTML base design using layered base, semantic, and
component tokens plus hand-authored reset/component CSS. Represent tokens as
flat DTCG entries. Generate the CSS bundle and content-hashed name, sync the
committed assets into the Rust renderer, and reuse them in JVM packaging.
Applications customize through themes rather than selecting built-in designs.

## Rationale

The design README explicitly explains that flat keys accommodate overlapping
leaf/group paths that nested DTCG trees cannot represent. Internal base tokens
resolve into public semantic/component values. The content hash identifies the
actual renderer-owned bytes for cache-busting linked stylesheets.

## Alternatives

Nested token trees cannot represent the documented catalog shape. App-facing
base-design selection is explicitly absent. The sources do not establish why
Style Dictionary was selected over any particular competing build tool.

## Consequences

Generated CSS must not be edited by hand. Token changes require generation,
validation, and synchronization; Rust and JVM consume the same assets. Inline
and linked delivery remain renderer APIs, not separate theme systems.

## Sources and current contract

- [Design pipeline and token representation](../../design/README.md#tokens).
- [Serving and validation instructions](../../design/README.md).
- [HTML base-design integration](../../AGENTS.md#html-base-design-design).
- [Theme ownership contract](../../spec/TOKENS.md#core-principle).
