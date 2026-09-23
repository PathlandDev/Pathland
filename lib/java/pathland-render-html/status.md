# pathland-render-html (Java) — implementation status

**Last updated:** September 1, 2026

The **Java binding to the Rust HTML renderer** (`com.pathland.render.html`) — a
thin **JNA shim** over `libpathland_render_html`. There is a **single renderer**
(the Rust crate), reused by every language; the former hand-written retained
Java renderer is removed. Protocol contract: `spec/`.

## Implemented

- **Stateless JNA shim**: `HtmlRenderer.instance()` links the Rust cdylib;
  `render(frame, root)` / `renderFragment(frame, root)` /
  `renderFullSnapshot(frame, root)` render a self-contained snapshot frame to
  HTML in one pass (no retained tree, no `applyFrame`); `pathland_html_free`
  releases native strings. Lazy-loaded; skipped (test assumption) when the dylib
  is absent from `java.library.path`.
- **Debug comments**: `renderDebug(frame, root)` / `renderFragmentDebug(...)`
  bind the Rust `pathland_html_render_debug` exports — each rendered node is
  prefixed with an HTML comment naming its component type and the modifiers
  applied (opt-in; the plain `render` output is unchanged).
- **Full Rust renderer surface** (canonical): all spec components render inline
  (structural + decorative CSS in a single `style` attribute), ARIA/event
  attributes, `IS_SECURE` → password, `TOGGLE_STYLE` variants,
  `DATE_PICKER_MODE`, and literal colors inline. The renderer injects its own
  built-in `<style>` block (preflight reset + design tokens + `.pathland-*`
  component defaults) into every document — no external CSS, no `/tailwind.css`.
- **Demos use the shim**: `SessionApp` no longer calls `applyFrame`; SSR renders
  the mount frame through `HtmlRenderer.render(sink.frame(), rootId)`. The
  renderer is compiled once at startup.
- **Text fields share one component class**: `TEXT_FIELD` inputs and `TEXT_EDITOR`
  textareas render with `class="pathland-input"` (Tailwind-style inset-outline
  styling from the Rust renderer's CSS); the copied `@pathland/dom-renderer`
  bundle mirrors the class so delta-created fields hydrate identically.

## Removed (September 2026)

- `compileTailwind(overrideCss)` and the `pathland_tailwind_compile` binding are
  **deleted** — the renderer is inline-only now and needs no compiler. The demo
  `/tailwind.css` endpoints (`TailwindCssController` / `TailwindCssResource`)
  and the `<link rel="stylesheet" href="/tailwind.css">` in both `SessionApp`
  files are removed.

## Not implemented / gaps

- **Inter is loaded from the rsms.me CDN** (Cloudflare) — the Rust renderer emits
  the preconnect + stylesheet links in the document head; the demo pages render
  through it, so no Java-side font serving is needed. The font is **not bundled**
  with the renderer jar; self-hosting a subsetted, OFL-licensed woff2 is a
  planned follow-up for offline and enterprise deployments.

## Verified by

`mvn test -pl pathland-render-html` — JNA shim tests (skip when the Rust dylib
is absent); part of the non-demo reactor run on JDK 17.

