# pathland-render-html (Rust) — implementation status

**Last updated:** September 25, 2026

The **server-side / remote-projection HTML renderer**: a **stateless, streaming**
pure function of the opcode stream producing declarative HTML. Each render call
decodes a self-contained snapshot batch into a **transient** map, walks it once,
and emits HTML text out — no retained tree, zero cross-call state (Renderer
Statelessness). Protocol contract: `spec/`.

## Implemented

- **Stateless streaming API**: `HtmlRenderer::render_document(opcodes, strings, root)`
  / `render_fragment(...)` build a transient decode map per call and stream HTML;
  the retained `apply`/`apply_frame`/`render` model is removed.
- **All spec components render**: `TEXT`, `IMAGE`, `COLOR` (layout-greedy:
  `flex:1;align-self:stretch`), `SHAPE` (CSS/SVG by `SHAPE_KIND`), `DIVIDER`, `SPACER` (inline
  `flex:1`), `PROGRESS_VIEW`/`GAUGE` (`data-min`/`data-max` ride on the gauge so the DOM
  client can recompute its percentage), `VSTACK`/
  `HSTACK`/`LAZY_VSTACK`/`LAZY_HSTACK` (flex), `ZSTACK` (overlay), `GRID`/
  `LAZY_VGRID`/`LAZY_HGRID` (CSS grid), `SCROLLVIEW`, `BUTTON`, `TEXT_FIELD`
  (incl. `IS_SECURE` → `type="password"`), `TEXT_EDITOR`, `TOGGLE`
  (`TOGGLE_STYLE`), `SLIDER`, `STEPPER` (the `.pathland-stepper` composite with
  `data-min`/`data-max`/`data-step` on its hidden range span), `DATE_PICKER` (`SET_DATE` →
  `days_to_date`), `PICKER` (option children rendered as `<option data-pathland-id="{child}"
  value="{index}">` + `SELECTION`), `MENU` (the `.pathland-menu` composite:
  `.pathland-menu-trigger` label + `.pathland-menu-items` children),
  `COLOR_PICKER`, `COMMENT`.
- **Composite override mode**: `BUTTON`/`TOGGLE`/`SLIDER` with children render
  the custom body wrapped in the native element.
- **Properties**: `ALIGNMENT` (cross-axis flex), `CONTENT_MARGINS`,
  `WIDTH`/`HEIGHT` (`FILL` → `width:100%`/`height:100%` expansion, `HUG` →
  intrinsic), `PADDING` + per-edge, `COLOR`,
  `BACKGROUND_COLOR`, `FONT_SIZE`/`WEIGHT`/`FAMILY`, `OPACITY`, `VISIBLE`,
  `Z_INDEX`, `LINE_LIMIT`, `TEXT_ALIGNMENT`, `TRUNCATION_MODE`, `BORDER_*`,
  `SELECTED`, `TOGGLE_STYLE`, `ENABLED`, `ROLE`/`STATE` (ARIA), plus
  `TEXT_CASE`, `FONT_STYLE`, `FONT_DESIGN`, `UNDERLINE`/`STRIKETHROUGH`,
  `CLIPS_TO_BOUNDS`, `ALLOWS_HIT_TESTING`, `COLOR_INVERT`. `WIDTH`/`HEIGHT`
  `HUG_CONTENT` (-2) is **omitted** (intrinsic size); `FILL` (-1) → `100%`.
- **ARIA ROLE/STATE maps match the DOM client**: the full `ROLE` set (button…
  menu, incl. `text`/`img`/`radio`/`spinbutton`/`tablist`/`list`/`grid`/
  `region`/`menu`) and the `STATE` semantics (one true `aria-*` per state) are
  canonical with `lib/typescript/src/classes.ts`.
- **Semantic HTML elements from `ROLE`** (spec/OPCODE.md §semantic properties):
  a semantic role retags a **generic `div`/`span` shell** to its native element
  — the landmark/structural roles (`BANNER`→`<header>`, `NAVIGATION`→`<nav>`,
  `MAIN`→`<main>`, `CONTENT_INFO`→`<footer>`, `COMPLEMENTARY`→`<aside>`,
  `ARTICLE`→`<article>`, `SECTION`→`<section>`, `SEARCH`→`<search>`,
  `LIST`→`<ul>`, `LIST_ITEM`→`<li>`, `PARAGRAPH`→`<p>`), plus `HEADER`→`<h2>`
  (heading level is design-system-driven, interim default) and `SUMMARY`→
  `<section>`. **Control components keep their native element** (`<button>`,
  `<input>`, …) and emit no redundant ARIA role; roles with no element
  (`LINK`, `CHECKBOX`, `SLIDER`, `MENU`, …) fall back to an ARIA `role`
  attribute. The mapping lives in **`src/role_spec.rs`** (canonical with the DOM
  client — `pathland-ts-codegen` emits `generated/role-spec.ts`), with the role
  codes centralized in `pathland_core::constants::role`.
