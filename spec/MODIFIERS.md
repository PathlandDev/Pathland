# Pathland Core Modifiers

**Wire protocol version:** 1
**Status:** Draft
**Last Updated:** September 3, 2026

---

## Purpose

This document is the **catalog of core modifiers** the Pathland protocol must
support. In the protocol these are the **`STYLE` properties**: the constraint
properties and visual decorations carried by `STYLE::SET_PROPERTY`. The set
mirrors SwiftUI's core modifiers so that any SwiftUI-shaped UI can be expressed.

Like SwiftUI, modifiers in Pathland are **decoupled from views**: any modifier
applies to any view (`.padding` works on a `Text` and a `VStack` alike). A
modifier that a particular renderer cannot apply is **allowed and ignored** —
the property is still emitted; the renderer skips it. Application code composes
custom modifiers from these core ones.

- View catalog: [PRIMITIVES.md](./PRIMITIVES.md)
- Wire format and value types: [OPCODE.md](./OPCODE.md)
- Events: [EVENTS.md](./EVENTS.md)

> **Implementation status** is tracked per implementing project (a `status.md`
> in each protocol crate/library), **not** in this specification. This document
> defines the protocol contract only.

### Emission rules

Each modifier emits **one or more** `STYLE::SET_PROPERTY` opcodes — one per
underlying property. There is no compound-modifier opcode. Compound SwiftUI
modifiers (`.frame(width:height:alignment:)`, `.shadow(color:radius:x:y:)`,
`.border(_:width:)`) are expanded into several `SET_PROPERTY` opcodes, exactly
as the existing `.frame` modifier already does (`WIDTH` + `HEIGHT` +
`ALIGNMENT`).

`SET_PROPERTY` packing (see OPCODE.md):

```
A = nodeId
B = (valueType << 16) | propertyId
C = value (f32 bit pattern / u32 / u8 / color / arenaRef)
```

Only **changed** properties emit: an unchanged tree emits **zero** opcodes
(diff-based reactive emission). `STRING`-valued properties carry their text in
the frame's string section; `C` holds the arena offset.

### Value types (from `value_type`, u8)

| Value Type | Value | `C` encoding |
|------------|-------|--------------|
| `U8` | 0x01 | low byte of C |
| `U32` | 0x02 | u32 |
| `I32` | 0x03 | i32 |
| `F32` | 0x04 | f32 bit pattern |
| `STRING` | 0x05 | arenaRef (utf8) |
| `ENUM` | 0x06 | low byte of C |
| `COLOR` | 0x07 | packed `0xAARRGGBB` (sRGB) |
| `DESIGN_TOKEN` | 0x08 | arenaRef (token path) |

Special `WIDTH`/`HEIGHT` values: `-1.0` = `FILL` (expand to available), `-2.0` =
`HUG_CONTENT` (native intrinsic size).

> **`maxWidth`/`maxHeight: .infinity`**: SwiftUI's flexible frame is expressed as
> the `FILL` sentinel on `WIDTH`/`HEIGHT`. DSLs accept `∞` (e.g. Rust
> `f32::INFINITY`, Java `Float.POSITIVE_INFINITY`) and normalize it to `FILL`
> before emission, so `.frame(maxWidth: .infinity)` ⇔ `WIDTH = FILL`. The
> renderer then expands the element to the available space (GTK `hexpand`/`Fill`
> alignment, CSS `width:100%`).

> **Token references**: every property typed `COLOR` / `F32` / `STRING` / `ENUM`
> in the tables below MAY instead carry the `DESIGN_TOKEN` value type (`0x08`),
> with `C` = arenaRef to a token path — so modifiers like `.foregroundStyle`,
> `.tint`, `.background`, `.border(color:)`, `.shadow(color:)`, `.font(size:)`,
> `.padding(_:)`, `.cornerRadius(_:)`, `.frame(width:height:)`, and `.spacing(_:)`
> accept either a literal or a token reference. The renderer resolves the
> reference against the [token catalog and color schemes](./TOKENS.md).

---

## 1. Layout

Arrangement and sizing. These map to the native renderer's layout knobs.

