// Lazy web-icon glyph resolution. SSR inlines every rendered icon's `<path>`
// in the page, so first render is fully embedded — no network, no cache
// dependency. The DOM client only needs glyph data when a delta asks for an
// `ICON_NAME` that the page has *not* embedded. Instead of shipping the full
// canonical catalog in the bundle (~13KB), the glyph is resolved lazily:
//   1. the JS memo cache (this session),
//   2. an already-embedded `svg.pathland-icon[data-pathland-icon=name]`
//      elsewhere in the page — its inner markup, zero bytes and zero network,
//   3. the renderer-owned `/_pathland/icons/<name>.svg` the host serves from
//      the shared Rust library (`pathland_html_icon_svg` over the C ABI — the
//      same source SSR inlines from), fetched once then memoized + HTTP-cached.
// Unknown/extension names and fetch failures resolve to the fallback glyph.

const cache = new Map<string, string>();
const inflight = new Map<string, Promise<string>>();
const current = new WeakMap<Element, string>();

/** The reserved framework root (`/_pathland` by default; relocated via the SSR page's `data-pathland-base`). */
function basePath(): string {
  return document.documentElement.dataset.pathlandBase ?? "/_pathland";
}

/** The decorative fallback glyph (a faint circle) — the renderer's `web_fallback_svg()`. */
export const FALLBACK_SVG = '<circle cx="12" cy="12" r="8"/>';

/** The inner markup of an already-embedded icon svg for `name`, if present. */
function embeddedGlyph(name: string): string | undefined {
  for (const svg of document.querySelectorAll("svg.pathland-icon[data-pathland-icon]")) {
    if (svg.getAttribute("data-pathland-icon") === name) {
      return svg.innerHTML;
    }
  }
  return undefined;
}

async function fetchGlyph(name: string): Promise<string> {
  try {
    const url = new URL(`${basePath()}/icons/${encodeURIComponent(name)}.svg`, document.baseURI);
    const res = await fetch(url.href);
    if (!res.ok) return FALLBACK_SVG;
    const text = (await res.text()).trim();
    // The served file is the same markup SSR inlines: `<svg …>…inner…</svg>`.
    const inner = text.slice(text.indexOf(">") + 1, text.lastIndexOf("</svg>"));
    return inner || FALLBACK_SVG;
  } catch {
    return FALLBACK_SVG;
  }
}

/**
 * Ensure a glyph is cached for `name` (resolves to it, or the fallback on
 * failure/unknown). Deduplicated: concurrent callers share one fetch.
 */
export function preloadIcon(name: string): Promise<string> {
  const existing = inflight.get(name);
  if (existing) return existing;
  const done = fetchGlyph(name).then((glyph) => {
    cache.set(name, glyph);
    inflight.delete(name);
    return glyph;
  });
  inflight.set(name, done);
  return done;
}

/**
 * Set/swap an icon node's glyph: memo cache → an embedded sibling svg → the
 * fallback now, then the fetched glyph when it arrives (only if the element
 * still shows `name`). All synchronous calls are instant; the only async path
 * is a name the page never embedded.
 */
export function setIcon(svg: Element, name: string): void {
  if (!svg) return;
  current.set(svg, name);
  if (!name) {
    svg.removeAttribute("data-pathland-icon");
    svg.innerHTML = FALLBACK_SVG;
    return;
  }
  // Mirror the SSR svg's `data-pathland-icon` so the embedded-glyph seed can
  // find this element (and the conformance canon stays in parity too).
  svg.setAttribute("data-pathland-icon", name);
  const memo = cache.get(name);
  if (memo !== undefined) {
    svg.innerHTML = memo;
    return;
  }
  const embedded = embeddedGlyph(name);
  if (embedded !== undefined) {
    svg.innerHTML = embedded;
    return;
  }
  svg.innerHTML = FALLBACK_SVG;
  void preloadIcon(name).then((glyph) => {
    if (current.get(svg) === name) svg.innerHTML = glyph;
  });
}