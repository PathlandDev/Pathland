# pathland-render-html (Rust) — implementation status

**Last updated:** October 2, 2026

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
- **Native elements** (the specs carry no renderer mappings — this is the
  concrete HTML map the removed spec hints lived in): `TEXT` → `<span>` (or
  `<p>`/`<h1>`–`<h5>` by `ROLE`/heading `TEXT_STYLE`), `IMAGE` → `<img>`,
  `COLOR` → `<div>`, `SHAPE` → CSS shapes / inline SVG, `DIVIDER` → `<hr>`,
  `SPACER` → inline flex filler, `PROGRESS_VIEW` → `<progress>` /
  `.pathland-spinner`, `GAUGE` → `.pathland-gauge`, stacks/lazy stacks → flex
  `div`, `ZSTACK` → grid overlay `div`, grids → CSS-grid `div`, `SCROLLVIEW` →
  overflow `div`, `BUTTON` → `<button>`, `TEXT_FIELD` → `<input>` (secure →
  `type="password"`), `TEXT_EDITOR` → `<textarea>`, `TOGGLE` → checkbox/switch
  input, `SLIDER` → `<input type="range">`, `STEPPER` → `.pathland-stepper`,
  `DATE_PICKER` → `<input type="date">`/`<input type="time">`, `PICKER` →
  `<select>` + `<option>`, `MENU` → `.pathland-menu` composite, `COLOR_PICKER`
  → `<input type="color">`, `AUDIO`/`VIDEO` → `<audio controls>` /
  `<video controls>` (hidden media element + custom body when app-driven),
  `COMMENT` → none.
- **Media**: `IMAGE` renders `<img src alt>` (the `LABEL` accessibility text is
  the `alt`; empty = decorative) and honors `CONTENT_MODE`/`ASPECT_RATIO`
  inline (`Fit`→`object-fit:contain`, `Fill`→`object-fit:cover`); `VIDEO` →
  `<video src controls>` and `AUDIO` → `<audio src controls>` (playback
  interaction is renderer-native by default). **App-driven media**: a custom
  `AudioStyle`/`VideoStyle` body keeps its own component/layout (e.g. a `VStack`
  flex column) and, when it carries `AUDIO_SOURCE`/`VIDEO_SOURCE` with children,
  the renderer injects a hidden control-less media element as its first child
  and marks the container `data-pathland-media` (the DOM client wires playback
  events, spec/EVENTS.md Media). Asset refs are absolute (`/_pathland/assets/…`);
  bytes never ride the opcode stream.
- **Composite override mode**: `BUTTON`/`TOGGLE`/`SLIDER` with children render
  the custom body wrapped in the native element.
- **Properties**: `ALIGNMENT` (cross-axis **position** — the default is hug
  (`flex-start`), never CSS `align-items:stretch`, per `spec/LAYOUT.md`; only
  `FILL`-sized children stretch via their `100%` size), `CONTENT_MARGINS`,
  `WIDTH`/`HEIGHT` (`FILL` → `width:100%`/`height:100%` expansion, `HUG` →
  intrinsic), `PADDING` + per-edge, `COLOR`,
  `BACKGROUND_COLOR`, `FONT_SIZE`/`WEIGHT`/`FAMILY`, `OPACITY`, `VISIBLE`,
  `Z_INDEX`, `LINE_LIMIT` (positive → `-webkit-box`/`-webkit-line-clamp`, matching
  the DOM client), `TEXT_ALIGNMENT`, `TRUNCATION_MODE`, `BORDER_*`,
  `SELECTED`, `TOGGLE_STYLE`, `ENABLED`, `ROLE`/`STATE` (ARIA), plus
  `TEXT_CASE`, `FONT_STYLE`, `FONT_DESIGN`, `UNDERLINE`/`STRIKETHROUGH`,
  `CLIPS_TO_BOUNDS`, `ALLOWS_HIT_TESTING`, `COLOR_INVERT`. `WIDTH`/`HEIGHT`
  `HUG_CONTENT` (-2) is **omitted** (intrinsic size); `FILL` (-1) → `100%`.