| SwiftUI modifier | Protocol property(ies) | Type | Emission |
| ------------------ | ------------------------ | ------ | ---------- |
| `.frame(width:height:alignment:)` | `WIDTH` 0x100B, `HEIGHT` 0x100C, `ALIGNMENT` 0x0002 | F32, F32, ENUM | one `SET_PROPERTY` per provided axis/alignment; `ALIGNMENT` is optional — when omitted (width-only / width+height frames), a stack's own cross-axis alignment is preserved |
| `.frame(minWidth:idealWidth:maxWidth:minHeight:idealHeight:maxHeight:)` | `MIN_WIDTH` 0x0012, `IDEAL_WIDTH` 0x0013, `MAX_WIDTH` 0x0014, `MIN_HEIGHT` 0x0015, `IDEAL_HEIGHT` 0x0016, `MAX_HEIGHT` 0x0017 | F32 | one `SET_PROPERTY` per provided bound |
| `.padding(_:)`, `.padding(edges:)`, `.padding(_:edges:)` | `PADDING` 0x1011, `PADDING_TOP` 0x1012, `PADDING_RIGHT` 0x1013, `PADDING_BOTTOM` 0x1014, `PADDING_LEFT` 0x1015 | F32 | uniform → one; per-edge → per-edge |
| `.offset(x:y:)` | `OFFSET_X` 0x000E, `OFFSET_Y` 0x000F | F32 | two |
| `.position(x:y:)` | `POSITION_X` 0x0010, `POSITION_Y` 0x0011 | F32 | two |
| `.fixedSize()` / `.fixedSize(horizontal:vertical:)` | `FIXED_SIZE_HORIZONTAL` 0x0018, `FIXED_SIZE_VERTICAL` 0x0019 | U8 (0/1) | two (or one when axis-limited) |
| `.layoutPriority(_:)` | `LAYOUT_PRIORITY` 0x001A | F32 | one |
| `.zIndex(_:)` | `Z_INDEX` 0x100F | F32 | one |
| `.aspectRatio(_:contentMode:)` | `ASPECT_RATIO` 0x001B, `CONTENT_MODE` 0x001C | F32, ENUM | two |
| `.scaledToFit()` | `CONTENT_MODE` 0x001C | ENUM (`Fit`=0) | one |
| `.scaledToFill()` | `CONTENT_MODE` 0x001C | ENUM (`Fill`=1) | one |
| `.minimumScaleFactor(_:)` | `MINIMUM_SCALE_FACTOR` 0x001D | F32 | one |
| `.spacing(_:)` (stacks) | `SPACING` 0x0001 | F32 | one |
| `Spacer` | — (implied by component) | — | none |

### Semantics

- **`WIDTH` / `HEIGHT`**: size hints; the renderer maps them to its size
  request / `width`/`height` style. `FILL` (-1.0) expands to available space;
  `HUG_CONTENT` (-2.0) sizes to intrinsic content. Absence of a property leaves
  that axis to the native default.
- **`frame(min/ideal/max)`**: each provided bound emits its own property.
  Renderers map them to min/max/ideal size (GTK `set_size_request`, CSS
  `min-width`/`max-width`/`width`).
- **`PADDING` vs per-edge**: `PADDING` is uniform and shorthand; a per-edge
  modifier overwrites the edge only. Per-edge wins over uniform when both are
  present.
- **`ALIGNMENT` enum**: `Leading`=0, `Center`=1, `Trailing`=2, `Fill`=3.
- **`CONTENT_MODE` enum**: `Fit`=0 (aspect-fit within the bounds),
  `Fill`=1 (aspect-fill, cropped).
- **`OFFSET`** moves the element **after** layout without affecting layout
  (post-layout translation; GTK margins/translation, CSS `transform:
  translate`).
- **`POSITION`** is absolute placement within the parent (CSS `position:
  absolute; left/top`); the renderer resolves the anchor.

---

## 2. Text Formatting

Apply to `Text`, `Label`, `Button` labels, and text-bearing controls. Renderers
map them to their native text attributes.

