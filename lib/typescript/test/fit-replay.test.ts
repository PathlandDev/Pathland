import { describe, expect, it } from "vitest";
import { applyBatch } from "../src/apply";
import { parseBatch } from "../src/plpl";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import type { DomRenderer } from "../src/apply";

// Real frames captured from the Java emitter (MusicPlayerView, Spring SSR path):
// the COMPACT mount, and the HSTACK-wrapper fit cycles (compact→wide→narrow→wide).
const MOUNT = hex(read("mount.hex"));
const WIDE = hex(read("swap-wide.hex"));
const NARROW = hex(read("swap-narrow.hex"));
const WIDE2 = hex(read("swap-wide2.hex"));

function read(name: string): string {
  const here = dirname(fileURLToPath(import.meta.url));
  return readFileSync(resolve(here, "fixtures/fit", name), "utf8").trim();
}

function hex(h: string): Uint8Array {
  const out = new Uint8Array(h.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(h.slice(i * 2, i * 2 + 2), 16);
  return out;
}

function attachCompact(r: DomRenderer): HTMLElement {
  document.body.innerHTML = "";
  applyBatch(parseBatch(MOUNT), r);
  const root = r.byId.get(1) as HTMLElement;
  expect(root).toBeTruthy();
  document.body.appendChild(root);
  return root;
}

/** The fit row (the slot's only child) and, for a sidebar, its text. */
function rowOf(root: HTMLElement): HTMLElement {
  return root.children[0] as HTMLElement;
}

function directChildTexts(row: HTMLElement): string[] {
  return Array.from(row.children).map((c) => (c as HTMLElement).textContent ?? "");
}

function rowSidebarCount(row: HTMLElement): number {
  return Array.from(row.children).filter((c) => (c.textContent ?? "").includes("Now Playing")).length;
}

describe("SizeThatFits swap replay (real MusicPlayerView frames)", () => {
  it("wide swap appends the sidebar AFTER the main area (trailing, right)", () => {
    const r: DomRenderer = { byId: new Map() };
    const root = attachCompact(r);

    expect(() => applyBatch(parseBatch(WIDE), r)).not.toThrow();

    const row = rowOf(root);
    const texts = directChildTexts(row);
    const libraryIdx = texts.findIndex((t) => t.includes("Library"));
    const nowPlayingIdx = texts.findIndex((t) => t.includes("Now Playing"));
    expect(libraryIdx).toBeGreaterThanOrEqual(0);
    expect(nowPlayingIdx).toBeGreaterThan(libraryIdx),
      `sidebar must come AFTER the main area; row order was: ${JSON.stringify(texts.map((t) => t.slice(0, 24)))}`;
  });

  it("narrow->wide cycles leave exactly ONE sidebar and keep it trailing", () => {
    const r: DomRenderer = { byId: new Map() };
    const root = attachCompact(r);

    applyBatch(parseBatch(WIDE), r);
    applyBatch(parseBatch(NARROW), r);
    applyBatch(parseBatch(WIDE2), r);

    const row = rowOf(root);
    expect(rowSidebarCount(row)).toBe(1),
      "no duplicate now-playing bar after a full wide->narrow->wide cycle";
    const texts = directChildTexts(row);
    const lib = texts.findIndex((t) => t.includes("Library"));
    const np = texts.findIndex((t) => t.includes("Now Playing"));
    expect(lib).toBeGreaterThanOrEqual(0);
    expect(np).toBeGreaterThan(lib), `sidebar stays trailing: ${JSON.stringify(texts)}`;
  });
});