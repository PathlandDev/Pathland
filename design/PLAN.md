# HTML Base Design — Style Dictionary Plan

**Status:** Complete
**Branch:** `feature/design-system-style-dictionary`
**Last updated:** October 28, 2026

---

## Goal

Make the HTML renderer's base design a **CSS-first, Style Dictionary pipeline**:

- Design tokens are authored in three layers (base → semantic → component).
- Style Dictionary generates the token CSS.
- The hand-authored reset and `.pathland-*` component CSS live in `design/html/` and consume
  the generated `var(--pl-*)` tokens.
- The renderer embeds the generated bundle in Rust.
- The design loop (edit tokens/CSS → rebuild CSS → preview) does not require changing Rust
  source files.

## Non-goals

- **Multiple brands/themes.** Removed from scope.
- **App-selected base designs, custom app base CSS, `BaseDesign`/`BaseDesignSource` APIs, and
  per-app `pathland.base-design.*` configuration.** Removed from scope. The HTML renderer has
  one built-in base design.
- GTK/Qt renderer changes.
- Generated Java/TypeScript token constants and documentation (possible follow-up).
- A production hashed linked stylesheet (deferred; the renderer embeds the base CSS).
- Any protocol change. `spec/TOKENS.md` remains the catalog contract.

---

## Architecture

### Three token layers

| Layer | Contents | Emitted to CSS? | References |
|---|---|---|---|
| **base** | Palette (`base.color.blue.600`), spacing/type scales, radii, border widths, elevation primitives | No — resolved into literals | None |
| **semantic** | `color.*`, `space.base`, `font.*`, `radius.*`, `border.width.*`, `elevation.*`, `control.*`, `size.control.*`, `opacity.disabled` | Yes | base |
| **component** | `button.*`, `input.*`, `toggle.*`, `slider.*`, `progress.*`, `gauge.*`, `stepper.*`, `picker.*`, `menu.*`, `label.*`, `icon.*`, `spinner.*` | Yes | semantic (base only when no alias exists) |

- Dark values use `$extensions.pathland.dark` on each token.
- Base palette entries are distinct (`blue.600`, `blue.400`); semantic tokens explicitly
  reference the dark entry.
- `space.<N>` remains generative: only `space.base` is emitted. Component dimensions reference
  the base scale (e.g. `base.space.3`), not the generative family.
- `button.padding` becomes `button.padding.horizontal` / `.vertical`.
- Internal component tokens (durations, z-index, `em` icon sizes) are emitted but are
  renderer-internal, not part of `spec/TOKENS.md`.

### Source and generated files

```
design/
  package.json                  # style-dictionary v5, chokidar; scripts: build/dev/check
  package-lock.json
  style-dictionary.config.mjs
  tokens/
    base/*.tokens.json          # internal palette + scales
    semantic/*.tokens.json      # catalog semantic + shared control.*
    component/*.tokens.json     # full per-component token surface
  html/
    reset.css                   # preflight + html/body
    components.css              # .pathland-* rules, consuming var(--pl-*)
  scripts/
    build.mjs                   # SD build + concat reset+tokens+components
    dev.mjs                     # build + watch tokens + html/*.css
    check.mjs                   # layer/catalog/type/reference validation
  build/                        # generated, gitignored
    tokens.css
    base.css
    manifest.json
  README.md
lib/rust/crates/pathland-render-html/assets/base.css   # synced generated bundle (committed)
scripts/sync-design-css.sh
```

### Renderer embedding

```rust
// lib/rust/crates/pathland-render-html/src/css.rs
const CSS: &str = include_str!("../assets/base.css");
pub fn builtin_css() -> &'static str { CSS }
pub const STYLE: &str = concat!("<style>", include_str!("../assets/base.css"), "</style>");
```

- `render_document` keeps using `css::STYLE`.
- The synced crate copy is generated from `design/build/base.css` and committed for packaging.
- `scripts/sync-design-css.sh --check` fails on drift.
- The renderer remains stateless and a pure function of the opcode stream; the base design is
  compiled in.

### Style Dictionary configuration

- Style Dictionary v5, Node ≥22, DTCG (`$value`, `$type`, `$description`, `$extensions`).
- Custom formats:
  - `pathland/css-tokens` emits:
    - `:root` semantic + component variables;
    - `@media (prefers-color-scheme: dark)` dark layer;
    - `:root[data-pathland-scheme='dark']` and `:root[data-pathland-scheme='light']`;
    - `:root` forced-dark `--pl-dark-*` variables.
  - `pathland/manifest` emits `{ path, layer, type, cssVar, darkCssVar, internal }` for
    validation and future codegen.
- Reference rules:
  - semantic/component → emitted token: `var(--pl-<target>)` (light/dark) and
    `var(--pl-dark-<target>)` (forced-dark);
  - → base token: resolved literal;
  - no explicit dark value: reference the emitted target so its dark layer wins;
  - explicit dark extension: use it for the dark and forced-dark layers.
- Base tokens are filtered from the CSS output.
- Dimensions preserve the current `px`/`rem` values.

> **Implementation note (landed):** token files use **flat DTCG keys** carrying
> their dotted path rather than nested groups. The Pathland catalog contains
> paths that are both a leaf and a group (`control.border` is a color while
> `control.border.width` is a dimension), which nested DTCG trees cannot
> represent; Style Dictionary's custom formats read and resolve the flat maps
> directly (see `design/scripts/token-model.mjs`).

---

## Phases

### Phase 1 — Extract the hand-authored CSS

**Deliverables**

- `design/html/reset.css` — preflight + `html`/`body` from `css.rs`, preserving the Tailwind
  MIT attribution.