| SwiftUI modifier | Protocol property(ies) | Type | Emission |
| ------------------ | ------------------------ | ------ | ---------- |
| `.font(.system(size:))` | `FONT_SIZE` 0x1007 | F32 | one |
| `.fontWeight(_:)` | `FONT_WEIGHT` 0x1008 | F32 (100–900) | one |
| `.font(.custom(name:size:))` | `FONT_FAMILY` 0x1009 | STRING | one (arenaRef) |
| `.italic()` / `.fontStyle(_:)` | `FONT_STYLE` 0x1017 | ENUM (`Normal`=0, `Italic`=1) | one |
| `.fontDesign(_:)` | `FONT_DESIGN` 0x1018 | ENUM (`Default`=0, `Serif`=1, `Rounded`=2, `Monospaced`=3) | one |
| `.fontWidth(_:)` | `FONT_WIDTH` 0x1019 | F32 (0.5–1.5, 1.0 default) | one |
| `.kerning(_:)` | `KERNING` 0x101A | F32 | one |
| `.tracking(_:)` | `TRACKING` 0x101B | F32 | one |
| `.baselineOffset(_:)` | `BASELINE_OFFSET` 0x101C | F32 | one |
| `.lineSpacing(_:)` | `LINE_SPACING` 0x101D | F32 | one |
| `.lineLimit(_:)` | `LINE_LIMIT` 0x000B | U32 | one |
| `.multilineTextAlignment(_:)` | `TEXT_ALIGNMENT` 0x000C | ENUM (`Leading`=0, `Center`=1, `Trailing`=2) | one |
| `.truncationMode(_:)` | `TRUNCATION_MODE` 0x000D | ENUM (`Head`=0, `Middle`=1, `Tail`=2) | one |
| `.textCase(_:)` | `TEXT_CASE` 0x101E | ENUM (`None`=0, `Uppercase`=1, `Lowercase`=2) | one |
| `.underline()` | `UNDERLINE` 0x101F | U8 (0/1) | one |
| `.strikethrough()` | `STRIKETHROUGH` 0x1020 | U8 (0/1) | one |
| `.foregroundStyle(_:)` | `COLOR` 0x100A | COLOR | one |
| `.tint(_:)` | `TINT` 0x1030 | COLOR | one |

> **`Color` is never a modifier.** `Color` exists only as a **View** (a
> layout-greedy solid-color fill) and as a **Data Type** passed into
> style-taking modifiers (`.foregroundStyle(_:)`, `.background(_:)`,
> `.border(_:)`, `.tint(_:)`). There is **no** `.color()` modifier and
> `.foregroundColor(_:)` is deprecated — all foreground styling uses
> `.foregroundStyle(_:)`.

\* `FONT_FAMILY` is a `STRING` property (arenaRef), matching SwiftUI, which
passes font families as string names (`.font(.custom("Georgia", size:))`).

### Semantics

- **`FONT_WEIGHT`** is a numeric F32 on the 100–900 scale (`regular`≈400,
  `bold`≈700). Renderers map it to the nearest native weight.
- **`KERNING` vs `TRACKING`**: kerning adjusts per-glyph spacing at a
  point/em scale; tracking adds uniform letter spacing. Both are F32 points.
- **`LINE_LIMIT`** of 0 means unlimited.
- **`TEXT_ALIGNMENT`** differs from stack `ALIGNMENT`: it aligns the text block
  inside its own bounds (CSS `text-align`), not children of a stack.

---

## 3. Appearance & Effects

Visual decoration. These never change layout; they decorate the element.

