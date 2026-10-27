# @pathland/protocol — status

**Last updated:** October 22, 2026

## Implemented

- `constants.ts` generated from `pathland-ts-codegen` (same constants as the
  existing DOM renderer).
- `BatchWriter` / `BatchReader` (`parseBatch`, `readString`, `readList`) for
  self-contained PLPL frames: header, 16-byte opcodes, frame-local strings.
- EVENT decode (`decodeEvents`), including `TEXT_CHANGED` and URL-bearing
  `NAVIGATE`.
- META decode (`environment`, `resync`, `ping`, `pong`) and `encodePong`.
- Tests cover round-trips, known header/layout, list values, event and
  environment decoding, and heartbeat messages.

## Not implemented / gaps

- The browser client keeps its own codec (`lib/typescript/src/plpl.ts`); the two
  are cross-tested behaviorally but not yet shared.
- No streaming/partial batch decode (every batch is self-contained).
