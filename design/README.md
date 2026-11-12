# Pathland base design

The built-in look of the HTML renderer, generated from a **three-layer Style
Dictionary pipeline** plus hand-authored reset/component CSS. The canonical
design and token source is **`design_system.fig`**, authored and maintained in
OpenPencil; `tokens/` is the current downstream Style Dictionary mirror.

```
design_system.fig                canonical design + token source    (OpenPencil)
tokens/base/*.tokens.json        internal palette + scales          (not emitted)
tokens/semantic/*.tokens.json    protocol catalog semantics        (emitted)
tokens/component/*.tokens.json   per-component surface             (emitted)
html/reset.css                   Tailwind-derived preflight        (hand-authored)
html/components.css              .pathland-* rules                 (hand-authored)
```

The generated bundle is `build/base.css` (reset + `tokens.css` + components);
`build/base.css.name` is its content-hashed asset name (e.g.
`base-5f86423db422.css`). Both are synced into
`lib/rust/crates/pathland-render-html/assets/` and committed: the renderer embeds
the bytes with `include_str!` and exposes the name for cache-busting links. There
is one built-in base design and no app-facing design selection.

## Canonical source: OpenPencil

`design_system.fig` is the source of truth for the design system and its
tokens. It contains three variable collections:

| Collection | Modes | Purpose |
|---|---|---|
| `Base` | `Value` | internal palette, type/space/radius scales |
| `Semantic` | `Light` / `Dark` | protocol catalog semantics |
| `Component` | `Light` / `Dark` | per-component token surface |

It currently holds **277 variables**: the 241 variables that mirror
`design/tokens` plus 36 extended tokens needed for full typography and
component coverage (caption/meta/heading sizes, weights, line-heights,
tracking, an Inter family token, extra radius steps, and
`toggle.checkbox.radius` / `color.swatch.radius`). Those 36 are not yet in
`design/tokens` — the next export/transform brings them across.

The three pages are **Foundations** (color/type/spacing/radius specimens),
**Components** (Light/Dark variant sets), and **Showcase** (the preview
reproduced in both themes).

Binding coverage and known limits:

- All 254 text nodes bind `fontSize`, `lineHeight`, `letterSpacing`, and
  `fontFamily`; spacing, radii, border widths, control/component sizes, and
  disabled opacity are bound too.
- `fontWeight` cannot be bound: OpenPencil's `bind_variable` API rejects
  `fontWeight`. The weight variables exist and export.
- Dark components/panels/boards use the **Dark variable mode**. Translucent
  dark surface/border tokens keep explicit opaque composites where the
  renderer would otherwise composite alpha over white instead of the parent
  surface; the Dark variable values remain the true alpha tokens for export.
- Swatch hairline strokes and the showcase page backdrop are intentionally
  unbound document decoration, not design tokens.

### Exporting to Style Dictionary

```sh
openpencil tokens design/design_system.fig --format dtcg --out <dir>
```

This writes a DTCG bundle:

```
Base/Value.tokens.json
Semantic/{Light,Dark}.tokens.json
Component/{Light,Dark}.tokens.json
tokens.resolver.json
```

- The original dotted token path is preserved on every token under
  `$extensions.dev.openpencil.name`.
- Light/Dark values are separate mode files. A transform is still required to
  reproduce this repository's flat `tokens/` format: restore the dotted key from
  `$extensions.dev.openpencil.name` and emit it flat, merge the Dark file into
  `$extensions.pathland.dark`, and map FLOAT types using the
  `dtcg type number|duration|fontFamily` hints in the descriptions.
- References are emitted as literal values (OpenPencil does not resolve variable
  aliases for paints). Each description records the source `value ref` /
  `dark ref`, which the transform uses to restore SD references.

The transform is not wired into CI yet; until it lands, edit the `.fig` in
OpenPencil and update `design/tokens` from the export manually.

## Commands

```sh
# canonical design source
openpencil tokens design/design_system.fig --format dtcg --out <dir>

# downstream Style Dictionary / HTML base design
npm --prefix design ci          # install (CI / clean checkout)
npm --prefix design run build   # Style Dictionary + build/base.css
npm --prefix design run check   # catalog/layer/dark/dimension validation
npm --prefix design run dev     # rebuild on token/CSS changes
npm --prefix design run dev -- --serve   # also serve preview/ on :4173
scripts/sync-design-css.sh      # copy base.css + base.css.name into the Rust crate
scripts/sync-design-css.sh --check
```

After exporting tokens or changing CSS: `npm --prefix design run build`, then
`scripts/sync-design-css.sh`, then run `cargo test -p pathland-render-html`.

`build/` and `node_modules/` are gitignored. `package-lock.json` is committed.

## Tokens

`tokens/` is the downstream Style Dictionary input. Going forward, edit the
corresponding variable in `design_system.fig` and export rather than
hand-editing `tokens/**` (until the export transform is automated).

