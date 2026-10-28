# Pathland base design

The built-in look of the HTML renderer, generated from a **three-layer Style
Dictionary pipeline** plus hand-authored reset/component CSS.

```
tokens/base/*.tokens.json        internal palette + scales          (not emitted)
tokens/semantic/*.tokens.json    protocol catalog semantics        (emitted)
tokens/component/*.tokens.json   per-component surface             (emitted)
html/reset.css                   Tailwind-derived preflight        (hand-authored)
html/components.css              .pathland-* rules                 (hand-authored)
```

The generated bundle is `build/base.css` (reset + `tokens.css` + components).
`lib/rust/crates/pathland-render-html/assets/base.css` is the synced, committed
copy the renderer embeds with `include_str!`; the renderer has one built-in base
design and no app-facing design selection.

## Commands

```sh
npm --prefix design ci          # install (CI / clean checkout)
npm --prefix design run build   # Style Dictionary + build/base.css
npm --prefix design run check   # catalog/layer/dark/dimension validation
npm --prefix design run dev     # rebuild on token/CSS changes
npm --prefix design run dev -- --serve   # also serve preview/ on :4173
scripts/sync-design-css.sh      # copy build/base.css into the Rust crate
scripts/sync-design-css.sh --check
```

After changing tokens or CSS: `npm --prefix design run build`, then
`scripts/sync-design-css.sh`, then run `cargo test -p pathland-render-html`.

`build/` and `node_modules/` are gitignored. `package-lock.json` is committed.

## Tokens

Each token is a flat DTCG entry: `$value`, `$type`, optional `$description` and
`$extensions.pathland`. Keys carry their full dotted path because the catalog
contains paths that are both a leaf and a group (`control.border` is a color,
`control.border.width` is a dimension), which nested DTCG trees cannot represent.

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
  base tokens are not emitted.

## Preview

`preview/index.html` loads `build/base.css` and renders representative
`.pathland-*` markup (buttons, fields, toggles, slider, progress, stepper, menu,
media) with a light/dark/system switcher. Run `npm run dev -- --serve` and open
<http://127.0.0.1:4173/>. This is a development tool, not an app-selectable base
design.

## Generated files — do not edit

- `build/tokens.css`
- `build/manifest.json`
- `build/base.css`
- `lib/rust/crates/pathland-render-html/assets/base.css` (synced copy)

Edit the token files or `html/{reset,components}.css`, then rebuild and sync.