- **`Z_INDEX` (draw-order override)**: a `Z_INDEX != 0` on a `ZSTACK` child or
  `GRID` cell is emitted on its **shell** (the grid item that participates in
  stacking — the child's own `z-index` is inert inside the shell), so a higher
  value draws above lower siblings regardless of child index
  (spec/PRIMITIVES.md §ZStack). The child keeps its own property too (the DOM
  client reads it). Mirrored by the DOM client's `applyLayout`.
- **Layout contract (spec/LAYOUT.md)**: cross-axis default is **hug**
  (`flex-start`), not CSS stretch; `ALIGNMENT` positions only (`Fill`=3 → hug
  default). `DIVIDER` is greedy on the cross axis (`width:100%`); `SCROLLVIEW`
  is greedy on both axes (`flex:1 1 auto;align-self:stretch`); fixed frames emit
  exact px boxes. Conformance cases C1–C6 asserted in `lib.rs` tests.
- **Fill propagation (spec/PRIMITIVES.md §stack layout model)**: implemented —
  `fills_axis` resolves a Hug stack/ZStack containing a `FILL` child / greedy
  primitive on an axis as `FILL`, emitting `width/height:100%` on that axis, and
  cross-axis `FILL` children stretch via `align-self:stretch` (SSR + TS DOM
  client, kept canonical via the layout pass in `applyLayout`).
- **ZStack (spec/PRIMITIVES.md)**: implemented — an overlapping grid
  (`grid-area:1/1` cells); the container hugs to its largest child (`max-content`)
  unless Fixed/FILL (or fill propagation → `100%`); each child keeps its own
  size and is positioned by `ALIGNMENT` on both axes (SSR + TS DOM client).
  **Grid tracks are `minmax(0,1fr)`** (ZStack + count-generated grid tracks,
  `repeat(n,minmax(0,1fr))`, and lazy-hgrid auto columns), so a FILL ZStack/grid
  can shrink below its content min-height on a short viewport — an `end`-aligned
  child (e.g. a player bar) stays pinned instead of being pushed out of view and
  the inner `ScrollView` scrolls. A `GRID_TRACKS` per-track spec is emitted
  verbatim (app-authored). **Child shells carry no explicit
  `width/height`** — `justify-self/align-self:stretch` only applies when a grid
  item's size is `auto`, so pinning `max-content` silently degraded `stretch` to
  `start` and a FILL child's `100%` resolved circularly (content height),
  overflowing the viewport. With `auto` sizes a FILL child genuinely stretches
  to its (shrinkable) track and a Hug child keeps content size + alignment.
- **ARIA ROLE/STATE maps match the DOM client**: the full `ROLE` set (button…
  menu, incl. `text`/`img`/`radio`/`spinbutton`/`tablist`/`list`/`grid`/
  `region`/`menu`) and the `STATE` semantics (one true `aria-*` per state) are
  canonical with `lib/typescript/src/classes.ts`.
- **Semantic HTML elements from `ROLE` + typography** (spec/OPCODE.md §semantic
  properties): a semantic role retags a **generic `div`/`span` shell** to its
  native element — `HEADER`→`<h2>`, `PARAGRAPH`→`<p>`, `LIST`→`<ul>`,
  `LIST_ITEM`→`<li>`, `SUMMARY`→`<section>`, and the landmarks `BANNER`→
  `<header>`, `NAVIGATION`→`<nav>`, `MAIN`→`<main>`, `CONTENT_INFO`→`<footer>`,
  `COMPLEMENTARY`→`<aside>`, `ARTICLE`→`<article>`, `SECTION`→`<section>`,
  `SEARCH`→`<search>`. **Control components keep their native element** and
  emit no ARIA role from `ROLE` (`BUTTON` stays a `<button>`, `MENU` renders
  its intrinsic `role="menu"`). **Headings** come from a heading `TEXT_STYLE`
  typography (LargeTitle…Headline → `<h1>`–`<h5>`, always a heading) or from
  `ROLE_HEADER` (default `<h2>`); non-heading text defaults to `<span>`.
  Interactive/control roles are NOT in the catalog (intrinsic to the
  components); a custom button uses `Button`+`ButtonStyle`. The mapping lives
  in **`src/role_spec.rs`** (canonical with the DOM client —
  `pathland-ts-codegen` emits `generated/role-spec.ts`), with the role codes
  centralized in `pathland_core::constants::role`.
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
- `PARAMETER::SET_DATE` handled (days + millis-of-day → date/time).
- **Design tokens (spec/TOKENS.md)**:
  - The naming/length conventions live in **`src/token_spec.rs`** — the single
    source of truth consumed by the renderer AND by `pathland-ts-codegen`, which
    emits the DOM client's `lib/typescript/src/generated/tokens-core.ts`
    (`tokenToCssVar`, `isDarkToken`, `isLengthToken`, `resolveTokenCssRef`).
  - `PARAMETER::SET_DESIGN_TOKEN` overrides are collected per snapshot batch and
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

- **`TRUNCATION_MODE`** (spec/LAYOUT.md §content fitting): aligned — it **alone**
  has no observable effect (the old `nowrap`+`text-overflow:ellipsis` emission is
  gone, SSR + TS DOM client); under a `LINE_LIMIT` clamp the renderer tail-
  ellipsizes via `line-clamp`. Renderer-owned fidelity: CSS cannot place a
  head/middle ellipsis, so non-Tail modes fall back to the tail ellipsis.
- **`DESIGN_TOKEN` property references** cover the directly-mappable subset
  (colors, font size/weight, spacing/padding, corner radius, opacity,
  width/height, border width/color, shadow color). Compound accumulators that
  need concrete numbers (shadow radius/x/y) remain literal-only.
- **Grid/ScrollView/Lazy layout (spec/PRIMITIVES.md §Grid / §ScrollView)**:
  grids emit `gap` from `SPACING`, equal-`1fr` track templates from the
  `GRID_COLUMNS`/`GRID_ROWS` constructor properties (columns for GRID/
  LAZY_VGRID, rows for LAZY_HGRID + `grid-auto-flow:column`), **`GRID_TRACKS`**
  per-track specs (`flex`/`fixed:<pts>`/`adaptive:<pts>` → `1fr`/`<pts>px`/
  `minmax(<pts>px,1fr)`, taking precedence over the count), and **per-cell
  alignment** — each cell is wrapped in an auto-placed shell whose
  `justify-self`/`align-self` is `stretch` for a `FILL`/greedy cell, else the
  grid `ALIGNMENT` position (default start) — with `justify-items`/
  `align-items` on the container. **`GRID_ROW`** (0x1D) rows are flattened with
  explicit `grid-row`/`grid-column` placement; each row renders as a transparent
  `<div style="display:contents">` (its id hydrates the DOM client), and a
  short row leaves trailing columns empty. `WIDTH`/`HEIGHT` on a grid are the
  universal pixel box, never a count. `SCROLLVIEW` is greedy
  (`flex:1 1 auto;align-self:stretch;overflow:auto`) and renders **only its
  first child** (the spec-pinned single content). Conformance cases C11–C14
  covered by the SSR tests + golden fixtures (incl. the `gridrow` scenario).
- `SHAPE` `Path` renders as an SVG placeholder (no path data wire property).
- GAUGE/SHAPE visuals are CSS approximations, not pixel-exact.
- **Typography uses the platform system font stack** (`system-ui,
  -apple-system, 'Segoe UI', Roboto, …`) — no external/webfont loading, so the
  rendered document makes no network requests for fonts and works offline.
  An app can still pick its own typeface via a `font.body.family`
  `SET_DESIGN_TOKEN` override (any STRING value, e.g. `"Inter"` if the app
  hosts it).

## Verified by

`cargo test -p pathland-render-html` — 43 headless render tests (components,
properties, composite, event attrs, navigation slot route/transition attrs,
`days_to_date`, network-decoded frames, inline-all styling, built-in CSS block
contents, Inter CDN head links, design tokens: base/dark override CSS, `px`
lengths, `DESIGN_TOKEN` refs, generative `space.N`). `cargo test -p
pathland-html-golden` — the SSR golden-fixture guard.