Each token is a flat DTCG entry: `$value`, `$type`, optional `$description` and
`$extensions.pathland`. Keys carry their full dotted path. The representation
choice and shared-asset pipeline are explained in
[ADR-0012](../docs/decisions/0012-generated-html-base-design.md).

- **base** tokens are literals. They are internal: the generator resolves them
  to literals and **never emits them** as CSS variables. Override the semantic
  or component token instead.
- **semantic** tokens reference base tokens. They are the protocol catalog
  surface (`spec/TOKENS.md`).
- **component** tokens reference semantic tokens, or a base scale token
  (`base.space.*`, `base.radius.*`, `base.border.width.*`, `base.size.*`,
  `base.font.*`) when no semantic alias exists. A component token that must
  reference a base **color** needs an explicit justification:

  ```json
  "$extensions": {
    "pathland": { "baseReference": "why this base color is used directly" }
  }
  ```

### Dark values

Dark values live on `$extensions.pathland.dark`:

```json
{
  "color.primary": {
    "$value": "{base.color.blue.600}",
    "$type": "color",
    "$extensions": { "pathland": { "dark": "{base.color.blue.400}" } }
  }
}
```

The generator emits four token layers:

1. `:root` (light),
2. `@media (prefers-color-scheme: dark) :root:not([data-pathland-scheme])`,
3. `:root[data-pathland-scheme='dark']` (forced dark) and
   `:root[data-pathland-scheme='light']` (forced light),
4. the forced-dark variable family `--pl-dark-*` for explicit `dark.` token
   references.

A reference to another **emitted** token compiles to `var(--pl-<target>)` (and
`var(--pl-dark-<target>)` in the forced-dark family), so the target's dark layer
wins. A reference to a **base** token compiles to its literal. With no explicit
dark value, the dark layers reference the same target variable.

### Internal tokens

Durations, z-index, and `em` icon sizes are renderer-internal (not part of
`spec/TOKENS.md`). Mark them with `$extensions.pathland.internal: true`; they are
still emitted and listed in `build/manifest.json` with `"internal": true`.

## Validation

`npm run check` enforces:

- every Tier-1 token in `pathland_core::TIER1_TOKENS` is present;
- layer direction (semantic → base, component → semantic/base scale) and
  no unresolved references;
- every emitted token has light, platform-dark, forced-dark-scheme, and
  forced-dark variable declarations;
- every `$type: dimension` token is recognized by the canonical
  `isLengthToken` rules in `pathland-render-html::token_spec` (add a rule there
  when introducing a new dimension path);
- durations / z-index / icon sizes are marked internal;
- `build/manifest.json` and `build/tokens.css` match the current sources, and
  base tokens are not emitted;
- `build/base.css.name` matches the SHA-256 of `build/base.css`.

## Serving the stylesheet

The renderer supports two deliveries:

- **inline** (the library default, `render_document` / `render`): the whole
  base design is a `<style>` block in the document head;
- **linked** (`render_document_linked` / Java `renderLinked`): an external
  `<link rel="stylesheet" href="{base}/base-<hash>.css">`. Token overrides
  (`<style data-pathland-tokens>`) and fit-band rules stay inline after it.

The Spring Boot and Quarkus SSR starters use the linked form and serve the
renderer-owned bytes at `{base}/base-<hash>.css` (global and per-app bases) with
`Cache-Control: public, max-age=31536000, immutable`. A new design build gets a
new hash, so the URL busts the cache. Library hosts can keep the inline default
or serve `baseCss()` / `baseCssName()` themselves.

## Preview

`preview/index.html` loads `build/base.css` and renders representative
`.pathland-*` markup (buttons, fields, toggles, slider, progress, stepper, menu,
media) with a light/dark/system switcher. Run `npm run dev -- --serve` and open
<http://127.0.0.1:4173/>. This is a development tool, not an app-selectable base
design.

## Source and generated files

**Source of truth** — edit in OpenPencil, not by hand:

- `design_system.fig` (Base/Semantic/Component variables + Foundations,
  Components, and Showcase pages)

**Downstream token mirror** — currently committed; will be regenerated from the
OpenPencil export once the transform lands:

- `tokens/base/*.tokens.json`
- `tokens/semantic/*.tokens.json`
- `tokens/component/*.tokens.json`

**Hand-authored CSS**:

- `html/reset.css`
- `html/components.css`

**Generated — do not edit**:

- `build/tokens.css`
- `build/manifest.json`
- `build/base.css`
- `build/base.css.name`
- `lib/rust/crates/pathland-render-html/assets/base.css` (synced copy)
- `lib/rust/crates/pathland-render-html/assets/base.css.name` (synced copy)

Edit tokens/components in `design_system.fig` and export; edit
`html/{reset,components}.css` by hand, then rebuild and sync.
