import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import {
  BatchWriter,
  CAT_PARAMETER,
  CAT_TREE,
  CMD_CREATE_NODE,
  CMD_SET_TEXT,
  COMPONENT_TEXT,
} from "@pathland/protocol";
import { loadSsrRenderer, renderPage, resolveBundleName } from "../src/index";

function textFrame(text: string): Uint8Array {
  const writer = new BatchWriter();
  writer.begin();
  writer.push(CAT_TREE, CMD_CREATE_NODE, 0, 1, COMPONENT_TEXT, 0);
  const offset = writer.string(text);
  writer.push(CAT_PARAMETER, CMD_SET_TEXT, 0, 1, offset, 0);
  return writer.end();
}

describe("@pathland/ssr", () => {
  it("renders a document and a fragment through the Rust wasm renderer", async () => {
    const wasmPath = fileURLToPath(new URL("../wasm/pathland_render_html.wasm", import.meta.url));
    const renderer = await loadSsrRenderer(readFileSync(wasmPath));
    const document = renderer.renderDocument(textFrame("Hello & <world>"), 1);
    expect(document).toContain("<!DOCTYPE html>");
    expect(document).toContain('data-pathland-id="1"');
    expect(document).toContain("Hello &amp; &lt;world&gt;");

    const fragment = renderer.renderFragment(textFrame("fragment"), 1);
    expect(fragment).not.toContain("<!DOCTYPE html>");
    expect(fragment).toContain("fragment");
  });

  it("does not leak output between consecutive renders", async () => {
    const wasmPath = fileURLToPath(new URL("../wasm/pathland_render_html.wasm", import.meta.url));
    const renderer = await loadSsrRenderer(readFileSync(wasmPath));
    expect(renderer.renderDocument(textFrame("first"), 1)).toContain("first");
    const second = renderer.renderDocument(textFrame("second"), 1);
    expect(second).toContain("second");
    expect(second).not.toContain("first");
  });

  it("injects the base, bundle, and SSR scope flags", () => {
    const html = renderPage("<html><body><main>x</main></body></html>", {
      base: "/_pathland",
      bundleName: "dom-renderer-abc.js",
      serviceWorker: true,
      ssrScoped: true,
    });
    expect(html).toContain('<html data-pathland-base="/_pathland" data-pathland-service-worker="true" data-pathland-ssr-scoped="true">');
    expect(html).toContain('<script src="/_pathland/dom-renderer-abc.js" defer></script></body>');
  });

  it("resolves the current bundle manifest", () => {
    const dir = fileURLToPath(new URL("../../ssr/wasm", import.meta.url));
    expect(resolveBundleName(dir)).toBe("dom-renderer.js");
  });
});
