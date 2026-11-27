# pathland-render-html (Java) — implementation status

**Last updated:** November 26, 2026

The **pure-Java HTML renderer** (`dev.pathland.render.html`) — a behavioural
port of the Rust `pathland-render-html` crate. Stateless: each call decodes a
self-contained snapshot frame into a transient tree and renders it to HTML in
one pass. No JNA and no cdylib, so a Maven dependency runs on every platform and
is GraalVM native-image compatible (no reachability/resource config for a native
library). Protocol contract: `spec/`.

## Implemented

- **Keyboard focus attributes**: a node whose `EVENT_LISTENERS` carries the
  `FOCUS` bit renders `tabindex="0" data-pathland-focusable=""`; scroll
  containers render `tabindex="-1"` unless they themselves declare `FOCUS`.
  Native control shells (button, text field/editor, slider, toggle, stepper
  buttons, picker, date/color picker) render an explicit `tabindex="0"` so
  Safari's default Tab policy still reaches them. `SLIDER` honors
  `STEP_VALUE` (absent = continuous `step="any"`). Scroll containers emit
  `overflow-anchor:none` so a lazy window's virtual padding updates stay
  scroll-stable.

- **Pure-Java renderer**: `HtmlRenderer.instance()` / `tryInstance()` /
  `isAvailable()` (always true); `render(frame, root)` /
  `renderFragment(frame, root)` / `renderFullSnapshot(frame, root)` render a
  self-contained snapshot in one pass. The public API is unchanged from the
  former JNA shim, so `PathlandSession`, Spring, and Quarkus need no changes.
- **Layout parity with the Rust renderer**: `SCROLL_AXIS`-aware ScrollView
  greed, finite `MAX_*` cap/bounded-fill semantics (no unbounded propagation or
  cross-axis stretch), parent `ALIGNMENT` positioning, and the DOM hydration
  `data-pathland-scroll-axis` attribute. The retired `IDEAL_*` properties are
  ignored.
- **Byte-identical Rust output**: `GoldenConformanceTest` renders every
  committed `lib/typescript/test/fixtures/ssr/*.plpl` fixture and compares the
  fragment to its `.html` fixture byte-for-byte (the same contract the TS DOM
  client asserts), including the delta base/change/result scenario.
- **SEO document metadata (issue #45)**: `DocumentMeta`/`render(frame, root,
  meta)` / `renderLinked(..., meta)` add canonical URL, Open Graph/Twitter
  cards, and component-derived JSON-LD (WebPage + Article for `ROLE_ARTICLE`).
  Window `TITLE`/`DESCRIPTION` win; the metadata values are fallbacks. The
  document output is pinned byte-for-byte against the Rust renderer's
  `document_meta.document.html` fixture.
- **Full renderer surface** (canonical): all spec components render inline
  (structural + decorative CSS in a single `style` attribute), ARIA/event
  attributes, `IS_SECURE` → password, `TOGGLE_STYLE` variants,
  `DATE_PICKER_MODE`, literal colors inline, attributed text, media
  surfaces/controllers, proxy views, grid rows/tracks, fit bands, and
  view-that-fits snapshots.
- **Eager list spacing**: the HTML round-trip test verifies rows expanded by an
  eager `ForEach` are direct children of their parent stack and share its CSS gap.
- **List composite + dark mode**: the DSL `List`/`ListStyle` emits ordinary
  primitives plus `ROLE=LIST`/`LIST_ITEM`, so the renderer produces semantic
  `<ul>`/`<li>` with no List-specific code. Built-in styles reference the
  `list.*` tokens; `HtmlRendererTest.rendersListStylesWithTokenizedDarkModeChrome`
  checks the token refs and the light/forced-dark `--pl-list-*` CSS defaults.
- **Built-in base design**: the generated `design/build/base.css` (synced into
  the Rust assets) is copied into the jar at build time and inlined by the
  default `render(...)`. `renderLinked(frame, root, cssBase)` /
  `renderLinkedDebug(...)` emit a content-hashed
  `<link rel="stylesheet" href="{cssBase}/{baseCssName()}">`; `baseCss()` /
  `baseCssName()` expose the renderer-owned bytes for immutable serving.
- **Debug comments**: `renderDebug` / `renderFragmentDebug` prefix every node
  with an HTML comment naming its component and decoded modifiers.
- **Icon glyphs**: `iconSvg(name)` returns the full inline `<svg>` for a
  canonical `ICON_NAME` (unknown names → the fallback glyph; `null` → `null`),
  so hosts serve `/_pathland/icons/<name>.svg` from the same table SSR inlines.
- **Demos use the renderer**: the Spring Boot and Quarkus starters SSR through
  `HtmlRenderer`, and the DOM client hydrates in place.

## Not implemented / gaps

- **Typography uses the platform system font stack**; the document head makes no
  external/webfont requests.
- The renderer is a port of the Rust implementation, not a second source of
  truth: when the HTML output changes, update the Rust crate + golden fixtures
  and this port together (`GoldenConformanceTest` fails on drift).
- **Virtualized lazy stacks (ADR-0014)**: the Java port renders the virtual
  extent and window placement and emits the `data-pathland-lazy*` attributes,
  matching the Rust renderer. App `PADDING` is added as an inset around the
  virtual extent. Lazy stacks support variable item extents; lazy grids use the
  same row/column window with a uniform extent.

## Verified by

`mvn test -pl pathland-render-html` — the golden conformance suite plus the
DSL → emitter → frame → renderer round-trip tests. Part of the non-demo reactor
run on JDK 17/21/25 and under GraalVM native-image.