| SwiftUI modifier | Protocol property(ies) | Type | Emission |
| ------------------ | ------------------------ | ------ | ---------- |
| `.background(_:)` | `BACKGROUND_COLOR` 0x1001 | COLOR | one |
| `.border(_:width:)` | `BORDER_COLOR` 0x1004, `BORDER_WIDTH` 0x1003 | COLOR, F32 | two |
| `.border(_:width:edges:)` | `BORDER_COLOR`, `BORDER_WIDTH`, `BORDER_EDGES` 0x1016 | COLOR, F32, U32 (bitmask) | three |
| `.cornerRadius(_:)` | `BORDER_RADIUS` 0x1005 | F32 | one |
| `.shadow(color:radius:x:y:)` | `SHADOW_COLOR` 0x1021, `SHADOW_RADIUS` 0x1022, `SHADOW_X` 0x1023, `SHADOW_Y` 0x1024 | COLOR, F32, F32, F32 | four |
| `.opacity(_:)` | `OPACITY` 0x100D | F32 (0–1) | one |
| `.blur(radius:)` | `BLUR_RADIUS` 0x1025 | F32 | one |
| `.saturation(_:)` | `SATURATION` 0x1026 | F32 (0–1) | one |
| `.contrast(_:)` | `CONTRAST` 0x1027 | F32 (0–1) | one |
| `.brightness(_:)` | `BRIGHTNESS` 0x1028 | F32 (−1–1) | one |
| `.grayscale(_:)` | `GRAYSCALE` 0x1029 | F32 (0–1) | one |
| `.hueRotation(_:)` | `HUE_ROTATION` 0x102A | F32 (degrees) | one |
| `.colorMultiply(_:)` | `COLOR_MULTIPLY` 0x102B | COLOR | one |
| `.colorInvert()` | `COLOR_INVERT` 0x102C | U8 (0/1) | one |
| `.clipped()` / `.clipsToBounds` | `CLIPS_TO_BOUNDS` 0x1010 | U8 (0/1) | one |
| `.clipShape(_:)` | `CLIPS_TO_BOUNDS` 0x1010 + `SHAPE_KIND` 0x0006 | U8 + ENUM | two (see note) |
| `.transition(_:)` (container hint) | `TRANSITION` 0x1031 | ENUM (`None`=0, `PlatformDefault`=1, `Fade`=2, `Slide`=3, `Scale`=4) | one (presentation hint) |

### Semantics

- **`BORDER_EDGES`** is a u32 bitmask: `TOP`=1, `LEADING`=2, `BOTTOM`=4,
  `TRAILING`=8 (direction-aware; see OPCODE.md). When `.border` has no `edges:`
  argument the renderer treats the mask as `ALL` (0xF).
- **`BORDER_RADIUS`** rounds all corners; a future per-corner variant would add
  `TOP_LEFT_*`/… properties.
- **`SHADOW`** is a compound: exactly four `SET_PROPERTY` opcodes. Missing
  fields default: color = token `color.shadow`, radius = 0, x = 0, y = 0.
