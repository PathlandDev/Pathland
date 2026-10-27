export interface SsrRenderer {
  renderDocument(batch: Uint8Array, rootId: number): string;
  renderFragment(batch: Uint8Array, rootId: number): string;
}

interface WasmExports {
  memory: WebAssembly.Memory;
  pathland_wasm_alloc(len: number): number;
  pathland_wasm_free(ptr: number, len: number): void;
  pathland_wasm_render(
    batch: number,
    len: number,
    root: number,
    fragment: number,
    outLen: number,
  ): number;
}

/**
 * Load the Rust HTML renderer compiled to wasm32. The renderer is stateless:
 * each call decodes one self-contained PLPL snapshot and returns HTML.
 */
export async function loadSsrRenderer(
  wasm: Uint8Array | WebAssembly.Module,
): Promise<SsrRenderer> {
  const module =
    wasm instanceof WebAssembly.Module ? wasm : await WebAssembly.compile(wasm as unknown as BufferSource);
  const instance = await WebAssembly.instantiate(module, {});
  const exports = instance.exports as unknown as WasmExports;

  const render = (batch: Uint8Array, rootId: number, fragment: boolean): string => {
    const inputPtr = exports.pathland_wasm_alloc(Math.max(1, batch.length));
    const outLenPtr = exports.pathland_wasm_alloc(4);
    try {
      if (inputPtr === 0 || outLenPtr === 0) throw new Error("wasm allocation failed");
      new Uint8Array(exports.memory.buffer, inputPtr, batch.length).set(batch);
      const outPtr = exports.pathland_wasm_render(
        inputPtr,
        batch.length,
        rootId,
        fragment ? 1 : 0,
        outLenPtr,
      );
      if (outPtr === 0) throw new Error("wasm HTML render failed");
      // wasm may grow memory during render: re-read memory.buffer, then copy the
      // output immediately. The pointer is only valid until the next render.
      const length = new DataView(exports.memory.buffer).getUint32(outLenPtr, true);
      const bytes = new Uint8Array(exports.memory.buffer, outPtr, length).slice();
      return new TextDecoder().decode(bytes);
    } finally {
      exports.pathland_wasm_free(inputPtr, Math.max(1, batch.length));
      exports.pathland_wasm_free(outLenPtr, 4);
    }
  };

  return {
    renderDocument: (batch, rootId) => render(batch, rootId, false),
    renderFragment: (batch, rootId) => render(batch, rootId, true),
  };
}