- **`TREE::INSERT_CHILD` honors its `C` index** (`u32::MAX` = append), matching
  the protocol and the DOM client's `insertAt`.
- **Event surfacing**: `data-event-listeners` / `data-action-id` /
  `data-binding-id` attributes.
- **Navigation slot attrs** (spec DSL.md §4.5 / MODIFIERS.md): a slot node's
  `ROUTE` (STRING) renders as `data-pathland-route="<path>"`, its
  `TRANSITION` hint renders as `data-pathland-transition="<platform|fade|slide|scale>"`,
  its `NAV_CHROME` (F32 enum) renders `data-pathland-nav-chrome="custom"`
  when `Custom` (the renderer adds no default chrome), and `NAV_DEPTH` (U32)
  renders `data-pathland-depth="N"` when deeper than its root so the DOM client
  can hydrate its default back button from the SSR HTML — the DOM renderer
  mirrors the route into the URL, may animate a swap, and draws the renderer's
  own back button (the web has no native navigation container).
- `STYLE::SET_DATE` handled (days + millis-of-day → date/time).
- **Design tokens (spec/TOKENS.md)**:
  - The naming/length conventions live in **`src/token_spec.rs`** — the single
    source of truth consumed by the renderer AND by `pathland-ts-codegen`, which
    emits the DOM client's `lib/typescript/src/generated/tokens-core.ts`
    (`tokenToCssVar`, `isDarkToken`, `isLengthToken`, `resolveTokenCssRef`).
  - `STYLE::SET_DESIGN_TOKEN` overrides are collected per snapshot batch and
    emitted into the document head as CSS: base (light) tokens as `:root`
    rules, `dark.*` overrides inside `@media (prefers-color-scheme: dark)` —
    the browser resolves the scheme natively, so SSR needs no client scheme.
  - `DESIGN_TOKEN`-typed `SET_PROPERTY` values resolve at render time to
    `var(--pl-…)`, with the generative `space.<N>` family resolving to
    `calc(var(--pl-space-base) * N)` (`Node::token_ref`).
  - **`STRING`-valued token overrides** (e.g. `font.body.family`): a
    `SET_DESIGN_TOKEN` with `valueType = STRING` resolves the value string from
    the batch's string section and emits it single-quoted (escaped) as a
    `:root` rule (or inside the dark media query).
  - The override rules render inside their own **`<style data-pathland-tokens>`**
    element in the document head, **after** the built-in block (so their
    `:root` variables win the cascade and the browser applies them) — matching
    the JS DOM client's `style[data-pathland-tokens]` element. No overrides →
    no extra element.
  - The built-in `:root` tokens use the **canonical spec paths** (`--pl-color-primary`,
    `--pl-color-background`, `--pl-color-text-primary`, `--pl-color-text-secondary`,
    `--pl-color-surface`, `--pl-color-border`) so app overrides retheme the
    renderer.
  - **Full Tier-1 default coverage**: `--pl-space-base` (light + dark, so the
    generative `space.<N>` family always resolves), canonical typography
    (`--pl-font-body-size/weight/family`), the radius scale + `--pl-border-width-thin`,
    and the **shared control core mapped onto the canonical catalog** —
    `--pl-control-*`, `--pl-button-*`, `--pl-input-*` (light + dark), with the
    focus ring mapped to `control.accent` and shadows mapped onto the composite
    `elevation.low.*` / `elevation.high.*` tokens. `.pathland-*` rules reference
    the canonical variables, so `SET_DESIGN_TOKEN` overrides retheme components.

## Design decision (decision A, September 2026): inline-all + built-in design system

Tailwind is **removed**. The renderer emits **everything inline** as CSS in a
single `style` attribute — no external compiler, no class system, no safelist:

