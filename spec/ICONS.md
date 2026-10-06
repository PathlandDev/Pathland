# Icons — the `ICON` primitive and canonical vocabulary

The `ICON` primitive (0x0B) renders a **renderer-native symbol** for a canonical,
semantic name written once in the UI model. Unlike `IMAGE` (an asset loaded from
a URL), an icon is a *word* the renderer maps onto its platform's native icon
set.

> **The spec is the vocabulary contract only.** It lists the icons that are
> spec'ed — the `ICON_NAME` values every renderer MUST understand. **Which
> native glyph each renderer draws for a name is renderer-owned** (like the
> concrete values of design tokens): the mapping never appears in the spec;
> each renderer defines its own.

The **application owns the intent** (the neutral name); the **renderer owns the
visual** (which glyph, its default size, its weighting). This mirrors the design
tokens' split (spec/TOKENS.md).

## Protocol

- **Component**: `ICON` `0x0B` (leaf).
- **Property**: `ICON_NAME` `0x1038` (`STRING`, arena ref) — the canonical name.
- Reuses: `LABEL` (accessibility — a `LABEL` makes the icon presentable, absent
  makes it decorative), `COLOR` / `foregroundStyle` (tint), `FONT_SIZE` (size —
  text-style symbol sizing; the renderer owns its default size when absent), and
  the frame/opacity modifiers.
- `ICON` is a leaf like `IMAGE` — no new events, no children.

## Canonical vocabulary

`ICON_NAME` values are the *protocol contract*. The canonical names live in
`pathland_core::constants::icon` (`NAMES`, `is_canonical`) — the single
protocol-authoritative list every renderer and DSL binds to. An app may
reference a name the vocabulary does not (yet) contain; renderers fall back
however they own the mapping does, and the vocabulary can grow.

- **Navigation**: `home` `search` `menu` `close` `chevron-left` `chevron-right`
  `chevron-up` `chevron-down` `arrow-left` `arrow-right`
- **Actions**: `add` `remove` `check` `edit` `delete` `save` `share` `download`
  `upload` `refresh` `lock` `logout`
- **Media**: `play` `pause` `stop` `skip-back` `skip-forward` `volume`
  `volume-mute` `shuffle` `repeat` `music`
- **Status**: `info` `warning` `error` `success` `heart` `star` `bell` `cloud`
- **People**: `user` `users`
- **System**: `settings` `grid` `list` `filter`
- **Content**: `folder` `file` `image`

## Semantics

- **Authoring**: `Icon.of(IconName.PLAY)` / `Icon.of("play")` (a canonical
  string, or an app-extension name), `Label.of(title, Icon)`,
  `Button.of(Icon.of(…) , action)`.
- **Size / tint**: `FONT_SIZE` / `COLOR` as on text. Sized like a symbol (the
  renderer owns its default size); tinted for the active scheme.
- **Reactivity**: the name is a single `Signal<String>` — a change re-emits only
  the node's `ICON_NAME` delta.
- **Accessibility**: `LABEL` makes the icon presentable; without one the icon is
  decorative (not announced, not interactive).

## Adding an icon

1. Add the name to `spec/ICONS.md` + `pathland_core::constants::icon` (the
   protocol vocabulary).
2. Add the `IconName` variant in the Java and Rust DSLs (canonical value — the
   Rust variant binds `pathland_core::icon::…`).
3. Each renderer adds its **own** glyph for the name (renderer-owned mapping).
4. Test: the core vocabulary is canonical + unique, and every renderer covers
   the vocabulary.