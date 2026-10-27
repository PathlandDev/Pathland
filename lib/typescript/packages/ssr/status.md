# @pathland/ssr — status

**Last updated:** October 22, 2026

## Implemented

- `loadSsrRenderer` loads the Rust HTML renderer wasm module.
- `renderDocument` / `renderFragment` copy the wasm output immediately (the
  pointer is only valid until the next render).
- `renderPage` injects `data-pathland-base`, optional service-worker and
  SSR-scope flags, and the content-hashed DOM-client script.
- `resolveBundleName` reads the `dom-renderer.current` manifest with a fallback.
- Tests cover wasm rendering, escaping, consecutive-render isolation, page
  injection, and manifest resolution.

## Not implemented / gaps

- The wasm binary is a build output (`pnpm build:wasm`), not committed.
- No service-worker registration path yet (the flag is emitted; phase 1 omits
  the worker).
