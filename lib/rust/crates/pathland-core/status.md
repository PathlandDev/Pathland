# pathland-core — implementation status

**Last updated:** September 25, 2026

The **protocol core**: 16-byte opcode, SPSC ring buffers (both directions),
bump arenas (both directions), memory layout, typed events, and the golden
conformance vectors. The protocol contract is defined in `spec/`; this file
tracks what this crate implements.

## Implemented

- **Component types** (`constants.rs::component_type`): the full grouped map —
  `TEXT 0x01, IMAGE 0x02, COLOR 0x03, SHAPE 0x04, DIVIDER 0x05, SPACER 0x06,
  PROGRESS_VIEW 0x07, GAUGE 0x08, VSTACK 0x10, HSTACK 0x11, ZSTACK 0x12, GRID
  0x13, SCROLLVIEW 0x14, LAZY_VGRID 0x15, LAZY_HGRID 0x16, LAZY_VSTACK 0x1B,
  LAZY_HSTACK 0x1C, BUTTON 0x20, TEXT_FIELD 0x21, TEXT_EDITOR 0x22, TOGGLE
  0x24, SLIDER 0x25, STEPPER 0x26, DATE_PICKER 0x27, PICKER 0x28, MENU 0x29,
  COLOR_PICKER 0x2A, COMMENT 0x7F`.
- **Properties** (`property_id`): the full `spec/MODIFIERS.md` catalog —
  stack/text/styling/semantic IDs plus every draft modifier (`SHAPE_KIND`,
  layout `OFFSET`/`POSITION`/frame bounds/`FIXED_SIZE`/`LAYOUT_PRIORITY`/
  `ASPECT_RATIO`/`CONTENT_MODE`/`MINIMUM_SCALE_FACTOR`, text-format
  `FONT_STYLE`/`FONT_DESIGN`/`FONT_WIDTH`/`KERNING`/`TRACKING`/
  `BASELINE_OFFSET`/`LINE_SPACING`/`TEXT_CASE`/`UNDERLINE`/`STRIKETHROUGH`,
  effects `SHADOW_*`/`BLUR_RADIUS`/`SATURATION`/`CONTRAST`/`BRIGHTNESS`/
  `GRAYSCALE`/`HUE_ROTATION`/`COLOR_MULTIPLY`/`COLOR_INVERT`,
  `ROTATION_DEGREES`/`SCALE`/`ALLOWS_HIT_TESTING`, control drafts
  `STEP_VALUE`/`CONTROL_SIZE`/`IS_SECURE`/`PROGRESS`/`IS_INDETERMINATE`/
  `SELECTION`/`COLOR_VALUE`/`DATE_PICKER_MODE`/`PICKER_STYLE`,
  `ACTION_ID`/`BINDING_ID`/`TOGGLE_STYLE`, `IMAGE_SOURCE`, plus the navigation
  drafts `TRANSITION` (0x1031), `ROUTE` (0x2019, STRING), `NAV_DEPTH` (0x201A,
  U32 — the `NavigationContainer`'s back-stack depth, so native navigation
  adapters reconcile their page stack by depth) and `NAV_CHROME` (0x201B,
  F32-enum chrome mode: `PlatformDefault`=0 / `Custom`=1 — the renderer
  supplies default navigation chrome, or the developer owns all nav UI)).
- **Commands**: `TREE` create/delete/insert/remove/move (append = `u32::MAX`);
  `STYLE` `SET_PROPERTY`/`SET_DESIGN_TOKEN`/`SET_TEXT`/`SET_DATE`; `META`
  `RESET`/`ENVIRONMENT`/`RESYNC`. `META::ENVIRONMENT` is the extensible
  platform-environment field family (`environment::VIEWPORT_WIDTH`/
  `VIEWPORT_HEIGHT`/`ROUTE` — the `ROUTE` string uses the `TEXT_CHANGED`/
  `NAVIGATE` dual convention).
- **Typed `Event` enum**: pointer/key/value/text events (0x01–0x07) plus the
  draft `FocusChanged`, `EditingChanged`, `Submit`, `Scroll`, `Wheel`,
  `DateChanged` (0x08–0x0D) and `Navigate` (0x0E) — all encode/decode
  round-trip. `Navigate { url: Some(_) }` resolves its URL from the event arena
  / batch string section (same dual convention as `TEXT_CHANGED`);
  `Navigate { url: None }` is a native back request (decodes from a bare
  opcode).