- **`clipShape`**: `SHAPE_KIND` (0x0006, defined in
  [PRIMITIVES.md](./PRIMITIVES.md#5-shapes--paints)) selects the clip geometry;
  `CLIPS_TO_BOUNDS` (1) turns clipping on. A bare `.clipped()` is
  `CLIPS_TO_BOUNDS` with no shape.
- **Color effects** (`SATURATION`, `CONTRAST`, …) are renderer-owned filters;
  the renderer maps them to native filter APIs (CSS `filter`, GTK
  `GtkSnapshot` effects). They compose in the order applied.
- **`TRANSITION`** is a presentation hint on a container: when its child
  subtree is structurally replaced (a `NavigationContainer` destination swap, a
  `Conditional.when` branch change) the renderer **may** animate the swap with
  its native transition for the hinted kind (`PlatformDefault` = the platform's
  navigation transition). A renderer that ignores it renders the new subtree
  normally. It is presentation, never state — the renderer animates its own
  rendered-output cache and never stores the application's navigation.

---

## 4. Transform

Geometric transforms applied after layout.

| SwiftUI modifier | Protocol property(ies) | Type | Emission |
| ------------------ | ------------------------ | ------ | ---------- |
| `.rotationEffect(_:anchor:)` | `ROTATION_DEGREES` 0x102D | F32 (degrees, counter-clockwise) | one (anchor is renderer-token-owned) |
| `.scaleEffect(_:anchor:)` | `SCALE` 0x102E | F32 (uniform scale) | one (anchor is renderer-token-owned) |

### Semantics

- Transforms do **not** affect layout; the renderer applies them as a
  post-layout visual transform (CSS `transform`, GTK `gtk_widget_allocate`
  transform).
- **Anchor** is renderer-token-owned (`.center` default); the protocol does not
  transmit anchors — the renderer owns presentation (see OPCODE.md design
  tokens). Non-uniform scale / 3D rotation are future extensions.

---

## 5. Interaction & State

Semantic properties controlling whether and how an element responds to input,
plus accessibility. These are the "semantic" (`0x2000`) properties.

| SwiftUI modifier | Protocol property | Type | Emission |
| ------------------ | ------------------- | ------ | ---------- |
| `.hidden()` | `VISIBLE` 0x100E | U8 (0 = hidden) | one |
| `.disabled(_:)` | `ENABLED` 0x2003 | U8 (1 = enabled) | one |
| `.allowsHitTesting(_:)` | `ALLOWS_HIT_TESTING` 0x102F | U8 (0/1) | one |
| `.controlSize(_:)` | `CONTROL_SIZE` 0x200C | ENUM (`Small`=0, `Regular`=1, `Large`=2) | one |
| `.focusable(_:)` | — (via `FOCUS` listener) | — | none (declares `EVENT_LISTENERS` bit 5) |
| `.accessibilityLabel(_:)` | `LABEL` 0x200A | STRING | one (arenaRef) |
| `.accessibilityRole(_:)` | `ROLE` 0x2001 | ENUM | one |
| `.accessibilityState(_:)` | `STATE` 0x2002 | ENUM | one |

### Semantics

- **`VISIBLE`**: 0 hides the element entirely (removed from layout and hit
  testing by the renderer); 1 shows it. Diff-based emission means toggling
  visibility emits a single `SET_PROPERTY`.
- **`ENABLED`** is the inverse of SwiftUI `.disabled()`: 1 = interactive, 0 =
  disabled. Disabled controls do not emit events.
- **`.focusable`** has no property: the app requests focus events via the
  `FOCUS` listener bit (`EVENT_LISTENERS`, bit 5) and observes
  `EVENT::FOCUS_CHANGED` — see [EVENTS.md](./EVENTS.md).
- **`ROLE`/`STATE`** are accessibility enums; their enumerated values are
  defined in [OPCODE.md](./OPCODE.md#semantic-properties), not here. `STATE` is
  a semantic accessibility property — it never describes visual styling.
- **`LABEL`** is a `STRING` property (the accessibility label), distinct from a
  `TEXT_FIELD`'s caption label.

---

## 6. Navigation

Two properties support routing and URL sync. They are emitted by the
application's `NavigationContainer` ([DSL.md §4.5](./DSL.md#45-navigation)) and
are never chainable modifiers.

| Property | ID | Type | Emission |
|----------|----|------|----------|
| `ROUTE` | 0x2019 | STRING (arenaRef) | one — the current absolute path |
| `NAV_DEPTH` | 0x201A | U32 | one — current back-stack depth |
| `NAV_CHROME` | 0x201B | ENUM | one — chrome mode (at mount; never varies per destination) |
| `TRANSITION` | 0x1031 | ENUM | one — swap transition hint (see [§3](#3-appearance--effects)) |

- **`ROUTE`** carries the current absolute path (`/users/42`). The DOM renderer
  reacts to a `ROUTE` change with `history.pushState`; the initial SSR document
  already carries the correct URL (no push). The application owns navigation
  state; the browser mirrors it. The platform back affordance arrives as the
  `NAVIGATE` event (0x0E) — [EVENTS.md](./EVENTS.md#navigation).
- **`NAV_DEPTH`** is the number of destinations in the app's back-stack
  including the current one (an initial `navigate` is 1; each `push`
  increments; `pop`/`back` decrements; `replace` leaves it unchanged). It lets
  a **native navigation adapter** reconcile its page stack by depth —
  `push` when the route is new and deeper, **replace the top page** when the
  depth is unchanged (a guard redirect), **pop down** on a depth decrease
  (back or a deep-link stack reset), instead of relying on route tags alone
  ([DSL.md §4.5](./DSL.md#45-navigation)). The web renderer ignores it
  (browser history is the depth).
- **`NAV_CHROME`** is the container's chrome mode: `PlatformDefault`=0 (the
  renderer supplies the navigation chrome — the platform's native navigation
  container where one exists, a renderer-drawn back affordance where it does
  not, e.g. DOM), `Custom`=1 (the developer owns all navigation UI — custom
  back buttons/bars in the destinations — and the renderer adds none). Emitted
  once at mount. Renderers MUST treat a missing `NAV_CHROME` as
  `PlatformDefault`. A developer who authors no navigation at all (no
  `NavigationContainer`) gets no navigation — the renderer adds nothing.

---

## Property ID Catalog

### Allocated property IDs

| Range | Group |
| ------- | ------- |
| `0x0001`–`0x0005` | Stack constraint (SPACING, ALIGNMENT, …, CONTENT_MARGINS) |
| `0x0006` | `SHAPE_KIND` (ENUM; view-specific, see PRIMITIVES.md) |
| `0x000A`–`0x000D` | Text (TEXT, LINE_LIMIT, TEXT_ALIGNMENT, TRUNCATION_MODE) |
| `0x000E`–`0x001D` | Layout properties (allocated: OFFSET, POSITION, frame bounds, FIXED_SIZE, LAYOUT_PRIORITY, ASPECT_RATIO/CONTENT_MODE, MINIMUM_SCALE_FACTOR) |
| `0x001E`–`0x00FF` | Future layout/format properties (unallocated) |
| `0x1001`–`0x1016` | Styling (BACKGROUND_COLOR, BORDER_*, FONT_SIZE/WEIGHT/FAMILY, COLOR, WIDTH, HEIGHT, OPACITY, VISIBLE, Z_INDEX, CLIPS_TO_BOUNDS, PADDING_*, BORDER_EDGES) |
| `0x1002` | `IMAGE_SOURCE` (STRING; view-specific, see PRIMITIVES.md) |
| `0x1017`–`0x102F` | Text-format properties (allocated: FONT_STYLE/DESIGN/WIDTH, KERNING, TRACKING, BASELINE_OFFSET, LINE_SPACING, TEXT_CASE, UNDERLINE, STRIKETHROUGH), effect properties (SHADOW_*, BLUR, SATURATION, CONTRAST, BRIGHTNESS, GRAYSCALE, HUE_ROTATION, COLOR_MULTIPLY, COLOR_INVERT), ROTATION_DEGREES, SCALE, ALLOWS_HIT_TESTING |
| `0x1030` | `TINT` (COLOR) | — |
| `0x1031` | `TRANSITION` (ENUM; navigation swap hint) | — |
| `0x1032`–`0x10FF` | Future styling properties (unallocated) |
| `0x2001`–`0x200B` | Semantic (ROLE, STATE, ENABLED, SELECTED, EVENT_LISTENERS, VALUE, MIN_VALUE, MAX_VALUE, LABEL, PROMPT) |
| `0x2009`, `0x200C`–`0x2014` | Control properties (allocated: STEP_VALUE, CONTROL_SIZE, IS_SECURE, PROGRESS, IS_INDETERMINATE, SELECTION, COLOR_VALUE, DATE_PICKER_MODE, PICKER_STYLE) — defined in PRIMITIVES.md controls. Note: a `DATE_PICKER`'s date is set via the `STYLE::SET_DATE` command (0x04), not a property; **`0x2011` is unallocated/reserved** (its former `DATE_VALUE` draft was dropped) |
| `0x2016`–`0x2018` | Binding/action properties (allocated: `ACTION_ID`, `BINDING_ID`, `TOGGLE_STYLE`) — defined in PRIMITIVES.md semantic controls |
| `0x2019` | `ROUTE` (STRING; navigation current path) — see §6 |
| `0x201A` | `NAV_DEPTH` (U32; navigation back-stack depth) — see §6 |
| `0x201B` | `NAV_CHROME` (ENUM; navigation chrome mode) — see §6 |
| `0x2015`, `0x201C`–`0x20FF` | Future semantic properties (unallocated) |

### Reserved ranges

| Range | Use |
|-------|-----|
| `0x0003`, `0x0004` | Reserved (formerly JUSTIFICATION, PADDING) — MUST NOT be reused |
| `0x0007`–`0x0009`, `0x000E`+ high | Draft/future layout |
| `0x1002` | `IMAGE_SOURCE` (allocated) |

A renderer MUST ignore unknown property ids and continue decoding.

### Default value type resolution

The canonical value type per property (the protocol's authoritative mapping):

- `COLOR`, `BACKGROUND_COLOR`, `BORDER_COLOR`, `COLOR_MULTIPLY`,
  `SHADOW_COLOR`, `COLOR_VALUE`, `TINT` → `COLOR`
- `EVENT_LISTENERS`, `BORDER_EDGES` → `U32`
- `SELECTED`, `VISIBLE`, `ENABLED`, `UNDERLINE`, `STRIKETHROUGH`,
  `COLOR_INVERT`, `CLIPS_TO_BOUNDS`, `ALLOWS_HIT_TESTING`, `IS_SECURE`,
  `IS_INDETERMINATE`, `FIXED_SIZE_*` → `U8`
- `LABEL`, `PROMPT`, `FONT_FAMILY`, `IMAGE_SOURCE`, `ROUTE` → `STRING`
- `LINE_LIMIT`, `SELECTION`, `ACTION_ID`, `BINDING_ID` → `U32`
- `ALIGNMENT`, `TEXT_ALIGNMENT`, `TRUNCATION_MODE`, `TEXT_CASE`, `FONT_STYLE`,
  `FONT_DESIGN`, `CONTENT_MODE`, `CONTROL_SIZE`, `SHAPE_KIND`,
  `DATE_PICKER_MODE`, `PICKER_STYLE`, `TOGGLE_STYLE`, `TRANSITION` → `F32`
  (numeric enum code; see the appendix and [OPCODE.md](./OPCODE.md#value-types))
- everything else → `F32`

---

## Appendix: Enumerated values

Single authoritative source for every enum-valued property in the protocol.
A renderer or emitter MUST use these exact numeric codes. The code is carried
in the property value as an **f32 bit pattern** (`value_type::F32`) — see the
[value-type
conventions](./OPCODE.md#value-types) and the [default value type
resolution](#default-value-type-resolution).

| Property | ID | Values |
|----------|----|--------|
| `ALIGNMENT` | 0x0002 | `Leading`=0, `Center`=1, `Trailing`=2, `Fill`=3 |
| `SHAPE_KIND` | 0x0006 | `Circle`=0, `Rectangle`=1, `RoundedRectangle`=2, `Capsule`=3, `Ellipse`=4, `Path`=5 |
| `TEXT_ALIGNMENT` | 0x000C | `Leading`=0, `Center`=1, `Trailing`=2 |
| `TRUNCATION_MODE` | 0x000D | `Head`=0, `Middle`=1, `Tail`=2 |
| `CONTENT_MODE` | 0x001C | `Fit`=0, `Fill`=1 |
| `FONT_STYLE` | 0x1017 | `Normal`=0, `Italic`=1 |
| `FONT_DESIGN` | 0x1018 | `Default`=0, `Serif`=1, `Rounded`=2, `Monospaced`=3 |
| `TEXT_CASE` | 0x101E | `None`=0, `Uppercase`=1, `Lowercase`=2 |
| `CONTROL_SIZE` | 0x200C | `Small`=0, `Regular`=1, `Large`=2 |
| `DATE_PICKER_MODE` | 0x2013 | `Date`=0, `Time`=1, `DateAndTime`=2 |
| `PICKER_STYLE` | 0x2014 | `Menu`=0, `Segmented`=1, `Wheel`=2, `RadioGroup`=3 |
| `TOGGLE_STYLE` | 0x2018 | `Switch`=0 (default), `Checkbox`=1, `Button`=2 |
| `TRANSITION` | 0x1031 | `None`=0, `PlatformDefault`=1, `Fade`=2, `Slide`=3, `Scale`=4 |

Non-`ENUM` scales:

- `FONT_WEIGHT` (0x1008) is a numeric F32 on the 100–900 scale
  (`regular`≈400, `bold`≈700), not an enum.
- `ROLE` (0x2001) and `STATE` (0x2002) are accessibility enums; their value
  sets are defined in [OPCODE.md](./OPCODE.md#semantic-properties) and are not
  reproduced here.

---

## Conformance

New properties added here MUST be reflected in `pathland-core`'s diff emitter
and the golden vectors in [CONFORMANCE.md](./CONFORMANCE.md) before renderers
depend on them.