import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { applyBatch, destroyFitElement, refreshFitSlots } from "../src/apply";
import { parseBatch, readList } from "../src/plpl";
import { buildBatch, stringEntry } from "./plpl.test";
import {
  CAT_EVENT,
  CAT_PARAMETER,
  CAT_TREE,
  CMD_DELETE_NODE,
  CMD_FIT_CHANGED,
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