- **`Guest::set_date`** helper (`STYLE::SET_DATE`).
- **`Guest::set_design_token`** helper (`STYLE::SET_DESIGN_TOKEN`): global token
  override (`path` arena string, `valueType`, `value`), incl. `dark.`-prefixed
  dark variants (spec/TOKENS.md).
- **`Guest::set_design_token_string`** helper: STRING-valued token override —
  both the path and the value ride the arena (`A` = path ref, `B` = STRING,
  `C` = value ref).
- **`pathland_core::tokens`** — the **reference resolution algorithm**
  (spec/TOKENS.md): `dark.*` layer → override → default → parent-fallback →
  fallback, the generative `space.<N>` family (`space.base` × N), and the
  `TokenValue`/`Scheme`/`TokenTables` types. Pure `no_std` + `alloc`;
  renderers keep their own tables and call `tokens::resolve`. The full
  "Design-Token Resolution Conformance" table (spec/CONFORMANCE.md) is tested
  here.
- **`value_type_for`** matches `spec/MODIFIERS.md`'s canonical mapping
  (COLOR / U32 / U8 / STRING / F32-enum-code); the `DESIGN_TOKEN` value type
  (`0x08`) is available per-instance on `SET_PROPERTY`.
- **Listener bits**: 0–9 (`POINTER_*`, `KEY_*`, `FOCUS`, `EDITING`, `SUBMIT`,
  `SCROLL`, `WHEEL`).
- **`constants::role`** — the canonical **`ROLE` enum catalog** (spec/OPCODE.md
  §semantic properties): the 20 classic accessibility roles (0–19) plus the
  landmark/structural roles `BANNER`/`NAVIGATION`/`MAIN`/`CONTENT_INFO`/
  `COMPLEMENTARY`/`ARTICLE`/`SECTION`/`SEARCH`/`LIST_ITEM`/`PARAGRAPH`
  (20–29). Single source the renderers, DSLs, and `pathland-ts-codegen` bind
  to (the TS `ROLE_*` constants are generated from it).
- **Shared linear memory**: 80-byte header, guest→host ring, host→guest event
  ring, guest arena, host→guest **event arena** (two-way string section — a
  host `send_event(TextChanged)` round-trips text over the shared ring).
- **Conformance vectors** (`conformance.rs`): TREE/STYLE/META/EVENT golden
  bytes **incl. vectors 17–18, 20–27** (`SET_DESIGN_TOKEN` (COLOR +
  STRING-valued), `DESIGN_TOKEN`-typed `SET_PROPERTY`, `NAVIGATE`±URL,
  `ROUTE`, `TRANSITION`, `META::ENVIRONMENT` VIEWPORT_WIDTH + ROUTE) and a ring
  test proving `Guest::set_design_token` emits vector 17 byte-exactly;
  `cargo test` enforces them.

## Not implemented / gaps

- `ENVIRONMENT` (`META`) is a constant + the field-id family; the **string
  codec over the shared ring** (event-arena absolute offset for a `ROUTE`
  field) and a native emitter are not wired yet — the web path encodes/decodes
  it in the TS client + Java `FrameCodec` (batch string section).
- Enum *value* codes (e.g. `TOGGLE_STYLE=Switch=0`) are used inline; there are
  no named value constants. (`ROLE` is now the canonical exception — see
  `constants::role` above; the remaining enum codes live in the specs + the
  `pathland-ts-codegen` enum table.)
- No **general STRING-property diff path** in `pathland-engine` (the engine
  stores numeric properties + design-token refs only). `ROUTE` (STRING) will
  need a small string-property capability when the Rust router lands (Phase 3);
  `TRANSITION` (F32) is handled by the generic property diff today.

## Verified by

`cargo test -p pathland-core` — conformance vectors, opcode round-trips,
arena, ring, event-ring (incl. shared-ring `TEXT_CHANGED` + `Navigate` round
trips and `META::RESET` event-arena recycle), randomized tests.