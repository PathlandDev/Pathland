# pathland-markdown-capi — implementation status

**Last updated:** November 6, 2026

The C ABI (`cdylib` name `libpathland_markdown`) for foreign hosts (Java/JNA,
Swift, …). It wraps the shared `pathland-markdown` parser.

## Implemented

- `pathland_markdown_parse(input, len, outLen)` — returns an owned buffer with
  the encoded `AttributedString` payload; null on invalid UTF-8 / encode failure.
- `pathland_markdown_free(ptr, len)` — releases the buffer.
- Tests for round-trip and invalid UTF-8.

## Not implemented / gaps

- No streaming/incremental parse API; one call per document.
