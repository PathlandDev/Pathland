# pathland-markdown — implementation status

**Last updated:** November 6, 2026

The **shared inline Markdown parser**: `no_std` + `alloc`, used by the Rust DSL
directly and by foreign hosts through `pathland-markdown-capi`. It produces a
`pathland_core::AttributedString` (style runs + links), not a separate AST.

## Implemented

- Inline subset: `**bold**` / `__bold__` → `FONT_WEIGHT` 700, `*italic*` /
  `_italic_` → `FONT_STYLE` Italic, `` `code` `` → `FONT_DESIGN` Monospaced,
  `~~strikethrough~~` → `STRIKETHROUGH`, `[label](url)` → link run (label parsed
  recursively), and backslash escapes.
- Nested emphasis with run splitting (`***`/`___` close italic then bold);
  intraword underscores stay literal (`snake_case`).
- UTF-8 byte offsets in the produced runs; code spans do not parse inner markup.
- Unit tests for markers, nesting, links, code, escapes, underscores, unmatched
  markers, and UTF-8 offsets.
- Used by both authoring DSLs: Rust `pathland-engine` auto-parses
  `Node::markdown_text`; Kotlin `Text(...)` calls it through the embedded
  `pathland-markdown-capi` dylib in `pathland-view-core`.

## Not implemented / gaps

- Block Markdown (headings, lists, paragraphs), reference links, images, and
  tables.
