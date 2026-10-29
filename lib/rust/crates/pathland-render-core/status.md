# pathland-render-core — implementation status

**Last updated:** October 27, 2026

The **toolkit-free renderer core shared by every Pathland renderer** (GTK4, Qt,
…): opcode-frame decode into a retained native-element description
(`RenderTree` / `HostNode`) plus the design-token resolution contract
(spec/TOKENS.md). Protocol contract: `spec/`. This crate touches **no toolkit**
(no GTK, no Qt) — it is pure protocol + tokens.

## Implemented

- **Window metadata decode**: `RenderTree` stores `META::SET_WINDOW_METADATA` (`WindowMeta`), shared by the GTK/Qt renderers; empty strings clear fields.

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
    (`resolve_tokens`, reusing `pathland_core::tokens::resolve_ref`); paths
    are kept for re-resolution on scheme change.
  - Explicit `dark.` references resolve the dark layer in either scheme.
  - Previously materialized token values are removed before each resolution,
    so an unresolvable reference (or a reset override) leaves the property
    unset instead of retaining a stale value. A literal `SET_PROPERTY`
    replaces a previous token reference (and vice versa).
  - **Tier-1 default tables** (light + dark) via `concrete_default_tables` —
    concrete platform-appropriate fallbacks; a renderer may enrich with native
    theme colors via `RenderTree::set_defaults`.
  - Platform scheme (`set_scheme`) and application override
    (`set_scheme_override(Option<Scheme>)`, `META::SET_COLOR_SCHEME`) are
    separate; the effective scheme is the override when set.
  - `reset_theme()` / `META::RESET_THEME` clears base + `dark.*` overrides and
    re-resolves against renderer defaults.
  - `concrete_default_tables` now covers the full `TIER1_TOKENS` catalog in
    both schemes (coverage-tested).
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
- No META handling beyond theming (`SET_COLOR_SCHEME`/`RESET_THEME`); `RESET` /
  `ENVIRONMENT` / `RESYNC` remain renderer-owned concerns.
- Web renderers do not yet implement the parent-path fallback (`font.body` →
  `font.body.size`) in CSS; native renderers do via the shared resolver.

## Verified by

`cargo test -p pathland-render-core` — decode of a declarative frame (no
layout/rect data present), delta-frame application (unchanged nodes preserved),
and STRING property resolution from the arena. Full decode/token coverage also
runs through `pathland-render-gtk`'s tests (`cargo test`).