- `design/html/components.css` — every `.pathland-*` rule moved verbatim.
- `design/html/tokens.css` — temporary hand-authored copy of the current token block
  (`:root`, dark media, forced-dark).
- `scripts/sync-design-css.sh` — copy the three files to
  `lib/rust/crates/pathland-render-html/assets/`; `--check` mode.
- `css.rs` assembles the synced files with `include_str!`/`concat!`; output unchanged.
- Wire the sync into `scripts/lib.sh` (`build_rust_html`), the `refresh-ide` Maven profile,
  and CI.

**Verification**

- `cargo test -p pathland-render-html`.
- `cargo test -p pathland-html-golden`.
- `scripts/sync-design-css.sh --check`.
- Rendered output is textually unchanged.

### Phase 2 — Style Dictionary pipeline for the existing token surface

**Deliverables**

- `design/package.json`, `package-lock.json`, `style-dictionary.config.mjs`,
  `design/scripts/{build,dev,check}.mjs`.
- Base/semantic/component token files covering every variable currently in
  `design/html/tokens.css`.
- Generated `design/build/tokens.css`, `design/build/base.css`,
  `design/build/manifest.json`.
- Replace the temporary `design/html/tokens.css` with the generated output.
- Sync `design/build/base.css` to the crate asset.
- `design/scripts/check.mjs` validates:
  - every Tier-1 catalog token is present;
  - semantic → base, component → semantic (base only when justified);
  - every emitted token has dark + forced-dark values;
  - no unresolved references;
  - internal tokens are marked.

**Verification**

- `npm --prefix design run build && npm --prefix design run check`.
- `scripts/sync-design-css.sh --check`.
- `cargo test -p pathland-render-html`.
- The generated CSS contains the four token layers and all Tier-1 variables.

### Phase 3 — Full component token layer

**Deliverables**

- Add component tokens for design-relevant literal values in `components.css` and update the
  rules to consume them. Structural values (`display`, `flex`, `100%`, positioning) stay in
  CSS.

| Component | Tokens |
|---|---|
| Button | `button.padding.{vertical,horizontal}`, `button.line-height`, `button.border.width`, `button.focus.ring.{width,offset,color}`, `button.transition.duration`, `button.background-hover` |
| Icon | `icon.size`, `icon.offset` |
| Toggle | `toggle.gap`, `toggle.checkbox.size`, `toggle.switch.{width,height,radius}`, `toggle.thumb.{size,offset,travel}`, `toggle.transition.duration`, `toggle.focus.ring.{width,offset}` |
| Slider | `slider.gap`, `slider.height`, `slider.track.{height,radius}`, `slider.thumb.{size,offset,border.width}`, `slider.focus.ring.{width,offset}` |
| Control / Input | `control.line-height`, `control.border.width`, `control.transition.duration`, `input.padding.{vertical,horizontal}`, `input.focus.ring.{width,offset}`, `input.swatch.inset` |
| Progress | `progress.height`, `progress.radius` |
| Stepper | `stepper.gap`, `stepper.button.size`, `stepper.border.width` |
| Spinner | `spinner.size`, `spinner.border.width`, `spinner.duration` |
| Gauge | `gauge.height`, `gauge.radius`, `gauge.transition.duration` |
| Menu | `menu.min-width`, `menu.border.width`, `menu.z-index` |
| Color picker | `color.swatch.inset` |

- Extend `token_spec.rs` length rules so every `$type: dimension` token is recognized by
  `isLengthToken`.
- Add a validation that the SD manifest's dimension tokens all satisfy `isLengthToken`.
- Run `npm run regen` for the TypeScript token conventions if the lists change.

**Verification**

- `npm --prefix design run build && npm --prefix design run check`.
- `cargo test -p pathland-render-html` and `cargo test -p pathland-html-golden`.
- No design-relevant literal remains in `components.css`.

### Phase 4 — Build, CI, docs

**Deliverables**

- `scripts/lib.sh` runs the design build + sync before `build_rust_html`.
- `lib/java/pom.xml` `refresh-ide` runs the design build + sync before cargo.
- CI: design install/build/check, sync check, cargo test, Maven tests, TS tests.
- `design/README.md`: three-layer model, dark extension, references, internal tokens,
  build/dev/check, sync, "generated files — do not edit".
- Update `lib/rust/crates/pathland-render-html/status.md` and `AGENTS.md`.

**Verification**

- Full build/test pipeline passes from a clean checkout.

### Phase 5 — Design preview (development tool)

**Deliverables**

- A static preview that loads `design/build/base.css` and renders representative component
  markup (button, input, toggle, slider, progress, stepper, menu, media, etc.), so token/CSS
  changes can be reviewed with a browser refresh.
- Optional: a dev-only watcher that rebuilds `design/build/base.css` on token/CSS changes.

This is a development tool, **not** an app-selectable base design.

**Verification**

- `npm --prefix design run dev` rebuilds `design/build/base.css`.
- The preview reflects token and component CSS changes on refresh.

---

## Acceptance criteria

- The base design is generated from the three-layer Style Dictionary source plus hand-authored
  reset/component CSS.
- The renderer embeds the synced generated bundle; no app-facing base-design selection exists.
- The design token layers are validated against the protocol catalog, dark/forced-dark rules,
  and dimension/length conventions.
- `cargo test`, the Java shim tests, the TypeScript tests, and the sync check pass.
- No multiple-brand or app-selected base-design machinery is introduced.

## Deferred

- Production hashed linked stylesheet for the single built-in design.
- SD-generated TypeScript/Java token constants and documentation.
- Additional preview/design-tool ergonomics beyond Phase 5.

## Removed from scope

- Multiple brands/themes.
- App-selected base designs, custom app base CSS, `BaseDesign`/`BaseDesignSource`, and
  `pathland.base-design.*` configuration.