- **All opcode properties render inline** (`style_css()` + `border_style()`),
  including the previously class-based enum surface (alignment, text alignment,
  text case, visible, font style/design, underline/strikethrough, clips-to-bounds,
  hit-testing, invert, truncation). **1 dp = 1 CSS px**.
- **`css::STYLE`** is a built-in `<style>` block injected by `render_document`,
  containing:
  - a **preflight reset** (vendored from Tailwind CSS, MIT — see
    `THIRD_PARTY_NOTICES`),
  - `:root` **design tokens** as CSS custom properties (`--pl-color-primary`,
    `--pl-color-background` (document background), `--pl-color-surface`,
    `--pl-color-text-primary`/`--pl-color-text-secondary`, `--pl-color-border`,
    `--pl-radius-*`, `--pl-font-sans`, `--pl-input-*` (field bg/text/outline/
    placeholder/focus), button tokens), named to match the canonical protocol
    design-token paths so `SET_DESIGN_TOKEN` can retheme the renderer,
  - `.pathland-button` (the prettified-button POC, with `prefers-color-scheme`
    dark mode) and `.pathland-*` component defaults (toggle switch, slider, text
    field/editor, stepper, spinner, gauge, menu) — the interactive/hover/focus/
    disabled states that can't be expressed inline. Text fields use a
    Tailwind-style **inset outline** (`outline: 1px solid`, `outline-offset: -1px`,
    focus → `2px`/`-2px` + indigo) instead of a border, with dark-mode tokens;
    `html` sets `color-scheme: light dark` + the `--pl-color-bg` background so
    native form controls and the page follow the theme.
- **`pathland_html_*` cdylib** (`pathland_html_render` / `_render_fragment` /
  `_free`) for cross-language hosts (Java JNA shim). The `_tailwind_compile`
  entry point, `tw.rs`, `tailwind.rs`, `build.rs`, `scripts/fetch-tailwind.mjs`,
  `vendor/`, and the `tailwind-embed` feature are **deleted**.
- **Debug comments** (`HtmlRenderer::with_debug_comments(true)`; C ABI
  `pathland_html_render_debug` / `_fragment_debug`): every rendered node is
  prefixed with an HTML comment naming its component type and the modifiers
  applied (`<!-- #1 VStack: spacing=4, alignment=Fill, width=FILL -->`) — values
  decoded by protocol type (FILL/HUG sentinels, well-known enums, colors,
  strings, bitmasks); opt-in so default output is unchanged, and comments stay
  valid HTML (content scrubbed of `--`).

## Cross-renderer conformance (no drift)

The **`pathland-html-golden`** crate renders a battery of scenarios to committed
fixtures under `lib/typescript/test/fixtures/ssr/` (`{name}.plpl` batch bytes +
`{name}.html` canonical `render_fragment`). Its `tests/guard.rs` fails if the
SSR output changes without the fixtures being regenerated
(`cargo run -p pathland-html-golden -- --emit`), and the TypeScript
`test/ssr-conformance.test.ts` asserts the DOM client reproduces the same DOM
from the same batches (fresh-DOM + hydrate-then-delta). The SSR output is the
contract both renderers must satisfy.

## Not implemented / gaps

- **`DESIGN_TOKEN` property references** cover the directly-mappable subset
  (colors, font size/weight, spacing/padding, corner radius, opacity,
  width/height, border width/color, shadow color). Compound accumulators that
  need concrete numbers (shadow radius/x/y) remain literal-only.
- `SHAPE` `Path` renders as an SVG placeholder (no path data wire property).
- GAUGE/SHAPE visuals are CSS approximations, not pixel-exact.
- **Inter is loaded from the rsms.me CDN** (Cloudflare, `font-display: swap`,
  with the InterVariable progressive enhancement for variable-font browsers) —
  the renderer emits the preconnect + stylesheet links in the document head. It
  is **not bundled**: self-hosting a subsetted, OFL-licensed woff2 (embedded in
  the renderer / jar) is a planned follow-up for offline and enterprise
  deployments (see `THIRD_PARTY_NOTICES`).

## Verified by

`cargo test -p pathland-render-html` — 43 headless render tests (components,
properties, composite, event attrs, navigation slot route/transition attrs,
`days_to_date`, network-decoded frames, inline-all styling, built-in CSS block
contents, Inter CDN head links, design tokens: base/dark override CSS, `px`
lengths, `DESIGN_TOKEN` refs, generative `space.N`). `cargo test -p
pathland-html-golden` — the SSR golden-fixture guard.
