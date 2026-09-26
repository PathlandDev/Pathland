// Cross-renderer SSR conformance: the TypeScript DOM renderer must reproduce,
// for the same PLPL batches, the exact DOM the canonical Rust SSR renderer
// produces. Fixtures are emitted by `crates/pathland-html-golden`
// (`cargo run -p pathland-html-golden -- --emit` from lib/rust); the Rust side
// is pinned by `pathland-html-golden/tests/guard.rs`. Any divergence between
// the two renderers fails here.

import { beforeEach, describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { applyBatch, type DomRenderer } from "../src/apply";
import { parseBatch, type Batch } from "../src/plpl";

const FIXTURES = resolve(process.cwd(), "test/fixtures/ssr");

/** Full-snapshot scenarios (applied to a fresh DOM and compared to SSR). */
const FULL = ["counter", "form", "composite_controls", "layout", "tokens", "semantics"] as const;

function plpl(name: string): Uint8Array {
  return new Uint8Array(readFileSync(resolve(FIXTURES, `${name}.plpl`)));
}

function html(name: string): string {
  return readFileSync(resolve(FIXTURES, `${name}.html`), "utf8");
}

beforeEach(() => {
  document.head.querySelectorAll("style[data-pathland-tokens]").forEach((s) => s.remove());
});

// --- normalization: attribute order + CSS serialization differ between the
//     SSR string and the runtime DOM, so compare semantic trees. ---

function normalizeStyle(styleAttr: string): string {
  const props = new Map<string, string>();
  for (const decl of styleAttr.split(";")) {
    const idx = decl.indexOf(":");
    if (idx < 0) {
      continue;
    }
    const prop = decl.slice(0, idx).trim().toLowerCase();
    let value = decl.slice(idx + 1).trim().replace(/\s+/g, " ").trim();
    value = value.replace(/^0(\.0+)?px$/, "0");
    // happy-dom re-serializes `rgba(r,g,b,a)` as `rgba(r, g, b, a)` and expands
    // the alpha to e.g. `1.000`; normalize to the SSR renderer's compact form.
    value = value.replace(/rgba\(([^)]*)\)/g, (_m, inner) => {
      const parts = String(inner ?? "").split(",").map((p) => p.trim());
      if (parts.length === 4) {
        const a = Math.round(parseFloat(parts[3] ?? "0") * 1000) / 1000;
        return `rgba(${parts[0]},${parts[1]},${parts[2]},${a})`;
      }
      return `rgba(${inner.trim()})`;
    });
    // CSSOM last-wins: duplicate declarations collapse to the last value.
    props.set(prop, value);
  }
  // happy-dom expands `flex:1` into its longhands on assignment but not on
  // parse — collapse the longhands back to the shorthand for comparison.
  const grow = props.get("flex-grow");
  const shrink = props.get("flex-shrink");
  const basis = props.get("flex-basis");
  if (grow !== undefined && shrink !== undefined && basis !== undefined && !props.has("flex")) {
    props.delete("flex-grow");
    props.delete("flex-shrink");
    props.delete("flex-basis");
    props.set("flex", basis === "0%" ? "1" : `1 1 ${basis}`);
  }
  // Same for a `border-top` set via the CSSOM (expanded into its longhands).
  const btColor = props.get("border-top-color");
  const btStyle = props.get("border-top-style");
  const btWidth = props.get("border-top-width");
  if (btColor !== undefined && btStyle !== undefined && btWidth !== undefined && !props.has("border-top")) {
    props.delete("border-top-color");
    props.delete("border-top-style");
    props.delete("border-top-width");
    props.set("border-top", `${btWidth} ${btStyle} ${btColor}`);
  }
  const out = Array.from(props.entries()).map(([p, v]) => `${p}:${v}`);
  out.sort();
  return out.join(";");
}

/** Attributes whose property state is compared instead (see `canonNode`). */
const CONTROL_ATTRS = new Set(["value", "checked", "selected"]);

function canonAttributes(el: Element): string[] {
  const attrs: string[] = [];
  for (const a of Array.from(el.attributes)) {
    if (CONTROL_ATTRS.has(a.name)) {
      continue;
    }
    if (a.name === "style") {
      attrs.push(`style:${normalizeStyle(a.value)}`);
    } else {
      attrs.push(a.value === "" ? a.name : `${a.name}=${a.value}`);
    }
  }
  attrs.sort();
  return attrs;
}

/** The reflected state of form controls (SSR sets attributes, the runtime sets
 *  properties — both must agree on the effective value). */
function controlState(el: Element): string[] {
  const out: string[] = [];
  const tag = el.tagName.toLowerCase();
  if (el instanceof HTMLInputElement) {
    if (tag === "input" && (el.type === "checkbox" || el.type === "radio")) {
      out.push(`checked:${el.checked}`);
    } else {
      out.push(`value:${el.value}`);
    }
  } else if (el instanceof HTMLTextAreaElement) {
    out.push(`value:${el.value}`);
  } else if (el instanceof HTMLSelectElement) {
    out.push(`value:${el.value}`);
  } else if (el instanceof HTMLOptionElement) {
    out.push(`selected:${el.selected}`);
  } else if (el instanceof HTMLProgressElement) {
    out.push(`progress:${el.value}`);
  }
  return out;
}

function canonNode(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) {
    return `#text:${JSON.stringify(node.textContent ?? "")}`;
  }
  if (node.nodeType === Node.COMMENT_NODE) {
    return "#comment";
  }
  const el = node as Element;
  const attrs = canonAttributes(el).concat(controlState(el));
  const kids = Array.from(el.childNodes).map(canonNode).join("");
  const tag = el.tagName.toLowerCase();
  return `<${tag}${attrs.length > 0 ? " " + attrs.join(" ") : ""}>${kids}</${tag}>`;
}

/** Canonicalize either a DOM element or an SSR HTML fragment. */
function canon(el: HTMLElement | string): string {
  if (typeof el === "string") {
    const host = document.createElement("div");
    host.innerHTML = el;
    return Array.from(host.childNodes).map(canonNode).join("");
  }
  return canonNode(el);
}

/** Apply a full-snapshot batch to a fresh DOM and return the root node. */
function renderFresh(bytes: Uint8Array, rootId: number): HTMLElement {
  const r: DomRenderer = { byId: new Map<number, Node>() };
  applyBatch(parseBatch(bytes), r);
  return r.byId.get(rootId) as HTMLElement;
}

/** Hydrate an SSR fragment into a registry, the way the boot code does. */
function hydrate(fragment: string): DomRenderer {
  const host = document.createElement("div");
  host.innerHTML = fragment;
  const byId = new Map<number, Node>();
  for (const el of host.querySelectorAll<HTMLElement>("[data-pathland-id]")) {
    byId.set(Number(el.getAttribute("data-pathland-id")), el);
  }
  return { byId };
}

describe("SSR conformance (golden fixtures from pathland-html-golden)", () => {
  for (const name of FULL) {
    it(`${name}: fresh-DOM runtime render matches the Rust SSR fragment`, () => {
      const batch: Batch = parseBatch(plpl(name));
      const root = renderFresh(plpl(name), 1);
      expect(canon(root), name).toBe(canon(html(name)));
      expect(batch.opcodes.length).toBeGreaterThan(0);
    });
  }

  it("delta: a hydrated DOM + delta batch matches the post-delta SSR snapshot", () => {
    const r = hydrate(html("delta_base"));
    applyBatch(parseBatch(plpl("delta_change")), r);
    expect(canon(r.byId.get(1) as HTMLElement)).toBe(canon(html("delta_result")));
  });
});