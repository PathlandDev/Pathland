# pathland-render-html-wasm — status

**Last updated:** October 22, 2026

## Implemented

- `cdylib` wrapper over `pathland-render-html` for `wasm32-unknown-unknown`.
- Exports:
  - `pathland_wasm_alloc`
  - `pathland_wasm_free`
  - `pathland_wasm_render(batch, len, root, fragment, out_len)`
- Self-contained PLPL frames are decoded with `decode_frame`; document and
  fragment rendering share the native HTML renderer.
- The output is a reusable wasm-side buffer. Callers MUST copy the bytes before
  the next render call.
- Native unit tests cover document/fragment rendering and alloc/free.

## Not implemented / gaps

- No streaming, incremental rendering, or retained tree in wasm (SSR is a full
  snapshot render by design).
- The generated `.wasm` is a build artifact (`pnpm build:wasm`) and is not
  committed.
