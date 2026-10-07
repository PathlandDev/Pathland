import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { setIcon } from "../src/icons";

const SVG_NS = "http://www.w3.org/2000/svg";

function iconSvg(): SVGSVGElement {
  const svg = document.createElementNS(SVG_NS, "svg");
  svg.setAttribute("class", "pathland-icon");
  return svg;
}

/** Drop an "already-embedded" SSR-style glyph into the document (what a hydrated page carries). */
function seedGlyph(name: string, inner: string): SVGSVGElement {
  const svg = iconSvg();
  svg.setAttribute("data-pathland-icon", name);
  svg.innerHTML = inner;
  document.body.appendChild(svg);
  return svg;
}

function fetchStub(body?: string, ok = true) {
  const stub = vi.fn(async (_url: unknown) => {
    await new Promise((r) => setTimeout(r, 0));
    return ok ? { ok: true, text: async () => body ?? "" } : { ok: false, text: async () => "" };
  });
  vi.stubGlobal("fetch", stub);
  return stub;
}

const ENGINE_SHELL = '<svg class="pathland-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">';
const PLAY_PATH = '<path d="M19.376 12.4161L8.77735 19.4818L22.3137 3.5"/>';

beforeEach(() => {
  document.body.innerHTML = "";
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("setIcon · embedded + lazy fetch", () => {
  it("reads an already-embedded sibling glyph synchronously with no fetch", () => {
    const seeded = seedGlyph("music", '<path d="M9 1"/>');
    const fetch = fetchStub();
    const target = iconSvg();

    setIcon(target, "music");

    expect(target.innerHTML).toContain('d="M9 1"');
    expect(target.getAttribute("data-pathland-icon")).toBe("music");
    expect(fetch).not.toHaveBeenCalled();
    seeded.remove();
  });

  it("fetches a never-embedded glyph once, caching it for later swaps", async () => {
    const fetch = fetchStub(ENGINE_SHELL + PLAY_PATH + "</svg>");
    const target = iconSvg();

    setIcon(target, "play");
    // Synchronously the fallback shows (no embedded copy on the page)…
    expect(target.innerHTML).toContain("circle");
    expect(target.getAttribute("data-pathland-icon")).toBe("play");
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(String(fetch.mock.calls[0]?.[0])).toMatch(/\/icons\/play\.svg$/);

    // …then the fetched inner markup lands (memoized for the session).
    await vi.waitFor(() => expect(target.innerHTML).toContain('d="M19.376'));
    expect(fetch).toHaveBeenCalledTimes(1);

    // A later swap to the same name is instant — no second fetch.
    const again = iconSvg();
    setIcon(again, "play");
    expect(again.innerHTML).toContain('d="M19.376');
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("dedupes concurrent lookups into a single fetch that patches every requester", async () => {
    const fetch = fetchStub(ENGINE_SHELL + PLAY_PATH + "</svg>");
    const a = iconSvg();
    const b = iconSvg();

    setIcon(a, "shuffle");
    setIcon(b, "shuffle");

    await vi.waitFor(() => expect(a.innerHTML).toContain('d="M19.376'), { timeout: 2000 });
    await vi.waitFor(() => expect(b.innerHTML).toContain('d="M19.376'), { timeout: 2000 });
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("maps unknown names and fetch failures to the fallback without refetching", async () => {
    const fetch = fetchStub(undefined, false); // 404
    const target = iconSvg();

    setIcon(target, "no-such-icon");
    expect(target.innerHTML).toContain("circle");
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));

    // The failure is memoized as the fallback — a repeated swap refetches nothing.
    const again = iconSvg();
    setIcon(again, "no-such-icon");
    expect(again.innerHTML).toContain("circle");
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("an empty name clears the glyph and the embedding attr", () => {
    const target = iconSvg();
    target.innerHTML = '<path d="M1"/>';
    target.setAttribute("data-pathland-icon", "play");

    setIcon(target, "");

    expect(target.innerHTML).toContain("circle");
    expect(target.hasAttribute("data-pathland-icon")).toBe(false);
  });
});