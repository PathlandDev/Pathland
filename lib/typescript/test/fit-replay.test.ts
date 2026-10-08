import { describe, expect, it } from "vitest";
import { applyBatch } from "../src/apply";
import { parseBatch } from "../src/plpl";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import type { DomRenderer } from "../src/apply";

// Real frames captured from the Java emitter (MusicPlayerView, Spring SSR path):
// the COMPACT mount (root = SIZE_THAT_FITS fit slot, threshold-0 child) and the
// compact→wide fit swap. Replaying them applies the app's actual fit transition.
const MOUNT = hex(read("mount.hex"));
const SWAP_WIDE = hex(read("swap-wide.hex"));

function read(name: string): string {
  const here = dirname(fileURLToPath(import.meta.url));
  return readFileSync(resolve(here, "fixtures/fit", name), "utf8").trim();
}

function hex(h: string): Uint8Array {
  const out = new Uint8Array(h.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(h.slice(i * 2, i * 2 + 2), 16);
  return out;
}

describe("SizeThatFits swap replay (real MusicPlayerView frames)", () => {
  it("hydrates the compact root, then the compact→wide fit swap applies cleanly", () => {
    document.body.innerHTML = "";
    const r: DomRenderer = { byId: new Map() };
    applyBatch(parseBatch(MOUNT), r);
    const root = r.byId.get(1);
    expect(root).toBeTruthy();
    document.body.appendChild(root as Node);

    expect(() => applyBatch(parseBatch(SWAP_WIDE), r)).not.toThrow();
    // The wide row now carries the now-playing sidebar; the compact library
    // content is present too (a FIRST-paint dependency: no sidebar pre-swap).
    expect(document.body.textContent ?? "").toContain("Now Playing");
    expect(document.body.textContent ?? "").toContain("Library");
    expect(document.querySelectorAll("[data-pathland-id]").length).toBeGreaterThan(40);
  });
});