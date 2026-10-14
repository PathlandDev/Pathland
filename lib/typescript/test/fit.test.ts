import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { applyBatch, destroyFitElement, refreshFitSlots } from "../src/apply";
import { parseBatch, readList } from "../src/plpl";
import { buildBatch, stringEntry } from "./plpl.test";
import {
  CAT_EVENT,
  CAT_PARAMETER,
  CAT_TREE,
  CMD_CREATE_NODE,
  CMD_DELETE_NODE,
  CMD_FIT_CHANGED,
  CMD_INSERT_CHILD,
  CMD_REMOVE_CHILD,
  CMD_SET_PROPERTY,
  PROP_FIT_QUERY,
  VAL_LIST,
} from "../src/constants";
import type { DomRenderer } from "../src/apply";

const f32bits = (v: number): number => new Uint32Array(new Float32Array([v]).buffer)[0] ?? 0;

/** A `[u32 count][f32 × count]` LIST arena entry. */
function listEntry(values: number[]): Uint8Array {
  const out = new Uint8Array(4 + 4 * values.length);
  const view = new DataView(out.buffer);
  view.setUint32(0, values.length, true);
  values.forEach((v, i) => view.setUint32(4 + 4 * i, f32bits(v), true));
  return out;
}

/** A controllable ResizeObserver stand-in (happy-dom may not define one). */
class FakeResizeObserver {
  static instances: FakeResizeObserver[] = [];
  callback: ResizeObserverCallback;
  disconnected = false;
  constructor(callback: ResizeObserverCallback) {
    this.callback = callback;
    FakeResizeObserver.instances.push(this);
  }
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {
    this.disconnected = true;
  }
  trigger(): void {
    this.callback([], this as unknown as ResizeObserver);
  }
}

const SVG_RECT = { toJSON: () => "" } as unknown as DOMRect;

function renderer(onFit?: (batch: Uint8Array) => void): DomRenderer {
  const r: DomRenderer = { byId: new Map() };
  if (onFit) {
    r.onFitEvent = onFit;
  }
  return r;
}

beforeEach(() => {
  document.body.innerHTML = ""; // isolate each test's DOM (fitted slots, icons)
  FakeResizeObserver.instances = [];
  (globalThis as Record<string, unknown>).ResizeObserver = FakeResizeObserver;
});

afterEach(() => {
  vi.unstubAllGlobals();
  delete (globalThis as Record<string, unknown>).ResizeObserver;
});

