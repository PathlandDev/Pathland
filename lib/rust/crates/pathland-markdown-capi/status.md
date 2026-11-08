# pathland-markdown-capi — implementation status

**Last updated:** November 6, 2026

The C ABI (`cdylib` name `libpathland_markdown`) for non-JVM foreign hosts
(Swift, .NET, …). It wraps the shared `pathland-markdown` parser. The JVM uses a
pure-Java port in `pathland-core` and no longer loads this library.

## Implemented

- `pathland_markdown_parse(input, len, outLen)` — returns an owned buffer with
  the encoded `AttributedString` payload; null on invalid UTF-8 / encode failure.
- `pathland_markdown_free(ptr, len)` — releases the buffer.
- Tests for round-trip and invalid UTF-8.

## Not implemented / gaps

- No streaming/incremental parse API; one call per document.
