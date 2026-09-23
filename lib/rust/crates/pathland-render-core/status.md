# pathland-render-core — implementation status

**Last updated:** September 23, 2026

The **toolkit-free renderer core shared by every Pathland renderer** (GTK4, Qt,
…): opcode-frame decode into a retained native-element description
(`RenderTree` / `HostNode`) plus the design-token resolution contract
(spec/TOKENS.md). Protocol contract: `spec/`. This crate touches **no toolkit**
(no GTK, no Qt) — it is pure protocol + tokens.

## Implemented

- **`RenderTree` / `HostNode`** (extracted from `pathland-render-gtk`'s
  `host.rs`, which was already GTK-free):
  - `TREE` ops → node tree: `CREATE_NODE`, `DELETE_NODE`, `INSERT_CHILD`,
    `REMOVE_CHILD`, `MOVE_CHILD` (append/insert at `u32::MAX`), parent/child
    bookkeeping.
  - `STYLE` ops → node properties: `SET_PROPERTY` (numeric `properties`,
    `STRING` resolved from the arena into `strings`, `DESIGN_TOKEN` refs kept
    in `token_refs`), `SET_TEXT`, `SET_DESIGN_TOKEN` (global overrides, base +
    `dark.` split).
  - Node accessors: `node(id)`, `root()`, string/f32/u32 property helpers,
    `checked()`.
- **Design tokens (spec/TOKENS.md renderer contract)**:
  - `STYLE::SET_DESIGN_TOKEN` overrides stored (base + `dark.*` by path
    prefix; STRING-valued overrides resolved from the arena).
  - `DESIGN_TOKEN`-typed `SET_PROPERTY` values record the token path and
    **resolve at apply time** against the theme, the active scheme, and the
    parent-fallback chain into concrete `properties`/`strings`
    (`resolve_tokens`, reusing `pathland_core::tokens`); paths are kept for
    re-resolution on scheme change.
  - **Tier-1 default tables** (light + dark) via `concrete_default_tables` —
    concrete platform-appropriate fallbacks; a renderer may enrich with native
    theme colors via `RenderTree::set_defaults`.
  - `set_scheme` re-resolves every token-referencing property. Scheme is never
    carried by the protocol.
- **`render_tree_from_frame`** — decode a fresh tree from one frame.
- **`describe`** — one-opcode human-readable line (debugging aid).

## Consumed by

- `pathland-render-gtk` (decode + token resolution; the GTK-specific
  `tokens.rs` GTK theme-color enrichment stays in that crate).
- `pathland-render-qt` (planned).
- `pathland-view-native` and `pathland-core-transport` dev-tests (decode a
  frame into a `RenderTree` without pulling in a toolkit).

## Not implemented / gaps

- No layout computation (by design — the protocol never emits rects).
- No META handling (`RESET`/`ENVIRONMENT`/`RESYNC`) — renderer-owned concern.

## Verified by

`cargo test -p pathland-render-core` — decode of a declarative frame (no
layout/rect data present), delta-frame application (unchanged nodes preserved),
and STRING property resolution from the arena. Full decode/token coverage also
runs through `pathland-render-gtk`'s tests (`cargo test`).