describe("SizeThatFits · FIT_QUERY + local fit → FIT_CHANGED", () => {
  it("readList decodes an `[count][f32×count]` arena entry", () => {
    const batch = parseBatch(buildBatch([], listEntry([0, 640])));
    // A batch with only an arena entry: parse the bytes directly.
    const list = readList(listEntry([0, 640]), 0);
    expect(list).toEqual([0, 640]);
    void batch;
  });

  it("applies FIT_QUERY, mirrors data-pathland-fit, and reports the first fit index", () => {
    const el = document.createElement("div");
    el.setAttribute("data-pathland-id", "1");
    document.body.appendChild(el);
    const sent: Uint8Array[] = [];
    const r = renderer((batch) => sent.push(batch));
    r.byId.set(1, el);
    vi.spyOn(el, "getBoundingClientRect").mockReturnValue({
      ...SVG_RECT, width: 400, height: 0, top: 0, left: 0, right: 400, bottom: 0,
    } as unknown as DOMRect);

    applyBatch(
      parseBatch(
        buildBatch([[CAT_PARAMETER, CMD_SET_PROPERTY, 0, 1, (VAL_LIST << 16) | PROP_FIT_QUERY, 0]], listEntry([0, 640])),
      ),
      r,
    );

    expect(el.getAttribute("data-pathland-fit")).toBe("0,640");
    // The first measure reports once: width 400 in band index 0.
    const first: BatchGlimpse = decode(sent[0]!);
    expect(first.category).toBe(CAT_EVENT);
    expect(first.command).toBe(CMD_FIT_CHANGED);
    expect(first.a).toBe(1);
    expect(first.indexBits).toBe(f32bits(0));
    const observer = FakeResizeObserver.instances[0];
    expect(observer).toBeDefined();
  });

  it("reports only transitions: width band change sends, same band stays silent", () => {
    const el = document.createElement("div");
    el.setAttribute("data-pathland-id", "2");
    document.body.appendChild(el);
    const sent: Uint8Array[] = [];
    const r = renderer((batch) => sent.push(batch));
    r.byId.set(2, el);
    const rect = { width: 400, height: 0, top: 0, left: 0, right: 400, bottom: 0 } as unknown as DOMRect;
    vi.spyOn(el, "getBoundingClientRect").mockImplementation(() => rect);
    applyBatch(
      parseBatch(
        buildBatch([[CAT_PARAMETER, CMD_SET_PROPERTY, 0, 2, (VAL_LIST << 16) | PROP_FIT_QUERY, 0]], listEntry([0, 640])),
      ),
      r,
    );
    const observer = FakeResizeObserver.instances[0]!;
    const count = sent.length;
    expect(count).toBe(1); // initial: index 0

    observer.trigger(); // still 400 → band 0, no report
    expect(sent).toHaveLength(count);

    rect.width = 800; // band 1 (wide)
    observer.trigger();
    expect(sent).toHaveLength(count + 1);
    expect(decode(sent[sent.length - 1]!).indexBits).toBe(f32bits(1));

    observer.trigger(); // still wide → silent
    expect(sent).toHaveLength(count + 1);

    rect.width = 100; // back to the fallback band
    observer.trigger();
    expect(sent).toHaveLength(count + 2);
    expect(decode(sent[sent.length - 1]!).indexBits).toBe(f32bits(0));
  });

  it("a removed slot stops observing and reporting", () => {
    const el = document.createElement("div");
    el.setAttribute("data-pathland-id", "3");
    document.body.appendChild(el);
    const sent: Uint8Array[] = [];
    const r = renderer((batch) => sent.push(batch));
    r.byId.set(3, el);
    applyBatch(
      parseBatch(
        buildBatch([[CAT_PARAMETER, CMD_SET_PROPERTY, 0, 3, (VAL_LIST << 16) | PROP_FIT_QUERY, 0]], listEntry([0, 640])),
      ),
      r,
    );
    const observer = FakeResizeObserver.instances[0]!;

    applyBatch(parseBatch(buildBatch([[CAT_TREE, CMD_DELETE_NODE, 0, 3]])), r);
    expect(observer.disconnected).toBe(true);
    const after = sent.length;
    observer.trigger();
    expect(sent).toHaveLength(after); // no reports after teardown
    destroyFitElement(el); // idempotent
  });

  it("refreshFitSlots re-reports the current fit after the socket opens", () => {
    // A first load: FIT_QUERY arrives before the WS handshake finishes, so the
    // report is dropped (no onFitEvent yet) but lastIndex is still set — without
    // refreshFitSlots the ResizeObserver would stay silent on an unchanged width.
    const el = document.createElement("div");
    el.setAttribute("data-pathland-id", "7");
    document.body.appendChild(el);
    const r = renderer(); // no onFitEvent yet — the boot report would be dropped
    r.byId.set(7, el);
    applyBatch(
      parseBatch(
        buildBatch([[CAT_PARAMETER, CMD_SET_PROPERTY, 0, 7, (VAL_LIST << 16) | PROP_FIT_QUERY, 0]], listEntry([0, 640])),
      ),
      r,
    );
    expect(el.getAttribute("data-pathland-fit")).toBe("0,640");

    // Socket opens: the sink is now wired; refreshFitSlots must force one report.
    const sent: Uint8Array[] = [];
    r.onFitEvent = (batch) => sent.push(batch);
    refreshFitSlots(r);

    expect(sent).toHaveLength(1);
    expect(decode(sent[0]!).command).toBe(CMD_FIT_CHANGED);
    expect(decode(sent[0]!).a).toBe(7);
  });

  it("collapses an SSR snapshot fit slot (N candidates → single child) on the first structural op", () => {
    // The web SSR rendered the full snapshot form (issue #141): a fit slot
    // carrying EVERY candidate, each behind a `data-pathland-fit-cand` wrapper,
    // gated by a `@container` band-rule `<style data-pathland-fit>`. The first
    // live structural op is the FIT_CHANGED-driven downgrade to one child; the
    // client must unwrap the gated wrappers and strip container-name/cands +
    // the slot's band rules so the N→1 deltas apply to plain children.
    const style = document.createElement("style");
    style.setAttribute("data-pathland-fit", "");
    style.textContent = [
      "@container pf-2 (min-width: 640px) { [data-pathland-id=\"2\"] > [data-pathland-fit-cand=\"0\"] { display: none !important; } [data-pathland-id=\"2\"] > [data-pathland-fit-cand=\"1\"] { display: contents !important; } }",
      "@container pf-20 (min-width: 600px) { /* another slot — must survive (id boundary) */ }",
    ].join("\n");
    document.head.appendChild(style);

    const slot = document.createElement("div");
    slot.setAttribute("data-pathland-id", "2");
    slot.setAttribute("data-pathland-fit-cands", "2");
    slot.className = "pathland-ftf";
    slot.style.cssText = "display:flex;container-type:inline-size;container-name:pf-2;";
    // Candidate 2 (wide) hidden by default; candidate 3 (compact) = the fallback.
    const c0 = document.createElement("div");
    c0.setAttribute("data-pathland-fit-cand", "0");
    c0.innerHTML = '<div data-pathland-id="3"><span data-pathland-id="4">compact</span></div>';
    const c1 = document.createElement("div");
    c1.setAttribute("data-pathland-fit-cand", "1");
    c1.innerHTML = '<div data-pathland-id="5">wide</div>';
    slot.append(c0, c1);
    document.body.appendChild(slot);

    const r: DomRenderer = { byId: new Map() };
    r.byId.set(2, slot);
    r.byId.set(3, c0.firstElementChild as Element);
    r.byId.set(4, (c0.firstElementChild as Element).firstElementChild as Element);
    r.byId.set(5, c1.firstElementChild as Element);

    // The downgrade: remove candidate 5 (wide was never selected), then insert a
    // fresh single child under the slot.
    applyBatch(
      parseBatch(
        buildBatch([
          [CAT_TREE, CMD_DELETE_NODE, 0, 5],
          [CAT_TREE, CMD_DELETE_NODE, 0, 4],
        ]),
      ),
      r,
    );

    // The snapshot wrappers are unwrapped (no gated divs left under the slot),
    // and the slot is back to the plain single-child form.
    expect(slot.querySelector("[data-pathland-fit-cand]")).toBeNull();
    expect(slot.hasAttribute("data-pathland-fit-cands")).toBe(false);
    expect(slot.style.containerType).toBe("");
    expect(slot.style.containerName).toBe("");
    // The `data-pathland-fit-cands` marker survives on the retained candidate.
    expect(c0.querySelector('[data-pathland-id="4"]')).toBeNull(); // deleted
    // The slot keeps its surviving child and its fit observer (the JS fallback).
    expect(slot.children.length).toBe(1);
    // The touched slot's band rules are dropped; another slot's rules survive —
    // including a multi-digit id (`pf-20`) that shares the `pf-2` prefix.
    const kept = style.textContent ?? "";
    expect(kept).not.toContain("min-width: 640px");
    expect(kept).toContain("pf-20 (min-width: 600px)");
  });

  it("collapses a same-component snapshot downgrade, retaining the selected candidate", () => {
    // The server's clean N→1 (issue #141): the slot carried two candidates (both
    // the SAME component, like the demo's compact/wide rows); on the first
    // FIT_CHANGED it removes the unselected candidate (2) and KEEPS the selected
    // one (115, at its FIT_QUERY position). The client must tear down the gate and
    // leave the retained candidate as the slot's single child.
    const slot = document.createElement("div");
    slot.setAttribute("data-pathland-id", "1");
    slot.setAttribute("data-pathland-fit", "0,1024");
    slot.setAttribute("data-pathland-fit-cands", "2");
    slot.style.cssText = "display:flex;container-type:inline-size;container-name:pf-1;";
    const c0 = document.createElement("div");
    c0.setAttribute("data-pathland-fit-cand", "0");
    c0.setAttribute("data-pathland-fit-cand-id", "2");
    c0.style.display = "contents";
    c0.innerHTML = '<div data-pathland-id="2" style="display:flex;flex-direction:row;"><div data-pathland-id="3">main</div></div>';
    const c1 = document.createElement("div");
    c1.setAttribute("data-pathland-fit-cand", "1");
    c1.setAttribute("data-pathland-fit-cand-id", "115");
    c1.style.display = "none";
    c1.innerHTML = '<div data-pathland-id="115" style="display:flex;flex-direction:row;"><div data-pathland-id="116">main</div><div data-pathland-id="228">side</div></div>';
    slot.append(c0, c1);
    document.body.appendChild(slot);

    const r = renderer();
    r.byId.set(1, slot);
    r.byId.set(2, c0.firstElementChild as Element);
    r.byId.set(3, (c0.firstElementChild as Element).firstElementChild as Element);
    r.byId.set(115, c1.firstElementChild as Element);
    r.byId.set(116, (c1.firstElementChild as Element).children[0] as Element);
    r.byId.set(228, (c1.firstElementChild as Element).children[1] as Element);

    applyBatch(
      parseBatch(
        buildBatch([
          [CAT_TREE, CMD_REMOVE_CHILD, 0, 1, 2],
          [CAT_TREE, CMD_DELETE_NODE, 0, 2],
        ]),
      ),
      r,
    );

    expect(slot.querySelector("[data-pathland-fit-cand]")).toBeNull();
    expect(slot.hasAttribute("data-pathland-fit-cands")).toBe(false);
    expect(slot.style.containerName).toBe("");
    expect(slot.children.length).toBe(1);
    expect((slot.children[0] as HTMLElement).getAttribute("data-pathland-id")).toBe("115");
  });

  it("also tears down the gate on a same-component positional-morph downgrade", () => {
    // Defensive: even if a server relocated the winner into the loser's wrapper
    // (a positional id-reuse morph), the client must still tear the gate down.
    const slot = document.createElement("div");
    slot.setAttribute("data-pathland-id", "1");
    slot.setAttribute("data-pathland-fit", "0,1024");
    slot.setAttribute("data-pathland-fit-cands", "2");
    slot.style.cssText = "display:flex;container-type:inline-size;container-name:pf-1;";
    const c0 = document.createElement("div");
    c0.setAttribute("data-pathland-fit-cand", "0");
    c0.setAttribute("data-pathland-fit-cand-id", "2");
    c0.style.display = "contents";
    c0.innerHTML = '<div data-pathland-id="2" style="display:flex;flex-direction:row;"><div data-pathland-id="3">main</div></div>';
    const c1 = document.createElement("div");
    c1.setAttribute("data-pathland-fit-cand", "1");
    c1.setAttribute("data-pathland-fit-cand-id", "115");
    c1.style.display = "none";
    c1.innerHTML = '<div data-pathland-id="115" style="display:flex;flex-direction:row;"><div data-pathland-id="116">main</div><div data-pathland-id="228">side</div></div>';
    slot.append(c0, c1);
    document.body.appendChild(slot);

    const r = renderer();
    r.byId.set(1, slot);
    r.byId.set(2, c0.firstElementChild as Element);
    r.byId.set(3, (c0.firstElementChild as Element).firstElementChild as Element);
    r.byId.set(115, c1.firstElementChild as Element);
    r.byId.set(116, (c1.firstElementChild as Element).children[0] as Element);
    r.byId.set(228, (c1.firstElementChild as Element).children[1] as Element);

    applyBatch(
      parseBatch(
        buildBatch([
          [CAT_TREE, CMD_CREATE_NODE, 0, 263, 0x10],
          [CAT_TREE, CMD_INSERT_CHILD, 0, 2, 263, 1],
          [CAT_TREE, CMD_REMOVE_CHILD, 0, 1, 115],
          [CAT_TREE, CMD_DELETE_NODE, 0, 115],
        ]),
      ),
      r,
    );

    expect(slot.querySelector("[data-pathland-fit-cand]")).toBeNull();
    expect(slot.hasAttribute("data-pathland-fit-cands")).toBe(false);
  });

  it("keeps the gate on a resync replay (INSERT_CHILD of an existing candidate)", () => {
    // A META::RESYNC replays the whole tree as opcodes. For a snapshot slot the
    // client already holds the SSR gate (wrappers + bands); the replay's
    // CREATE/INSERT must leave it intact so the CSS band keeps showing the
    // width-correct candidate (slim window → candidate 0; wide → candidate 1).
    const slot = document.createElement("div");
    slot.setAttribute("data-pathland-id", "1");
    slot.setAttribute("data-pathland-fit", "0,1024");
    slot.setAttribute("data-pathland-fit-cands", "2");
    slot.style.cssText = "display:flex;container-type:inline-size;container-name:pf-1;";
    const c0 = document.createElement("div");
    c0.setAttribute("data-pathland-fit-cand", "0");
    c0.setAttribute("data-pathland-fit-cand-id", "2");
    c0.style.display = "contents";
    c0.innerHTML = '<div data-pathland-id="2" style="display:flex;flex-direction:row;"><div data-pathland-id="3">main</div></div>';
    const c1 = document.createElement("div");
    c1.setAttribute("data-pathland-fit-cand", "1");
    c1.setAttribute("data-pathland-fit-cand-id", "115");
    c1.style.display = "none";
    c1.innerHTML = '<div data-pathland-id="115" style="display:flex;flex-direction:row;"><div data-pathland-id="116">main</div><div data-pathland-id="228">side</div></div>';
    slot.append(c0, c1);
    document.body.appendChild(slot);

    const r = renderer();
    r.byId.set(1, slot);
    r.byId.set(2, c0.firstElementChild as Element);
    r.byId.set(3, (c0.firstElementChild as Element).firstElementChild as Element);
    r.byId.set(115, c1.firstElementChild as Element);
    r.byId.set(116, (c1.firstElementChild as Element).children[0] as Element);
    r.byId.set(228, (c1.firstElementChild as Element).children[1] as Element);

    // A resync replay: CREATE the (already-present) slot + insert an existing
    // candidate. Neither is the downgrade — the gate must survive.
    applyBatch(
      parseBatch(
        buildBatch([
          [CAT_TREE, CMD_CREATE_NODE, 0, 1, 0x17],
          [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 2, 0],
          [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 115, 1],
        ]),
      ),
      r,
    );

    expect(slot.hasAttribute("data-pathland-fit-cands")).toBe(true);
    expect(slot.style.containerName).toBe("pf-1");
    expect(Array.from(slot.children).filter((c) => c.hasAttribute("data-pathland-fit-cand")).length).toBe(2);
  });

  it("keeps the gate when a NON-candidate node is removed under a snapshot slot", () => {
    // A fit swap (e.g. a rotate crossing a band) can emit REMOVE_CHILD/DELETE_NODE
    // for a node INSIDE a candidate — the bar's wide→compact swap drops the volume
    // slider + a divider — while the slot is still in the SSR snapshot form. That is
    // NOT the N→1 downgrade (no candidate is removed): the gate must survive, or
    // unwrapping it would expose EVERY candidate (the "all branches rendered" bug).
    const style = document.createElement("style");
    style.setAttribute("data-pathland-fit", "");
    style.textContent =
      "@container pf-1 (min-width: 1024px) { [data-pathland-id=\"1\"] > [data-pathland-fit-cand=\"0\"] { display: none !important; } [data-pathland-id=\"1\"] > [data-pathland-fit-cand=\"1\"] { display: contents !important; } }";
    document.head.appendChild(style);

    const slot = document.createElement("div");
    slot.setAttribute("data-pathland-id", "1");
    slot.setAttribute("data-pathland-fit", "0,1024");
    slot.setAttribute("data-pathland-fit-cands", "2");
    slot.style.cssText = "display:flex;container-type:inline-size;container-name:pf-1;";
    const c0 = document.createElement("div");
    c0.setAttribute("data-pathland-fit-cand", "0");
    c0.setAttribute("data-pathland-fit-cand-id", "2");
    c0.style.display = "contents";
    c0.innerHTML = '<div data-pathland-id="2" style="display:flex;flex-direction:row;"><div data-pathland-id="3">main</div><div data-pathland-id="4">volume</div></div>';
    const c1 = document.createElement("div");
    c1.setAttribute("data-pathland-fit-cand", "1");
    c1.setAttribute("data-pathland-fit-cand-id", "115");
    c1.style.display = "none";
    c1.innerHTML = '<div data-pathland-id="115" style="display:flex;flex-direction:row;"><div data-pathland-id="116">main</div></div>';
    slot.append(c0, c1);
    document.body.appendChild(slot);

    const r = renderer();
    r.byId.set(1, slot);
    r.byId.set(2, c0.firstElementChild as Element);
    r.byId.set(3, (c0.firstElementChild as Element).children[0] as Element);
    r.byId.set(4, (c0.firstElementChild as Element).children[1] as Element);
    r.byId.set(115, c1.firstElementChild as Element);
    r.byId.set(116, (c1.firstElementChild as Element).children[0] as Element);

    // Remove a NON-candidate node (id 4, a child inside candidate 0).
    applyBatch(
      parseBatch(
        buildBatch([
          [CAT_TREE, CMD_REMOVE_CHILD, 0, 2, 4],
          [CAT_TREE, CMD_DELETE_NODE, 0, 4],
        ]),
      ),
      r,
    );

    // The gate SURVIVES (still gated, still two gated wrappers, band rules kept).
    expect(slot.hasAttribute("data-pathland-fit-cands")).toBe(true);
    expect(slot.style.containerName).toBe("pf-1");
    expect(Array.from(slot.children).filter((c) => c.hasAttribute("data-pathland-fit-cand")).length).toBe(2);
    expect(style.textContent).toContain("min-width: 1024px");
  });
});

interface BatchGlimpse {
  category: number;
  command: number;
  a: number;
  indexBits: number;
}

function decode(batch: Uint8Array): BatchGlimpse {
  const parsed = parseBatch(batch);
  const op = parsed.opcodes[0]!;
  return { category: op.category, command: op.command, a: op.a, indexBits: op.b };
}

void stringEntry;