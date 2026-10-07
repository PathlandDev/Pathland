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

/**
 * Memorize every glyph the page has embedded — the SSR inlines each rendered
 * icon, so a name that was ever on screen is capturable without a fetch. The
 * seeds are DOM-only, so without this the very element being swapped would be
 * the only play glyph and flipping it to pause would make the return trip to
 * play a (cached) fetch. Skips empty/shadowed contents (a mid-fill element).
 */
function harvestEmbedded(): void {
  for (const svg of document.querySelectorAll("svg.pathland-icon[data-pathland-icon]")) {
    const name = svg.getAttribute("data-pathland-icon");
    if (!name || cache.has(name)) continue;
    const glyph = svg.innerHTML.trim();
    if (glyph) cache.set(name, glyph);
  }
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
 * Set/swap an icon node's glyph: memo cache → an embedded glyph (the element
 * itself, or a sibling) → the fallback now, then the fetched glyph when it
 * arrives (only if the element still shows `name`). All synchronous calls are
 * instant; the only async path is a name the page never embedded.
 */
export function setIcon(svg: Element, name: string): void {
  if (!svg) return;
  current.set(svg, name);
  if (!name) {
    svg.removeAttribute("data-pathland-icon");
    svg.innerHTML = FALLBACK_SVG;
    return;
  }
  // Resolve BEFORE mutating the element: `embeddedGlyph` scans the document
  // (which includes `svg` itself), so rewriting the element's
  // `data-pathland-icon` first would make it match its own OLD inner markup
  // (a hydrated play button would paste its play glyph back on a pause delta —
  // the icon would never change). Resolved against the current DOM, the
  // element still advertises the name it actually shows.
  const memo = cache.get(name);
  if (memo !== undefined) {
    svg.setAttribute("data-pathland-icon", name);
    svg.innerHTML = memo;
    return;
  }
  const embedded = embeddedGlyph(name);
  if (embedded !== undefined) {
    cache.set(name, embedded);
    svg.setAttribute("data-pathland-icon", name);
    svg.innerHTML = embedded;
    return;
  }
  // A miss: memorize every glyph the page has EMBEDDED (the SSR seeds are
  // DOM-only — the moment this element flips away from "play" its play glyph
  // would otherwise evaporate and the return trip would refetch it). One pass,
  // then re-check; a name that is still absent genuinely needs the lazy fetch.
  harvestEmbedded();
  const harvested = cache.get(name);
  if (harvested !== undefined) {
    svg.setAttribute("data-pathland-icon", name);
    svg.innerHTML = harvested;
    return;
  }
  svg.setAttribute("data-pathland-icon", name);
  svg.innerHTML = FALLBACK_SVG;
  void preloadIcon(name).then((glyph) => {
    if (current.get(svg) === name) svg.innerHTML = glyph;
  });
}