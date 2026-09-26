# Pathland Opcode Protocol Specification

**Wire protocol version:** 1
**Status:** Draft
**Format:** Fixed-size (16-byte) opcode engine
**Last Updated:** September 3, 2026

---

## Overview

The Pathland Opcode Protocol is the **fixed-size instruction engine** at the heart of the Rust-first architecture. The **opcode engine** emits the application's **declarative view structure** — VStack, HStack, Text, spacing, padding, alignment — as a stream of fixed 16-byte opcodes into a **ring buffer** in **linear memory**. The host renderer consumes the ring and maps the structure onto that platform's **native elements** (GTK4 widgets, DOM elements, HTML).

**The engine does not compute layout and does not emit rects.** Native renderers lay out their own native elements from the constraint properties the engine passes through. This is the Pathland principle: *always render through that platform's native elements.*

This specification supersedes the historical variable-length binary instruction format (which has been removed from the repository).

### Goals

- **Sub-millisecond execution**: every opcode is a fixed 16 bytes; emission is a diff over the retained tree with **zero dynamic heap allocations** in the steady state.
- **L1 CPU cache alignment**: a 64-byte cache line holds exactly **4 opcodes**. Ring slots are 16-byte aligned and densely packed.
- **Reactive emission**: only the nodes that changed emit opcodes. An unchanged tree emits **zero** opcodes (see [Reactive Emission](#reactive-emission)).
- **Linear, deterministic decoding**: the host walks the ring with a single cursor.

### Core Principles

1. **Fixed-size instructions**: every opcode is exactly 16 bytes — no length prefixes, no skippable-variable framing.
2. **Declarative, not positioned**: the engine describes *what* the UI is (stacks, text, constraints), never *where* it is. Native elements compute their own positions.
3. **Numeric IDs**: categories, commands, component types, and properties are numeric.
4. **Native elements**: renderers map the opcode stream onto that platform's native elements — never a generic canvas unless a platform has no native equivalent.
5. **Zero-copy**: the ring and arena are plain regions of the same linear memory shared with the host.
6. **Single-producer / single-consumer**: the guest engine produces; the host renderer consumes. No locks required.

---

## Opcode Format

Every opcode is exactly **16 bytes**:

```
[Category: u8][Command: u8][Flags: u16][A: u32][B: u32][C: u32]
```

| Field | Size | Offset | Description |
|-------|------|--------|-------------|
| `Category` | 1B | 0 | Opcode category (see [Category Table](#category-table)) |
| `Command` | 1B | 1 | Command within the category (see [Command Tables](#command-tables)) |
| `Flags` | 2B | 2 | Category/command-specific flags |
| `A` | 4B | 4 | Primary payload |
| `B` | 4B | 8 | Secondary payload |
| `C` | 4B | 12 | Tertiary payload |

All multi-byte values are **little-endian**. The Rust reference type is `#[repr(C, packed)]`:

```rust
#[repr(C, packed)]
pub struct Opcode {
    pub category: u8,
    pub command: u8,
    pub flags: u16,
    pub a: u32,
    pub b: u32,
    pub c: u32,
}
```

`size_of::<Opcode>() == 16`.

### Payload encoding conventions

- **Float payloads** (`spacing`, `padding`, sizes) are stored as raw `f32` (IEEE 754) bit patterns in a `u32` field.
- **Signed payloads** (the `I32` value type, and dedicated commands like `SET_DATE`'s days-since-epoch) are stored as the raw two's-complement bit pattern of a signed value in a `u32` field; a consumer reinterprets the field as `i32`.
- **Node IDs** are `u32`.
- **Component types / property IDs** are `u16`; when packed into a 4-byte payload they occupy the low two bytes (the high bytes are reserved / zero unless documented).
- **Arena references** are a `u32` byte offset into the arena region (see [Arena](#arena)).
- **Colors** are packed `0xAARRGGBB` in sRGB (see [Colors](#colors)).

### Reserved ranges

- `Category == 0` is reserved (invalid).
- `Command == 0` in any category is reserved (invalid).
- `Category 0x80-0xFF`: custom/extended categories.

---

## Category Table

| Category | Value | Name | Producer | Description |
|----------|-------|------|----------|-------------|
| `TREE` | `0x01` | Tree mutations | Guest engine | Node create/delete/insert/remove/move |
| `STYLE` | `0x02` | Constraints & tokens | Guest engine | Property values (spacing, padding, …), design tokens |
| `EVENT` | `0x03` | Raw inputs | Host renderer | Pointer down/move/up, key down/up |
| `META` | `0x04` | Control | Both | Reset, environment |

---

## Command Tables

### TREE (0x01)

The `componentType` values (the primitive views the protocol supports) are
catalogued in [PRIMITIVES.md](./PRIMITIVES.md), grouped by their SwiftUI
counterpart. Component IDs are allocated there spec-first; this file remains
the wire-format authority.

| Command | Value | A | B | C | Flags | Description |
|---------|-------|---|---|---|-------|-------------|
| `CREATE_NODE` | `0x01` | nodeId | componentType (u16, low) | 0 | — | Create a node of `componentType` |
| `DELETE_NODE` | `0x02` | nodeId | 0 | 0 | — | Delete a node and its subtree |
| `INSERT_CHILD` | `0x03` | parentId | childId | index | — | Insert `childId` into `parentId` at `index`; append is `index = u32::MAX` (`APPEND`) |
| `REMOVE_CHILD` | `0x04` | parentId | childId | 0 | — | Remove `childId` from `parentId` |
| `MOVE_CHILD` | `0x05` | parentId | childId | newIndex | — | Move child to `newIndex` (after removal); append is `newIndex = u32::MAX` (`APPEND`) |

### STYLE (0x02)

The engine does not emit positions. `STYLE` carries the **constraint properties**
that native renderers feed to their own layout — spacing, alignment, FILL/HUG
size hints — plus **styling modifiers** (padding, colors, fonts, borders).

| Command | Value | A | B | C | Flags | Description |
|---------|-------|---|---|---|-------|-------------|
| `SET_PROPERTY` | `0x01` | nodeId | propertyId (u16, low) + valueType (u8, high byte) | value | — | Set a constraint/style property; `value` depends on `valueType` |
| `SET_DESIGN_TOKEN` | `0x02` | arenaRef (path) | valueType (u8) | value | — | Override a design token; path is an arena string |
| `SET_TEXT` | `0x03` | nodeId | arenaRef (utf8) | 0 | — | Set a node's text content |
| `SET_DATE` | `0x04` | nodeId | days since epoch (I32) | millis of day (U32) | — | Set a node's date value (e.g. a `DATE_PICKER`); see [EVENTS.md](./EVENTS.md#date) for the matching `DATE_CHANGED` event |

`SET_PROPERTY` encodes the value type in the **high byte of `B`** (bits 24–31) and the property id in the low two bytes:

```
B = (valueType << 16) | propertyId
```

#### Value types

| Value Type | Value | C / value encoding |
|------------|-------|--------------------|
| `U8` | `0x01` | low byte of C |
| `U32` | `0x02` | u32 |
| `I32` | `0x03` | i32 |
| `F32` | `0x04` | f32 |
| `STRING` | `0x05` | arenaRef (utf8) |
| `ENUM` | `0x06` | low byte of C |
| `COLOR` | `0x07` | packed `0xAARRGGBB` |
| `DESIGN_TOKEN` | `0x08` | arenaRef (token path) |

> **Enum-valued properties** (`ALIGNMENT`, `TEXT_ALIGNMENT`, `TRUNCATION_MODE`,
> `ROLE`, `STATE`, and the draft `FONT_*`/`CONTENT_MODE`/`CONTROL_SIZE`/`SHAPE_KIND`
> properties) are carried with the **`F32` value type**, holding the numeric enum
> code as an f32 bit pattern (e.g. `ALIGNMENT=Center(1)` → `C = 1.0f32.to_bits()`).
> The enumerated code tables live in
> [MODIFIERS.md](./MODIFIERS.md#appendix-enumerated-values). The `ENUM` value type
> (low byte of `C`) is available for explicitly `ENUM`-coded properties.
>
> **Token references**: any property that normally carries a `COLOR` / `F32` /
> `STRING` / `ENUM` value MAY instead carry the `DESIGN_TOKEN` value type, with
> `C` = arenaRef to a token path (see [Design Token System](#design-token-system)
> and [TOKENS.md](./TOKENS.md)).

#### Constraint properties for native layout

The following properties drive native layout; the renderer maps them to its
native equivalents (e.g. `SPACING` → GTK box spacing / CSS `gap`, `WIDTH`/`HEIGHT`
→ size requests). Special `WIDTH`/`HEIGHT` values: `-1` = FILL (expand to
available), `-2` = HUG_CONTENT (native intrinsic size).

| Property | Value | Type | Native meaning |
|----------|-------|------|----------------|
| `SPACING` | `0x0001` | F32 | Gap between children |
| `ALIGNMENT` | `0x0002` | F32 (enum code) | Cross-axis alignment |
| `CONTENT_MARGINS` | `0x0005` | F32 | Uniform inset between a stack's edge and its content |
| `WIDTH` | `0x100B` | F32 | Width hint (-1 FILL, -2 HUG) |
| `HEIGHT` | `0x100C` | F32 | Height hint (-1 FILL, -2 HUG) |

#### Text properties

Text-bearing nodes (`TEXT`, `BUTTON` labels, `TEXT_FIELD`) carry their content
via `SET_TEXT` (0x03) and their layout via these properties:

| Property | Value | Type | Native meaning |
|----------|-------|------|----------------|
| `TEXT` | `0x000A` | STRING | The text content (a `SET_TEXT`-style arena string) |
| `LINE_LIMIT` | `0x000B` | U32 | Maximum number of lines (0 = unlimited) |
| `TEXT_ALIGNMENT` | `0x000C` | F32 (enum code) | Alignment of the text block within its bounds |
| `TRUNCATION_MODE` | `0x000D` | F32 (enum code) | Truncation style when text overflows (`Head`/`Middle`/`Tail`) |

Enumerated values for these and every other enum-valued property are
consolidated in [MODIFIERS.md](./MODIFIERS.md#appendix-enumerated-values).

#### Styling properties (modifiers)

Styling properties are **modifiers** — they apply to any view, not just stacks —
and map to visual decoration rather than layout. `PADDING` (uniform) and its
per-edge variants map to widget margins / CSS `padding`, alongside
`COLOR`/`BACKGROUND_COLOR`/`BORDER_*`:

| Property | Value | Type | Native meaning |
|----------|-------|------|----------------|
| `PADDING` | `0x1011` | F32 | Uniform inner padding (any view) |
| `PADDING_TOP` | `0x1012` | F32 | Top padding |
| `PADDING_RIGHT` | `0x1013` | F32 | Right padding |
| `PADDING_BOTTOM` | `0x1014` | F32 | Bottom padding |
| `PADDING_LEFT` | `0x1015` | F32 | Left padding |
| `BORDER_EDGES` | `0x1016` | U32 | Which border edges to draw (bitmask, see [`border_edges`] flags) |
| `TRANSITION` | `0x1031` | F32 (enum code) | Presentation hint for structural swaps (a `NavigationContainer` destination swap, a `Conditional.when` branch change): `None`=0, `PlatformDefault`=1, `Fade`=2, `Slide`=3, `Scale`=4 — the renderer **may** animate, never stores navigation state |

`BORDER_EDGES` is a u32 bitmask (`SET_PROPERTY` with the `U32` value type)
selecting which edges of a node's border are drawn. Bits are direction-aware:

| Bit | Mask | Edge | Physical (LTR) |
|-----|------|------|----------------|
| 0 | `0x00000001` | `TOP` | top |
| 1 | `0x00000002` | `LEADING` | left |
| 2 | `0x00000004` | `BOTTOM` | bottom |
| 3 | `0x00000008` | `TRAILING` | right |

Full property catalog: see [MODIFIERS.md](./MODIFIERS.md) — the core modifiers
(the protocol's `STYLE` properties) grouped by SwiftUI modifier, with the same
IDs — and the Rust `constants.rs` (carried forward from the historical
protocol).

#### Semantic properties

Semantic properties describe a node's meaning and interaction state rather than
its layout or decoration. Currently exercised: `SELECTED` carries the boolean
checked state of a `TOGGLE` (`SET_PROPERTY` with the `U8` value type, `0` = off,
`1` = on). `EVENT_LISTENERS` (below) declares which raw inputs a node wants
reported; `ACTION_ID`/`BINDING_ID` identify bound callbacks that gate event
delivery (see [EVENTS.md](./EVENTS.md#transport-aware-event-guards-must)).

| Property | Value | Type | Meaning |
|----------|-------|------|---------|
| `ROLE` | `0x2001` | ENUM (F32 code) | Accessibility role (see below) |
| `STATE` | `0x2002` | ENUM (F32 code) | Control/interaction state (see below) |
| `ENABLED` | `0x2003` | U8 | Whether the control is enabled |
| `SELECTED` | `0x2004` | U8 | Checked/selected state of a `TOGGLE` |
| `EVENT_LISTENERS` | `0x2005` | U32 | Bitmask of raw-input events to report |
| `VALUE` | `0x2006` | F32 | Current value of a value-bearing control (e.g. `SLIDER`) |
| `MIN_VALUE` | `0x2007` | F32 | Inclusive minimum of a `SLIDER`'s range |
| `MAX_VALUE` | `0x2008` | F32 | Inclusive maximum of a `SLIDER`'s range |
| `LABEL` | `0x200A` | STRING | Caption label of a `TEXT_FIELD` |
| `PROMPT` | `0x200B` | STRING | Placeholder text of a `TEXT_FIELD` |
| `ACTION_ID` | `0x2016` | U32 | Bound callback id; gates event delivery for this node |
| `BINDING_ID` | `0x2017` | U32 | Two-way binding id (control value ↔ app state) |
| `TOGGLE_STYLE` | `0x2018` | ENUM (F32 code) | Visual style token for a `TOGGLE`: `Switch`=0, `Checkbox`=1, `Button`=2 |
| `ROUTE` | `0x2019` | STRING | Current navigation path (absolute, e.g. `/users/42`); drives web URL sync — see [DSL.md §4.5](./DSL.md#45-navigation) |
| `NAV_DEPTH` | `0x201A` | U32 | Navigation back-stack depth (destinations in the app's path incl. current; `push`+1, `pop`−1, `replace` unchanged); lets native navigation adapters reconcile their page stack by depth — see [DSL.md §4.5](./DSL.md#45-navigation) |
| `NAV_CHROME` | `0x201B` | F32 (enum code) | Navigation chrome mode on a `NavigationContainer` slot: `PlatformDefault`=0 (renderer supplies chrome — native container where one exists, renderer-drawn back affordance on DOM), `Custom`=1 (developer owns all nav UI; renderer adds none). Emitted once at mount; missing = `PlatformDefault` — see [DSL.md §4.5](./DSL.md#45-navigation) |
| `TEXT_STYLE` | `0x1032` | ENUM (F32 code) | Predefined typography from the design system (see below). The heading styles imply a heading element on a `TEXT`; raw font modifiers override the visual on top and never imply a heading |

**`TEXT_STYLE` enumerated values** (predefined typography; carried as an `F32` numeric code, `value_type::F32`):

| Value | Style | Heading |
|-------|-------|---------|
| 0 | `LargeTitle` | `<h1>` |
| 1 | `Title` | `<h2>` |
| 2 | `Title2` | `<h3>` |
| 3 | `Title3` | `<h4>` |
| 4 | `Headline` | `<h5>` |
| 5 | `Subheadline` | — (`<span>`) |
| 6 | `Body` | — (`<span>`) |
| 7 | `Callout` | — (`<span>`) |
| 8 | `Footnote` | — (`<span>`) |
| 9 | `Caption` | — (`<span>`) |
| 10 | `Caption2` | — (`<span>`) |

**`ROLE` enumerated values** (semantic structure; carried as an `F32` numeric code, `value_type::F32`):

| Value | Role | Element |
|-------|------|---------|
| 0 | `None` (no semantic role) | — |
| 1 | `Header` (a heading) | `<h2>` (raised by a heading `TEXT_STYLE`) |
| 2 | `Paragraph` | `<p>` |
| 3 | `List` | `<ul>` |
| 4 | `ListItem` | `<li>` |
| 5 | `Summary` | `<section>` |
| 6 | `Banner` (site header landmark) | `<header>` |
| 7 | `Navigation` (site nav landmark) | `<nav>` |
| 8 | `Main` (main content landmark) | `<main>` |
| 9 | `ContentInfo` (site footer landmark) | `<footer>` |
| 10 | `Complementary` (aside landmark) | `<aside>` |
| 11 | `Article` | `<article>` |
| 12 | `Section` | `<section>` |
| 13 | `Search` | `<search>` |

> **`ROLE` is semantic structure only.** Interactive/control roles (button, link,
> checkbox, slider, toggle, menu, text field, …) are **not roles** — they are
> intrinsic to the control components (a `BUTTON` renders as a `<button>`, a
> `TOGGLE` as an `<input type="checkbox">`/`role="switch"`, a `MENU` as
> `role="menu"`), and a custom-looking button is expressed with `Button` +
> `ButtonStyle`. The web renderer maps each semantic role onto its native element
> on a generic `div`/`span` shell; control components keep their native element
> and never take an ARIA role from `ROLE`. **Headings** come from a heading
> `TEXT_STYLE` typography (always `<h1>`–`<h5>`) or from `ROLE=Header` (default
> `<h2>`); a raw font modifier never implies a heading. Non-heading text defaults
> to `<span>`.

**`STATE` enumerated values** (control/interaction state; carried as an `F32` numeric code, `value_type::F32`):

| Value | State |
|-------|-------|
| 0 | `Normal` (no special state) |
| 1 | `Disabled` |
| 2 | `Focused` |
| 3 | `Pressed` |
| 4 | `Selected` |
| 5 | `Expanded` |
| 6 | `Busy` |

`STATE` is a **semantic/accessibility** property the application sets; it never
describes visual styling (hover/press styles stay renderer/token-owned).

`STRING` properties carry their text in the frame's string section: the
property value is the *relative* offset of a length-prefixed entry, exactly as
`SET_TEXT`'s `B` field does.

`TOGGLE` (`0x24`) is a boolean control whose visual style is a **token**, not a
separate component: `TOGGLE_STYLE` (`0x2018`, ENUM) selects the native variant —
`Switch` (sliding pill), `Checkbox` (square with checkmark), or `Button`
(toggle button). The checked state is `SELECTED` (`0x2004`, U8 0/1), emitted
guest → host and reported back host → guest. The renderer owns the visual
presentation (see [Design Token System](#design-token-system)).

`SLIDER` (`0x25`) is a continuous numeric control: the guest emits its
`VALUE` plus the `MIN_VALUE`/`MAX_VALUE` range, and the renderer reports value
changes back as `VALUE_CHANGED` events (host → guest), resolving the semantic
value from its own track geometry (see [EVENT](#event-0x03--host--guest)).

### EVENT (0x03) — host → guest

The event commands and their `EVENT_LISTENERS` bits are catalogued in
[EVENTS.md](./EVENTS.md) (core events, SwiftUI-style); this file defines the
wire encoding.

Events are the **raw inputs**: the renderer reports what happened (a pointer
went down/moved/up, a key went down/up) and resolves which node it hit via its
own rendered-output tree. High-level interpretations — tap, drag, long-press —
are built by the guest/app from these raw inputs and are **not** part of the
protocol.

#### Event listeners (which nodes emit events)

A renderer reports raw events only for nodes that declared they want them. The
application declares this intent through the `EVENT_LISTENERS` semantic
property (`0x2005`, a u32 bitmask, `SET_PROPERTY` with the `U32` value type).
Each bit requests a raw-input event kind (see [`listener`] flags):

| Bit | Mask | Event command | Meaning |
|-----|------|---------------|---------|
| 0 | `0x00000001` | `POINTER_DOWN` | Report pointer-down for this node |
| 1 | `0x00000002` | `POINTER_MOVE` | Report pointer-move/enter/leave |
| 2 | `0x00000004` | `POINTER_UP` | Report pointer-up for this node |
| 3 | `0x00000008` | `KEY_DOWN` | Report key-down |
| 4 | `0x00000010` | `KEY_UP` | Report key-up |

> Bits 5–9 (draft) are allocated in [EVENTS.md](./EVENTS.md#event-listeners-event_listeners)
> for `FOCUS_CHANGED`, `EDITING_CHANGED`, `SUBMIT`, `SCROLL`, and `WHEEL`.
> Value-bearing controls (`SLIDER`, `TOGGLE`, `DATE_PICKER`, …) report their
> value changes by component type and do not need a listener bit.

This is what makes **any element** (a `Text`, a `VStack`, …) able to emit
events — the renderer attaches native input recognition to the matching native
element when the listener bit is set, not only to buttons. A renderer SHOULD
report `POINTER_DOWN`/`POINTER_UP` for a node with the `POINTER_DOWN`/
`POINTER_UP` bits set by attaching a native click/press gesture to that
element.

| Command | Value | A | B | C | Flags | Description |
|---------|-------|---|---|---|-------|-------------|
| `POINTER_DOWN` | `0x01` | targetId | x (f32) | y (f32) | `POINTER_SECONDARY` | Pointer button pressed at viewport-relative point |
| `POINTER_MOVE` | `0x02` | targetId | x (f32) | y (f32) | `HOVER_ENTER` / `HOVER_LEAVE` | Pointer moved (bit 0 = entered/hovering, bit 1 = leaving) |
| `POINTER_UP` | `0x03` | targetId | x (f32) | y (f32) | `POINTER_SECONDARY` | Pointer button released at viewport-relative point |
| `KEY_DOWN` | `0x04` | targetId | keyCode (u16, low) | modifiers (u8, low) | `KEY_REPEAT` | Key pressed (bit 1 = auto-repeat) |
| `KEY_UP` | `0x05` | targetId | keyCode (u16, low) | modifiers (u8, low) | — | Key released |
| `VALUE_CHANGED` | `0x06` | targetId | value (f32) | 0 | — | A value-bearing control changed (e.g. slider); the renderer resolves the semantic value from its track geometry |
| `TEXT_CHANGED` | `0x07` | targetId | string offset | 0 | — | A text field's value changed; the new text is a length-prefixed entry in the **event arena** (shared memory, absolute `B` offset) or the batch's string section (network, *relative* `B` offset) — the same dual convention as `STYLE::SET_TEXT` |
| `NAVIGATE` | `0x0E` | 0 | URL string offset | 0 | `NAVIGATE_URL` | Global navigation request: with the flag, `B` is the destination URL (browser `popstate`/back/forward/deep-link, same dual string convention as `TEXT_CHANGED`); without the flag, "back one step" (native back affordance). Never node-keyed, never `EVENT_LISTENERS`-gated — see [EVENTS.md](./EVENTS.md#navigation) |

### META (0x04)

| Command | Value | A | B | C | Description |
|---------|-------|---|---|---|-------------|
| `RESET` | `0x01` | 0 | 0 | 0 | Host must clear all rendered output |
| `ENVIRONMENT` | `0x02` | fieldId (u16, low) | field value | 0 | A platform environment field (host → guest) — see [Environment fields](#environment-fields) |
| `RESYNC` | `0x03` | 0 | 0 | 0 | The host (renderer) requests a **full snapshot** of the current tree (host → guest). The guest answers with a single full-snapshot batch (the same TREE + STYLE stream as a mount). Used for reconnect/gap recovery and no-JS refresh. |

#### Environment fields

`META::ENVIRONMENT` is an **extensible field family** (mirroring
`STYLE::SET_PROPERTY`): each opcode sets one platform environment field, so
any number of fields ride one batch and new fields are new ids — never new
commands. The platform (renderer / DOM client / SSR host) delivers the
application's launch context this way; an HTTP request supplies what it offers,
and the WebSocket connection **enriches** the environment after connect
(viewport, native deep-links, and future fields all arrive the same way).

| Field | Value | `B` encoding | Notes |
|-------|-------|--------------|-------|
| `VIEWPORT_WIDTH` | `0x0001` | f32 | Logical points |
| `VIEWPORT_HEIGHT` | `0x0002` | f32 | Logical points |
| `ROUTE` | `0x0003` | string offset | The initial route/deep-link path. String convention is the `TEXT_CHANGED`/`NAVIGATE` dual: absolute offset into the host → guest **event arena** over the shared ring, *relative* offset into the batch's string section over the network. |

**Delivery** (spec DSL.md §4.5): on SSR the host synthesizes the environment
from the HTTP request (`ROUTE` = request path — all the request offers); over
the WebSocket the DOM client sends it (viewport + `ROUTE`) as its first message
and re-sends it to **enrich** after connect (a window-resize re-emits the
viewport fields). The application applies the environment uniformly — the
router hydrates from the `ROUTE` field before mount, so a deep-link renders
correctly on the first frame.

---

## Reactive Emission

The engine emits **only what changed** relative to its previous emission:

- **Property diff**: a changed `spacing`/`padding`/`WIDTH`/`HEIGHT`/`ALIGNMENT`/
  text emits a single `SET_PROPERTY` / `SET_TEXT` for that node. (Padding is a
  styling modifier, but it still diffs and emits like any other property.)
- **Structural diff**: a node added/removed/moved emits the corresponding
  `TREE` opcode(s).
- **Steady state**: an unchanged tree emits **zero** opcodes.

The **`frame` compound modifier** (width/height/alignment) is not a distinct
protocol concept: it emits `WIDTH`/`HEIGHT`/`ALIGNMENT` as ordinary
`STYLE:SET_PROPERTY` deltas, exactly like any other constraint property. There
is no `frame` opcode or dedicated wire type.

Signals in the application drive this: a signal marks the tree dirty; the
engine's next `emit` reconciles and writes only the delta. The renderer applies
each frame as a delta to its native elements.

---

## Linear Memory Layout

The guest engine and host share a **single linear memory**. Regions are fixed at module start:

```
+───────────────────────────────────────────────────────────────+
| 0x0000  Header block (80 B)  — cursors, frame counter, layout  |
| 0x0050  Ring buffer (16 B × slotCount)   [guest → host]        |
| ...      (slots densely packed, 64-byte aligned base)          |
| 0x10050 Event ring buffer (16 B × slotCount) [host → guest]    |
| ...                                                           |
| 0x20050 Arena (bump region, guest-owned: strings / tokens)     |
| ...                                                           |
| 0x30050 Event arena (bump region, host-owned: event strings)   |
| ...                                                            |
+───────────────────────────────────────────────────────────────+
```

> The event ring is the **host → guest** direction of the shared-memory
> transport: the renderer writes `EVENT`-category opcodes here and the
> guest/app drains them. It uses the same 16-byte slot layout as the main
> (guest → host) ring; both share `slotCount` (same capacity).
>
> The **event arena** is the host-owned bump region that makes strings flow
> **host → guest** over the shared ring: a renderer bump-allocates `TEXT_CHANGED`
> text (and any future string-bearing event payload) here and references it by
> its absolute byte offset — exactly mirroring how the guest uses the guest
> arena for `SET_TEXT` / STRING properties. The host resets its cursor on
> `META::RESET`; if the event arena is full the host applies backpressure
> (drops/stalls the event), mirroring ring backpressure.

### Header block (80 bytes)

| Offset | Size | Field | Producer | Description |
|--------|------|-------|----------|-------------|
| 0x00 | 4 | `magic` | — | `0x504C504C` (`PLPL`) |
| 0x04 | 2 | `version` | — | Wire protocol version (1) |
| 0x06 | 4 | `ringOffset` | — | Byte offset of the ring region |
| 0x0A | 4 | `ringBytes` | — | Byte length of the ring region |
| 0x0E | 4 | `slotCount` | — | Number of ring slots (power of two) |
| 0x12 | 4 | `arenaOffset` | — | Byte offset of the arena region |
| 0x16 | 4 | `arenaBytes` | — | Byte length of the arena region |
| 0x1A | 4 | `arenaCursor` | guest | Next free byte in the arena |
| 0x1E | 4 | `readCursor` | host | Ring slot the host has consumed up to |
| 0x22 | 4 | `writeCursor` | guest | Ring slot the guest has written up to |
| 0x26 | 4 | `frameCount` | guest | Monotonic completed-frame counter |
| 0x2A | 4 | `eventRingOffset` | — | Byte offset of the host→guest event ring region |
| 0x2E | 4 | `eventRingBytes` | — | Byte length of the event ring region |
| 0x32 | 4 | `eventSlotCount` | — | Event ring slot count (power of two; = `slotCount`) |
| 0x36 | 4 | `eventReadCursor` | guest | Event ring slot the guest has consumed up to |
| 0x3A | 4 | `eventWriteCursor` | host | Event ring slot the host has written up to |
| 0x40 | 4 | `eventArenaOffset` | — | Byte offset of the host→guest event arena region |
| 0x44 | 4 | `eventArenaBytes` | — | Byte length of the event arena region |
| 0x48 | 4 | `eventArenaCursor` | host | Next free byte in the event arena |

### Ring buffer

- Contiguous array of **16-byte slots**; `slotCount` is a **power of two** (wrap via mask, no modulo).
- **Producer** (guest) writes at `writeCursor`, then advances `writeCursor` and increments `frameCount` at frame end.
- **Consumer** (host) reads from `readCursor` up to `writeCursor`; after draining a frame it advances `readCursor`.
- **Backpressure**: the producer must not advance `writeCursor` past `readCursor` (ring full). The producer SHOULD flush a frame before the ring is full.
- **Frame boundary detection**: the host watches `frameCount`. When it advances, exactly one frame (all slots from the previously consumed `readCursor` to the current `writeCursor`) is complete.

### Event ring (host → guest)

- The **host** (renderer) is the producer: it writes `EVENT`-category opcodes at `eventWriteCursor`.
- The **guest** (app/engine) is the consumer: it reads from `eventReadCursor` up to `eventWriteCursor`, then advances `eventReadCursor`.
- Events are raw inputs and are consumed as soon as they are written (no frame batching); `eventFrameCount` is not required.
- Backpressure mirrors the main ring: the host must not advance `eventWriteCursor` past `eventReadCursor`.

### Arena

- A pre-allocated **bump region** for variable-length data (strings, token paths).
- Every arena entry is **self-describing**: `[u32 byteLength][bytes...]`.
- Opcodes reference entries by their **byte offset** (`arenaRef`), so an entry's length is read directly from the arena — all opcodes stay 16 bytes.
- The guest advances `arenaCursor` monotonically (no free-list, no fragmentation). The arena MAY be reset via `META::RESET`.

### Event arena (host → guest)

The event arena is the **host-owned mirror** of the arena, giving the shared-memory
transport a host → guest string section so strings flow **both ways on all
transports**:

- The **host** (renderer) bump-allocates event string payloads here (e.g. the
  new text of a `TEXT_CHANGED`) and references them by their **absolute byte
  offset** in the event opcode — the same `SET_TEXT` shared-memory convention
  used in the guest arena.
- The **guest** resolves the text from the event arena region when draining
  events (`EVENT::TEXT_CHANGED`).
- Entries share the same self-describing `[u32 byteLength][bytes...]` layout as
  the guest arena.
- The host advances `eventArenaCursor` monotonically and resets it on
  `META::RESET`; a full event arena is backpressure (the host drops/stalls the
  event until the guest drains or a reset lands).
- Over the **network**, host → guest string payloads use the batch's string
  section with *relative* offsets instead (see [Transport](#transport)) — the
  same dual absolute/relative convention as `SET_TEXT`.

---

## Colors

Literal colors are **sRGB** (IEC 61966-2-1, D65 white point), packed as `0xAARRGGBB`:

- `AA`: alpha (0x00 transparent – 0xFF opaque)
- `RR`: red, `GG`: green, `BB`: blue

Stored little-endian in a `u32` payload. A color **property** may alternatively carry a `DESIGN_TOKEN` reference to a token path instead of a literal (see [Design Token System](#design-token-system)); semantic token resolution and theme adaptation remain **renderer-owned**.

---

## Design Token System

### Core Principle

> **The application owns the theme. The renderer owns the defaults, the scheme
> detection, and the interaction states.**

- The **application** declares a theme: token overrides (base = **light**
  design, `dark.`-prefixed = **dark** design) and token references in style
  properties.
- The **renderer** supplies platform-appropriate defaults, derives the
  effective color scheme from the platform, resolves token references, and
  owns hover/pressed/focus/disabled styling.

This ensures a native look-and-feel by default and cross-platform consistency
when the application rolls a theme.

### Tokens & color schemes

- Tokens are identified by **dot-separated string paths** (`color.primary`,
  `font.body.size`, `space.2`), case-sensitive, lowercase recommended. The
  `DESIGN_TOKEN` value type (`0x08`) references a token path stored in the
  arena; `SET_DESIGN_TOKEN` overrides a token's value globally.
- **Base = light. `dark.`-prefixed paths are the dark variant** (`dark.color.primary`).
  The renderer derives the effective scheme (light/dark) from the platform and
  re-resolves when it changes — scheme selection is never carried by the
  protocol.
- The **standard token catalog** (colors, typography, spacing, radii,
  elevations, `control.*`, per-component tokens) and the **resolution
  algorithm** (dark layer → override → default → parent → fallback) are defined
  in **[TOKENS.md](./TOKENS.md)**.

### References in properties

Any property whose value type is `COLOR` / `F32` / `STRING` / `ENUM` MAY instead
carry the **`DESIGN_TOKEN`** value type (`0x08`), with `C` = arenaRef to the
token path. The renderer resolves the reference against the token tables and the
current scheme. A property carries exactly one value — a literal or a token
reference, never both. Because overrides and the scheme are renderer state, a
token-typed property requires **no re-emission** when a token changes.

### Renderer Responsibilities

The renderer MUST:

1. Provide platform-appropriate **light and dark defaults** for all Tier 1 tokens.
2. Accept `SET_DESIGN_TOKEN` commands and store overrides.
3. Derive the effective color scheme from the platform and re-resolve on change.
4. Resolve token references (`DESIGN_TOKEN` value type) in properties.
5. Fall back to defaults when no override exists.
6. Never transmit visual styling rules in the protocol.

The application MAY override any token via `SET_DESIGN_TOKEN` (base and `dark.*`)
and reference tokens via the `DESIGN_TOKEN` value type; it MUST NOT assume
specific default token values. Tokens a renderer cannot apply are ignored (the
same "allowed and ignored" rule as unapplicable modifiers).

---

## Frame Lifecycle

A frame is a batch of delta opcodes produced by one `emit` pass:

1. Guest begins a frame.
2. Guest emits the delta opcodes (tree structure and/or property changes) into consecutive ring slots.
3. Guest allocates strings in the arena as needed.
4. Guest `end_frame()`: publishes `writeCursor`, then increments `frameCount`.
5. Host observes `frameCount` change, drains `[readCursor, writeCursor)`, advances `readCursor`.

Because the ring and arena are plain memory, the guest publishes via a single release store of `frameCount`; the host acquires on read.

---

## Transport

The opcode protocol is transport-agnostic. Two peer backends carry frames from
producer (guest engine) to consumer (host renderer):

| Backend | Scenario | Mechanism |
|---------|----------|-----------|
| Shared memory | Desktop / native (guest + host share linear memory) | The ring buffer + arena above; zero-copy |
| Network batch | SSR → browser (guest and host do **not** share memory) | Serialized batches of opcodes + arena delta |

The `Frame` type is the **transport seam**: a consumer (renderer) applies a
frame the same way whether it came from the shared-memory ring or a decoded
network batch.

### Network batch format

```
[u32 magic "PLPL"] [u16 version] [u16 flags] [u32 frameCount] [u32 opcodeCount]
[opcode × opcodeCount (16 B each)]
[u32 arenaDeltaLen] [arena delta bytes]
```

- **magic / version**: `0x504C504C` (`PLPL`, little-endian on the wire) / `1`.
  A decoder MUST reject batches with a mismatched magic or version.
- **flags**: direction. `0x0000` = guest → host (tree/style). Bit `0x0001` =
  host → guest (raw-input events); host → guest batches carry their string
  payloads in the batch's string section.
- **frameCount**: the guest frame counter of the batch's first frame (opcodes
  from multiple frames may coalesce into one batch; they remain in order).
- **arena delta**: the bytes appended to the bump arena since the previous
  batch. The consumer keeps a **mirrored arena** and appends each delta, so
  absolute `arenaRef` offsets stay valid — identical to shared memory.
- **META::RESET**: a batch containing a reset opcode clears the consumer's
  mirrored arena before appending (offsets restart at 0).

### Batching policy

The producer buffers opcodes and flushes a batch when **either** threshold is
reached (first trigger wins):

- **Time**: the interval since the first buffered opcode elapses (default **4 ms**).
- **Size**: the accumulated batch reaches a network-frame size (default **1400 bytes**).

Both thresholds are configurable; a producer MAY flush eagerly (e.g. on the
first frame after connection).

---

## Conformance

Golden byte vectors for this protocol are in [CONFORMANCE.md](./CONFORMANCE.md).

### Companion specifications

The protocol's semantic surface is catalogued in four companion documents:
[PRIMITIVES.md](./PRIMITIVES.md) (the primitive views), [MODIFIERS.md](./MODIFIERS.md)
(the core modifiers, i.e. the `STYLE` properties), [EVENTS.md](./EVENTS.md)
(the core events, i.e. the raw inputs), and [TOKENS.md](./TOKENS.md) (the design
tokens / theming contract). IDs are allocated there spec-first;
this file remains the wire-format authority and must be updated when new IDs
land.

> **Implementation status** is tracked per implementing project (a `status.md`
> in each protocol crate/library), **not** in this specification. This document
> defines the protocol contract only.
