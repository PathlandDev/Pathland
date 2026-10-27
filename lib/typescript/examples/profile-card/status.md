# examples/profile-card — status

**Last updated:** October 22, 2026

## Implemented

- `UserProfileCard.ts` with the reference DSL (only `public` → `export` changed).
- Server-owned `ProfileCardApp` using `State` for the search query and a
  `computed` query echo.
- Express server: SSR through the Rust wasm renderer, static avatar/bundle
  assets, and the WebSocket endpoint.
- Content-hashed DOM-client bundle + `dom-renderer.current` manifest.
- Playwright e2e: SSR content, hydration without duplicate nodes, typing in the
  text field, and the live echo delta.

## Not implemented / gaps

- The example is single-page and static-tree; structural navigation is deferred.
- No Redis persistence (uses `MemoryStateStore`).
