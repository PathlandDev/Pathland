# Pathland Primitive Views

**Wire protocol version:** 1
**Status:** Draft
**Last Updated:** October 1, 2026

---

## Introduction

This document is the **semantic catalog of primitive nodes** the Pathland
protocol supports, organized by the three structural categories that drive
rendering. The **server** (the application/engine) dictates *what* the UI is —
structure, content, and intent. The **client platform renderer** measures
layout geometry, handles font/image assets, and maps each node onto that
platform's native elements, delegating interaction to native OS controls
wherever possible.

Every primitive maps to a protocol component type (`TREE::CREATE_NODE`), the
modifiers that shape it ([MODIFIERS.md](./MODIFIERS.md)), and the core events it
can produce ([EVENTS.md](./EVENTS.md)). This file is **implementation-ready**: a
renderer (GTK, HTML/SSR, or a new backend) can be written from
these tables alone. The wire encoding rules live in
[OPCODE.md](./OPCODE.md); this file does not repeat them.

### Structural categories

The protocol splits all supported nodes into three structural categories (the
component ID ranges are definitive — see [Definitive Opcode
Mapping Range](#definitive-opcode-mapping-range)):

| Range | Category | Behavior |
|-------|----------|----------|
| `0x01`–`0x0F` | **Primitive Drawing Nodes** | Low-level, non-decomposable building blocks (`Text`, `Image`, `Color`, `Shape`, `Divider`, `Spacer`, `ProgressView`, `Gauge`). The server dictates exact visual content and structural properties. The client measures layout geometry, handles font/image assets, and paints native elements. They hold **no independent interaction state machines**. |
| `0x10`–`0x1F` | **Layout & Container Primitives** | Structural arrangement (`VStack`, `HStack`, `ZStack`, `Grid`, `ScrollView`, lazy grids/stacks). They arrange children and own scroll/virtualization; they carry no interaction semantics beyond scrolling. |
| `0x20`–`0x2F` | **Semantic Control Nodes** | High-order interaction and input controls (`Button`, `TextField`, `Toggle`, `Slider`, `Stepper`, `Picker`, `DatePicker`, `ColorPicker`, `Menu`). They encapsulate intent, two-way bindings (`BINDING_ID`), accessibility traits, and local interaction physics (e.g. 120 FPS client-side thumb dragging). |

### Relationship to other specs

| File | Role |
|------|------|
| [OPCODE.md](./OPCODE.md) | Wire format: categories, commands, 16-byte opcode layout, ring/arena, value types |
| **PRIMITIVES.md** (this file) | Which **views/nodes** exist and their protocol IDs |
| [MODIFIERS.md](./MODIFIERS.md) | Which **modifiers** (protocol properties) exist and how they encode |
| [EVENTS.md](./EVENTS.md) | Which **core events** (raw inputs) exist and how they encode |
| [CONFORMANCE.md](./CONFORMANCE.md) | Golden byte vectors |

> **Implementation status** is tracked per implementing project (a `status.md`
> in each protocol crate/library — `pathland-core`, `pathland-render-gtk`,
> `pathland-render-html`, the Java libraries), **not** in
> this specification. This document defines the protocol contract only.

> **Enum-valued properties** (marked `ENUM` in the tables below) are carried on
> the wire with the `F32` value type, holding the numeric enum code as an f32
> bit pattern (see [OPCODE.md](./OPCODE.md#value-types)). The codes are listed
> in [MODIFIERS.md](./MODIFIERS.md#appendix-enumerated-values).

### Non-negotiables (recap)

- Views are **declarative structure + constraint properties**, never positions.
  The engine emits `WHAT` (VStack, HStack, Text, …), never `WHERE`.
- Renderers are **stateless**: they map the opcode stream onto native elements
  and lay those elements out with their native layout engine. A renderer MAY
  retain its rendered-output tree for drawing, hit-testing, and event routing —
  this is a cache of its own output, not application state.
- **Native elements everywhere**: each primitive maps to the platform's native
  element (a stack → the platform's native stack; text → the platform's native
  text element); semantic controls → native OS controls in Native Token Mode
  (below). Never a generic canvas unless a platform has no native equivalent.

---

## Architectural Rules

### 1. Core Structural Classification

Each node belongs to exactly one of the three categories above. The category
determines renderer responsibilities:

- **Primitive Drawing Nodes** hold no interaction state. The server sends the
  full visual description; the renderer measures and paints. If a primitive has
  no native equivalent (e.g. `Path`), the renderer may paint it directly — this
  is the one case where canvas-style painting is permitted.
- **Semantic Control Nodes** carry intent + two-way bindings (`BINDING_ID`),
  accessibility traits, and local interaction physics. Their *visual chrome* is
  delegated per the Dual-Mode rules below; their *interaction semantics* are
  always renderer-owned and reported as raw events.

### 2. Dual-Mode Rendering Pipeline & Platform Delegation

Every semantic control node renders in **one of two modes**, decided by whether
the node has a child layout tree:

#### Native Token Mode (Leaf Control)

When a semantic control node has **no child layout tree** (`childrenIds` is
empty), the client platform **MUST delegate rendering entirely to its native OS
control** — `Button`, `TextField`/`SecureField`, `Toggle`, `Slider`, `Stepper`,
`DatePicker`, `ColorPicker`, `Picker`, and `Menu` each map to the platform's
native control (the concrete widget per renderer lives in that renderer's
`status.md`).

**System style modifiers act as visual style tokens** — they force the platform
to switch native control variants **without emitting child nodes over the
wire**. Example: `TOGGLE_STYLE = CHECKBOX` renders `Toggle` (0x24) as a native
checkbox; `TOGGLE_STYLE = SWITCH` (default) as a native switch; `TOGGLE_STYLE =
BUTTON` as a toggle button.

#### Composite Override Mode (Container Control)

When a semantic control **contains child nodes** (or a custom style body is
defined on the server), the client platform **MUST**:

1. **Suppress** its default native control visuals (the OS control chrome).
2. **Render the custom view tree** sent by the server (the composite body).
3. **Wrap that layout tree** with the native touch/click gestures, focus
   states, and event handlers that give the control its semantics.

The control keeps its interaction semantics (events, two-way bindings,
accessibility) even though its chrome is custom. Example: a `Button` with an
`HStack` child (icon + text) renders the `HStack` and wraps it with a native
click/press gesture that emits the button's events.

#### Leaf Property Fallbacks

If a control (e.g. `Button` 0x20) arrives with an **empty `childrenIds` list**,
renderers **MUST** check for direct string properties (the node's text content
set via `SET_TEXT`, or the `LABEL` 0x200A property for controls that define one)
before falling back to rendering an empty platform control shell. An empty shell
is a last resort, never the primary path.

### 3. Transport-Aware Event Guards

Renderers **MUST** implement event guards to prevent network noise. User
interactions — taps, text changes, drags, value edits — **MUST NEVER** emit
outbound event opcodes unless the target node explicitly declared intent:

- it set the relevant `EVENT_LISTENERS` (0x2005) bits, **or**
- it is a value-bearing control reporting by component type (see
  [EVENTS.md](./EVENTS.md)), **or**
- it carries a bound callback property: `ACTION_ID` (0x2016) or `BINDING_ID`
  (0x2017).

An interaction that matches none of these is dropped at the renderer — it is
never serialized to the event ring or a network batch.

---

## Definitive Opcode Mapping Range

Component types are `u16` in `TREE::CREATE_NODE`. The ranges below are aligned
with SwiftUI's view categories and are the **single authoritative allocation**.

### A. Primitive Drawing & Visual Nodes (`0x01`–`0x0F`)

| ID | Component | Notes |
| ---- | ----------- | ------- |
| `0x01` | `TEXT` | Standard styled text display |
| `0x02` | `IMAGE` | Raster, vector, or system icon asset (SF Symbols, material icons) |
| `0x03` | `COLOR` | Solid color / layout-filling background view (new node; formerly a property value only) |
| `0x04` | `SHAPE` | Vector geometries via `SHAPE_KIND` (`Rectangle`, `Circle`, `Capsule`, `Path`, …) |
| `0x05` | `DIVIDER` | Axis-aligned separator line |
| `0x06` | `SPACER` | Flexible expanding layout filler |
| `0x07` | `PROGRESS_VIEW` | Activity indicator / determinate progress |
| `0x08` | `GAUGE` | Range meter against a scale |
| `0x09` | `AUDIO` | Audio playback node (`AUDIO_SOURCE`) |
| `0x0A` | `VIDEO` | Video playback node (`VIDEO_SOURCE`) |
| `0x0B` | `ICON` | Renderer-native symbol for a canonical name (`ICON_NAME`, spec/ICONS.md) |
| `0x0C`–`0x0F` | — | Future drawing/visual nodes |

### B. Layout & Container Primitives (`0x10`–`0x1F`)

| ID | Component | Notes |
| ---- | ----------- | ------- |
| `0x10` | `VSTACK` | Vertical flex stack layout |
| `0x11` | `HSTACK` | Horizontal flex stack layout |
| `0x12` | `ZSTACK` | Depth-overlapping layer stack layout |
| `0x13` | `GRID` | Static 2D matrix grid (eagerly rendered, aligned rows & columns) |
| `0x14` | `SCROLLVIEW` | Scrollable content container |
| `0x15` | `LAZY_VGRID` | Virtualized vertical grid container (windowed rendering for large datasets) |
| `0x16` | `LAZY_HGRID` | Virtualized horizontal grid container |
| `0x17`–`0x1A` | — | Formerly `List`/`NavigationStack`/`NavigationSplitView` (composites, removed) — never reused |
| `0x1B` | `LAZY_VSTACK` | Virtualized vertical stack |
| `0x1C` | `LAZY_HSTACK` | Virtualized horizontal stack |
| `0x1D` | `GRID_ROW` | Explicit row grouping for `GRID` (planned): a grid child whose children are one row's cells — the SwiftUI `GridRow` authoring surface |
| `0x1E`–`0x1F` | — | Future layout/container nodes |

### C. Semantic Control Nodes (`0x20`–`0x2F`)

| ID | Component | Notes |
| ---- | ----------- | ------- |
| `0x20` | `BUTTON` | Action trigger control |
| `0x21` | `TEXT_FIELD` / `SECURE_FIELD` | Single-line text input (`IS_SECURE` token for secure variant) |
| `0x22` | `TEXT_EDITOR` | Multi-line text editing area (new) |
| `0x23` | — | Future semantic control |
| `0x24` | `TOGGLE` | Boolean switch, checkbox, or button control (`TOGGLE_STYLE`) |
| `0x25` | `SLIDER` | Continuous or stepped numeric range control |
| `0x26` | `STEPPER` | Discrete increment/decrement control |
| `0x27` | `DATE_PICKER` | Date & time selection modal/popover control |
| `0x28` | `PICKER` | Selection control (segment, dropdown menu, or wheel) |
| `0x29` | `MENU` | Contextual action trigger + popover container |
| `0x2A` | `COLOR_PICKER` | Native system color picker control |
| `0x2B`–`0x2F` | — | Future semantic controls |

### Utility & custom

| ID | Component | Notes |
| ---- | ----------- | ------- |
| `0x7F` | `COMMENT` | Opaque/debug node; renderers ignore it (no native element) |
| `0x0100`–`0xFFFF` | — | Application/custom component types |

### Composite views (not primitives)

These SwiftUI views are **not** protocol primitives. Applications compose them
from the primitives in this file, so no component IDs are allocated:

| SwiftUI view | Composition |
|--------------|-------------|
| `List` | `ScrollView` + `VStack` of rows; row selection via `SELECTED` per row |
| `NavigationStack` | app-held navigation state emitting the current destination into a stable `Group` slot (a structural container, [DSL.md §3.4](./DSL.md#34-structural-reactivity-conditional-rendering)); the slot's child swaps on route change, producing `TREE` deltas |
| `NavigationSplitView` | `HStack` (sidebar + detail) |
| `Label` | `HStack` + `IMAGE` (or `ICON`) + `TEXT` |

---

## A. Primitive Drawing & Visual Nodes

### Text — `TEXT` 0x01

A run of styled text. The server dictates content and style; the client measures
and renders.

- **Protocol**: a leaf node; content via `PARAMETER::SET_TEXT` or a bound signal.
- **Properties**: `LINE_LIMIT` (0x000B, U32), `TEXT_ALIGNMENT` (0x000C, enum
  code), `TRUNCATION_MODE` (0x000D, enum code), plus all text-formatting and
  appearance modifiers from [MODIFIERS.md](./MODIFIERS.md).
- **Content fitting**: an unconstrained Text is a single line at its natural
  width; a constrained width (Fixed `WIDTH`) wraps at word boundaries;
  `LINE_LIMIT` clamps the line count with a tail ellipsis; `TRUNCATION_MODE`
  positions that ellipsis under a clamp; `CLIPS_TO_BOUNDS` clips the Text to its
  bounds box. Full contract: [LAYOUT.md](./LAYOUT.md#content-fitting-truncation--clipping).
- **Events**: none by default; any listener via `EVENT_LISTENERS`.
- **Font handling**: client-owned — the renderer resolves `FONT_FAMILY` /
  `FONT_*` to its native text system.

### Image — `IMAGE` 0x02

A static image or icon asset. Asset loading is client-owned.

- **Protocol**: a leaf node; source via `IMAGE_SOURCE` (0x1002, STRING: a
  resource name, file path, or absolute URL).
- **Properties**: `IMAGE_SOURCE`, `CONTENT_MODE` (0x001C, enum `Fit`=0 /
  `Fill`=1), size modifiers, `OPACITY`, `CLIPS_TO_BOUNDS`.
- **Events**: none by default.
- **Note (SwiftUI `AsyncImage`)**: remote/async loading is **not a separate
  primitive** — it is `IMAGE` with `IMAGE_SOURCE` set to an absolute URL; the
  renderer loads asynchronously and re-issues `SET_PROPERTY(IMAGE_SOURCE)` if
  the source changes.

### Icon — `ICON` 0x0B

A **renderer-native symbol** for a canonical semantic name — unlike `IMAGE`,
which loads an asset, an icon is a *word* the renderer maps onto its native
icon set (spec/ICONS.md).

- **Protocol**: a leaf node; the symbol via `ICON_NAME` (0x1038, STRING: the
  canonical name). The application owns the intent; the renderer owns the glyph.
- **Properties**: `ICON_NAME`, `COLOR`/`foregroundStyle` (tint), `FONT_SIZE`
  (size — after SwiftUI's font-based symbol sizing), size modifiers,
  `OPACITY`, `LABEL` (accessibility).
- **Events**: none by default.
- **Fallback**: an unknown canonical name (a name the vocabulary has not
  allocated) resolves to a renderer-owned placeholder (spec/ICONS.md).

### Audio — `AUDIO` 0x09

An audio playback node. Playback is **renderer-native by default** (the
renderer emits native playback controls); the app supplies the source
reference. A **custom
`AudioStyle`** supplies app-driven control children (transport buttons, seek,
volume) — the node then carries the media control properties below and the
renderer renders a hidden media element alongside the custom controls.

- **Protocol**: a leaf node by default (no children); source via `AUDIO_SOURCE`
  (0x1033, STRING: a resource name, file path, or absolute URL — an asset
  reference, never embedded in the opcode stream). With a custom style the
  node's children are the custom control UI.
- **Properties**: `AUDIO_SOURCE`, the media control properties —
  `PLAYBACK_STATE` (0x1035, U32 0/1), `MEDIA_POSITION` (0x1036, F32 seconds;
  a change seeks), `MEDIA_VOLUME` (0x1037, F32 0..1) — and size modifiers.
- **Events**: none by default; when a control property is bound, the renderer
  reports `MEDIA_PLAY_STATE_CHANGED` / `MEDIA_TIME_UPDATED` / `MEDIA_ENDED` /
  `MEDIA_VOLUME_CHANGED` (see EVENTS.md).

### Video — `VIDEO` 0x0A

A video playback node. Playback is **renderer-native by default** (`controls`);
a custom `VideoStyle` supplies app-driven control children, mirroring `AUDIO`.

- **Protocol**: a leaf node by default; source via `VIDEO_SOURCE` (0x1034,
  STRING asset reference). Custom style → children = the control UI.
- **Properties**: `VIDEO_SOURCE`, `CONTENT_MODE`, the media control properties
  (`PLAYBACK_STATE`, `MEDIA_POSITION`, `MEDIA_VOLUME`), size modifiers.
- **Events**: none by default; the media events above when a control property
  is bound.
- **Note**: a poster/preview frame is a planned draft (`POSTER_SOURCE`); a
  video plays fine without one.

### Color — `COLOR` 0x03

`Color` mirrors SwiftUI's **dual identity**: it is both a **View** and a
**Data Type** — and it is **never a modifier**.

- **As a View**: a solid-color visual element. It is **layout-greedy** —
  it expands to fill all available space offered by its parent container
  unless explicitly constrained (e.g. by a `.frame`/size modifier). Its color
  is the `COLOR` property (0x100A, packed `0xAARRGGBB`, sRGB).
- **As a Data Type**: a `Color` value is passed *into* style-taking modifiers
  (`.foregroundStyle(_:)`, `.background(_:)`, `.border(_:)`, `.tint(_:)`), and
  can be used anywhere a visual fill/style is expected.
- **Constraint**: there is **no** `.color()` modifier, and `.foregroundColor(_:)`
  is deprecated — all foreground styling uses `.foregroundStyle(_:)`.
- **Parser/generator rule**: treat `Color` as a **View** when it is a structural
  node in the UI tree; treat it as a **Value** when it appears in a modifier's
  parameter list.
- **Events**: none by default.
- **Note**: `Color` is also a **property value** (`COLOR`,
  `BACKGROUND_COLOR`, `BORDER_COLOR`, `TINT`) — the node exists for when a
  color is a first-class view (backgrounds, fills, spacers).

### Shape — `SHAPE` 0x04

A vector geometry. The shape kind is data (`SHAPE_KIND`), never a visual rule —
the renderer owns the actual drawing.

- **Protocol**: a leaf node with `SHAPE_KIND` (0x0006, enum): `Circle`=0,
  `Rectangle`=1, `RoundedRectangle`=2, `Capsule`=3, `Ellipse`=4, `Path`=5.
- **Properties**: fill = `COLOR`/`BACKGROUND_COLOR`; stroke = `BORDER_WIDTH`,
  `BORDER_COLOR`, `BORDER_RADIUS` (RoundedRectangle corner), `BORDER_EDGES`;
  size = `WIDTH`/`HEIGHT`.
- **Events**: none by default.

### Divider — `DIVIDER` 0x05

An axis-aligned 1px separator line.

- **Protocol**: a leaf node; orientation implied by the parent stack axis.
- **Properties**: `COLOR` (0x100A), `BORDER_WIDTH` (0x1003) — line thickness.
- **Events**: none.
- **Layout-greedy on the cross axis** (fills the stack's available cross size,
  SwiftUI-style) — see LAYOUT.md; an explicit `WIDTH`/`HEIGHT` overrides it.

### Spacer — `SPACER` 0x06

A flexible expanding layout filler.

- **Protocol**: a leaf node with no properties; expands to the remaining main
  axis space.
- **Events**: none.

### ProgressView — `PROGRESS_VIEW` 0x07

Determinate progress or an activity indicator.

- **Protocol**: a leaf node. Determinate: `PROGRESS` (0x200E, F32 0.0–1.0, or
  0.0–`MAX_VALUE` if set). Indeterminate: `IS_INDETERMINATE` (0x200F, U8 1) —
  the renderer animates a native activity indicator.
- **Events**: none.

### Gauge — `GAUGE` 0x08

A value shown against a scale (SwiftUI `Gauge`).

- **Protocol**: a leaf node with `VALUE` (0x2006), `MIN_VALUE` (0x2007),
  `MAX_VALUE` (0x2008). The gauge style (linear/circular) is renderer/token-owned.
- **Events**: none (read-only; an interactive gauge is a `Slider` 0x25).

---

## B. Layout & Container Primitives

Layout primitives arrange children. They carry `SPACING` (0x0001),
`ALIGNMENT` (0x0002), and `CONTENT_MARGINS` (0x0005), plus layout modifiers from
[MODIFIERS.md](./MODIFIERS.md#1-layout). Renderers map them to their native
layout primitives.

### The stack layout model

`VStack`/`HStack` (and the lazy stacks) follow the **SwiftUI / Jetpack Compose
layout model**: a stack *proposes* a size to each child, the child reports its
own size, and the stack *places* it. The protocol transmits **intent** — size
kinds and alignment — never geometry; the renderer resolves the proposal with
its native layout engine. The full allocation contract is
[LAYOUT.md](./LAYOUT.md); the essentials:

**Size kinds.** Each `WIDTH`/`HEIGHT` is one of three kinds (LAYOUT.md §size
model):

| Kind | Wire value | Meaning |
|------|-----------|---------|
| **Fixed** | finite `F32` | exactly that many points |
| **Fill** | `-1` | expand to available space |
| **Hug** | `-2` / absent | natural size |

**Main vs cross axis.** The **main axis** is the stack's layout direction
(VStack = vertical, HStack = horizontal); the other is the **cross axis**.

**Main-axis allocation.** A child is Fixed (exact box), Fill (expands), or Hug
(natural). **Leftover main-axis space goes only to `Fill` children** (and to
`Spacer`, which is Fill by nature); a stack with no Fill child leaves the
leftover empty at the end. Every other child is packed from the start.

**Cross-axis allocation.** A child keeps its own size on the cross axis and is
**positioned** by `ALIGNMENT` — position-only, it never resizes. A stack reads a
**single axis** of the 2D position code (see the [ZStack alignment
table](#zstack--zstack-0x12)): a `VStack` reads the horizontal component
(`0/1/2` = leading/center/trailing), an `HStack` the vertical (`0/1/2` =
top/center/bottom). The default is the start position (Compose `Column` defaults
`Alignment.Start`, `Row` defaults `Alignment.Top`; SwiftUI's stack default is
`.center` — a per-framework difference, all three are expressible with an
explicit `ALIGNMENT`). Only a **`Fill`-sized child** stretches to the stack's
cross size.

**Fill propagation.** A Hug-sized stack that contains a **`Fill`-sized child or
`Spacer` on an axis is itself `Fill`-sized on that axis** — the child's
expansion propagates to the stack, which then fills its parent's proposal on
that axis. This matches both reference frameworks:

- SwiftUI `VStack { Button().frame(maxWidth: .infinity) }` fills the width.
- Compose `Column { Box(Modifier.fillMaxWidth()) }` fills the width.
- `VStack { Spacer() }` fills the proposed height.

A full-width row is therefore expressed as a `Fill`-width child (or `Spacer`)
inside the stack, never by stretching the stack itself.

**Spacing.** `SPACING` is a fixed gap between **adjacent** children (never
before the first or after the last), clamped to ≥ 0. It matches SwiftUI
`VStack(spacing:)` and Compose `Arrangement.spacedBy(...)`. Leftover space is
distributed by `Fill` children / `Spacer`, never by the gap.

**Insets.** `CONTENT_MARGINS` is the stack's uniform content inset
(equivalently, `PADDING` on the stack). Both compose; when both are present on
the same node, precedence is per-edge `PADDING_*` > `PADDING` >
`CONTENT_MARGINS`.

**Overflow.** A Fixed box **constrains layout but does not clip**: content that
exceeds the box is drawn outside it unless `CLIPS_TO_BOUNDS` clips it (SwiftUI
`.clipped()` parity). A stack inside a `SCROLLVIEW` scrolls instead.

### VStack — `VSTACK` 0x10

Vertical flex stack. **Properties**: `SPACING`, `ALIGNMENT` (enum: `Leading`=0,
`Center`=1, `Trailing`=2, `Fill`=3), `CONTENT_MARGINS`.

**Layout & sizing**

- **Main axis (vertical)**: children are allocated per the stack model —
  Fixed exact, Fill expands (leftover only to `Fill`/`Spacer`), Hug natural. A
  Hug VStack's height is the sum of its children's heights plus `SPACING`
  between them.
- **Cross axis (horizontal)**: children keep their own width (a Fixed box, or a
  Hug natural width); `Fill`-width children stretch to the stack's width. Narrow
  children are positioned per `ALIGNMENT` (default Leading/Start).
- **Own size**: Hug by default (height = content + spacing; width = widest
  child). A Fixed `WIDTH`/`HEIGHT` makes the box exact; a `Fill` axis — or fill
  propagation — expands to the parent's proposal. A `Fill`-width child makes
  the stack full-width; a `Fill`-height child makes it full-height.
- **Edge cases**: negative `SPACING` → 0; no Fill child + leftover → empty at
  the end; a Fixed-width child narrower than the stack keeps its exact box and
  is aligned; a `Fill` child in a Hug stack propagates (fills the parent's
  proposal); root stacks are typically `Fill` (`FrameMod.of(FILL, FILL)`).

### HStack — `HSTACK` 0x11

Horizontal flex stack. Same properties as `VStack`, mirrored:

- **Main axis (horizontal)**: children per the stack model — Fixed exact, Fill
  expands (leftover only to `Fill`/`Spacer`), Hug natural. A Hug HStack's width
  is the sum of its children's widths plus `SPACING`.
- **Cross axis (vertical)**: children keep their own height (Fixed or Hug);
  `Fill`-height children stretch. Positioned per `ALIGNMENT` (default
  Leading/Start; Compose `Row` defaults to `Top`, SwiftUI's HStack to `.center`).
- **Own size**: Hug by default (width = content + spacing; height = tallest
  child); a Fixed box exact; `Fill`/propagation expands.
- **Edge cases**: as VStack.

### ZStack — `ZSTACK` 0x12

Depth-overlapping layer stack: children are drawn in depth order — **child index =
draw order**, later = on top. An explicit `Z_INDEX` overrides that order: a
higher value draws **on top of** lower values, and equal values tie-break by
child index (SwiftUI `.zIndex(_:)` parity). **Properties**: `ALIGNMENT` (enum:
`Leading`=0, `Center`=1, `Trailing`=2, `Fill`=3), plus layout modifiers (Fixed/Fill
`WIDTH`/`HEIGHT`, `PADDING`, `CONTENT_MARGINS` as an inset). **No `SPACING`** —
children occupy the same box by design; there is no gap between them.

**Layout & sizing** (SwiftUI `ZStack` / Compose `Box` semantics)

- **Overlay allocation**: unlike a flex stack, children do **not** add to each
  other along an axis — they share one box. Each child keeps its own size (a
  Fixed box, or a Hug natural size) and is **positioned** by `ALIGNMENT` on
  **both axes**; only a **`Fill`-sized child** stretches to the stack's size.
- **Own size**: the stack **hugs to its largest child** by default — on each
  axis its size is the **maximum** of its children's sizes, never the sum. A
  Fixed `WIDTH`/`HEIGHT` makes the box exact; a `Fill` axis — or fill
  propagation (a `Fill` child on an axis makes the Hug stack `Fill` on that
  axis, see the stack layout model above) — expands to the parent's proposal.
  `ZStack { Color.red }` fills (Color is layout-greedy); `ZStack { Text("hi") }`
  hugs the text.
- **Alignment**: `ALIGNMENT` positions each child on **both** axes with a
  **2D position code** (SwiftUI `Alignment` / Compose `Alignment` parity) — the
  code's horizontal and vertical components place the child within the stack's
  box. Position-only: it never resizes a child.

  | code | horizontal | vertical | meaning |
  |------|------------|----------|---------|
  | `0` | start | start | top-leading |
  | `1` | center | center | centered |
  | `2` | end | end | bottom-trailing |
  | `3` | center | start | top-center |
  | `4` | center | end | bottom-center |
  | `5` | start | center | center-leading |
  | `6` | end | center | center-trailing |
  | `7` | end | start | top-trailing |
  | `8` | start | end | bottom-leading |

  `0`/absent is the default (top-leading, Compose `TopStart` parity; SwiftUI
  `ZStack` defaults to `.center`). A **stack** (`VStack`/`HStack`) reads a
  **single axis** of the same code — the `VStack` cross-axis is horizontal
  (`0/1/2` = leading/center/trailing), the `HStack` cross-axis vertical
  (`0/1/2` = top/center/bottom).
- **Overflow**: a Fixed box constrains layout but does not clip — content that
  exceeds it is drawn outside the box unless `CLIPS_TO_BOUNDS` clips it
  (SwiftUI `.clipped()` parity).
- **Edge cases**: the first child is **not** a special "background" — it follows
  the same size model (a Fixed first child keeps its exact box; a `Fill` one
  stretches); draw order is child index, with an explicit `Z_INDEX` overriding it
  (higher = on top, equal → child index); a `Fill` child in a Hug ZStack
  propagates (fills the parent's proposal).

### Grid — `GRID` 0x13

Static 2D matrix grid, eagerly rendered with aligned rows & columns. Children
are cells, **row-major in insertion order** — child *i* occupies the cell at
`(row = i / columns, column = i % columns)`. **Properties**: `ALIGNMENT` (a 2D
position code applied per-cell, like `ZStack` — the [ZStack alignment
table](#zstack--zstack-0x12)), `SPACING`
(uniform row + column gap), `GRID_COLUMNS` (column count), `GRID_ROWS` (row
count), plus layout modifiers (`WIDTH`/`HEIGHT` frames — the grid's box, never a
count — `PADDING`/`CONTENT_MARGINS` as an inset).

#### The grid layout model

`GRID` (and the lazy grids) follow the **SwiftUI / Jetpack Compose grid model**:
a grid *proposes* a size to each cell, the cell reports its own size, and the
grid *places* it at its track intersection. Unlike a stack, a grid has no main
axis — its two track axes (columns × rows) follow the size model
[above](#the-stack-layout-model) independently, with two differences:

**Cell counts.** `GRID_COLUMNS` (`0x001E`) is the **column count** and
`GRID_ROWS` (`0x001F`) the **row count**. A **Fixed** value pins that count;
**`FILL` or absent means auto-fit** — the grid derives the count from the number
of cells and the available space (Compose `GridCells.Fixed(N)` vs `Adaptive`;
SwiftUI `LazyVGrid(columns:)` / `LazyHGrid(rows:)`). They are **constructor
properties** (never chainable modifiers) and are **never pixel sizes**.

**Tracks.** On a **fixed-count** axis the tracks are **equal `1fr` fractions**
of the grid's size on that axis (`repeat(N, 1fr)` in CSS, Compose
`GridCells.Fixed`, SwiftUI `.flexible()` columns): every track gets the same
share. A Hug grid on that axis sizes the tracks to the content instead (each
track as large as its widest/tallest cell) and hugs to the content + `SPACING`.
The **auto** axis always sizes tracks to the content — each row/column is
exactly as large as its cells need.

**Per-track sizes (`GRID_TRACKS`).** For per-track control a grid may carry a
**`GRID_TRACKS`** (`0x0020`, STRING) constructor property — a comma-separated
track list, one entry per track on the fixed-count axis, **taking precedence**
over `GRID_COLUMNS`/`GRID_ROWS` (a bare count is the `N`×`flexible` sugar). Each
entry is:

| Token | Meaning | CSS | Compose | SwiftUI |
|-------|---------|-----|---------|---------|
| `flex` | equal `1fr` share (the count's track) | `1fr` | `GridCells.Fixed` column | `.flexible()` |
| `fixed:<points>` | exactly `<points>` wide | `<points>px` | fixed dp column | `.fixed(_:)` |
| `adaptive:<points>` | auto-fit, at least `<points>` (fit as many as fit) | `minmax(<points>px,1fr)` | `GridCells.Adaptive(minSize)` | `.adaptive(minimum:)` |

The track list is **CSS-grid-native** — renderers whose native grid has no
per-track sizing equivalent (GTK `GtkGrid`) size tracks naturally instead
(renderer status, not a protocol contract).

**Spacing.** `SPACING` is a **uniform gap between tracks on both axes** — the
row gap and the column gap are the same value (Compose `Arrangement.spacedBy`,
CSS `gap`), clamped to ≥ 0, never before the first or after the last track.

**Per-cell allocation.** Each cell keeps its own size on both axes (a Fixed box,
or Hug content) and is **positioned** within its track by the grid's `ALIGNMENT`
(a **2D position code**, like `ZStack` — the [table
above](#zstack--zstack-0x12)) — e.g. `0`/absent = top-leading, `3` = top-center.
Position-only: it never resizes a cell. Only a **`Fill`-sized cell** (or a
greedy filler like `COLOR`) stretches to fill its track.

**Own size & edge cases.** A grid follows the universal size model: **Hug**
(content tracks + `SPACING`), a Fixed `WIDTH`/`HEIGHT` **box**, or **`Fill`**
(expands to the parent's proposal). `GRID_COLUMNS`/`GRID_ROWS` never size the
grid — they only set the track counts. A grid **never propagates** a `Fill` cell
up to a Hug parent — a `Fill` cell fills its own track, and the grid's own size
comes from its own frame. Negative `SPACING` → 0; a Fixed box constrains layout
but does not clip (`CLIPS_TO_BOUNDS` clips).

### GridRow — `GRID_ROW` 0x1D

An **explicit row grouping** for a `GRID` (the SwiftUI `GridRow` authoring
surface): a grid's children are **cells or `GRID_ROW`s**, and a `GRID_ROW`'s
children are the cells of **one row**. It lets the author control row layout
directly instead of relying on row-major index flow.

- **Row placement.** A `GRID_ROW` always starts a new row: its children are
  placed left-to-right in that row starting at column 0, then the row advances.
  **Bare cells** (direct `GRID` children) auto-flow row-major, advancing to the
  next row after `GRID_COLUMNS` cells (or auto, below).
- **Column count.** The equal-`1fr` track count is `GRID_COLUMNS` when set;
  otherwise the **widest row** (a `GRID_ROW` or a bare-cell run) defines it. A
  short row leaves its trailing columns **empty** — a cell never pulls the next
  row's cells forward.
- **Structure.** A `GRID_ROW` is structural only: it renders nothing outside a
  `GRID` (like `COMMENT`), and renderers flatten its cells into the grid's rows.
  Its own `ALIGNMENT`/`SPACING`/size properties are ignored.
- **Example.** `Grid { GridRow { A; B }; GridRow { C; D }; E }` lays out row 0 =
  `A B`, row 1 = `C D`, row 2 = `E` (auto-flowing to the next row after the two
  columns defined by the widest row).

### ScrollView — `SCROLLVIEW` 0x14

A scrollable content container. **Layout-greedy on both axes** by default — it
fills the available space (SwiftUI `ScrollView` / Compose scroll semantics); an
explicit `WIDTH`/`HEIGHT` overrides it (LAYOUT.md §layout-greedy primitives).
**Properties**: layout modifiers (Fixed/Fill frames, `PADDING`/`CONTENT_MARGINS`
as an inset).

**Content.** The **first child** is the content; a scroll view carries a single
content subtree (typically a stack), matching SwiftUI/Compose's single-content
contract. The content is measured with an **unbounded proposal** on both axes —
it may grow beyond the viewport (that is what scrolls). The **viewport** is the
scroll view's allocated box; a Hug `WIDTH`/`HEIGHT` — or a Hug parent proposal
on an axis — sizes the viewport to the content's natural size on that axis (the
viewport fits the content, so that axis does not scroll).

**Overflow.** A scroll view scrolls its content rather than clipping it — the
viewport clips by virtue of scrolling; `CLIPS_TO_BOUNDS` on the scroll view has
no further effect on the content.

**Events.** With the `SCROLL` listener bit (bit 8) the renderer reports the
content offset in logical points via `EVENT::SCROLL`; with `WHEEL` (bit 9) it
reports wheel/trackpad deltas via `EVENT::WHEEL` (both draft — see
[EVENTS.md](./EVENTS.md)).

### LazyVGrid — `LAZY_VGRID` 0x15

Virtualized vertical grid. **Allocation is identical to `GRID`** — row-major
cells, `GRID_COLUMNS` = column count (Fixed pins it, `FILL`/absent = auto-fit),
equal `1fr` columns, uniform `SPACING` gap, per-cell alignment, hug-to-content
own size. The only difference is **realization**: a renderer MAY defer realizing
off-screen cells until scrolled into view and MAY discard realized cells that
leave the viewport. Realization never changes layout — a cell's position is
always its row-major index in the full cell list. Typically nested inside a
`SCROLLVIEW`.

### LazyHGrid — `LAZY_HGRID` 0x16

Virtualized horizontal grid; the flipped-axis form of `LAZY_VGRID`. **Allocation
is identical to `GRID` mirrored**: cells are **column-major** in insertion
order, `GRID_ROWS` = row count (Fixed pins it, `FILL`/absent = auto-fit), equal
`1fr` rows, columns auto-flow, uniform `SPACING` gap, per-cell alignment,
windowed realization.

### LazyVStack — `LAZY_VSTACK` 0x1B

Virtualized vertical stack. **Allocation is identical to `VStack`** (the stack
layout model above — same properties, sizing, spacing, alignment, and fill
propagation); only **realization** differs: a renderer MAY defer realizing
children outside the visible region.

### LazyHStack — `LAZY_HSTACK` 0x1C

Virtualized horizontal stack. **Allocation is identical to `HStack`**; only
realization is windowed.

---

## C. Semantic Control Nodes

Semantic controls encapsulate intent, two-way bindings, accessibility traits,
and local interaction physics. Rendering follows the [Dual-Mode Rendering
Pipeline](#2-dual-mode-rendering-pipeline--platform-delegation): **Native Token
Mode** when the node has no children; **Composite Override Mode** when it does.

### Button — `BUTTON` 0x20

An action trigger control.

| Property | ID | Type | Meaning |
|----------|----|------|---------|
| label text | `SET_TEXT` / `TEXT` | STRING | The button's text label (leaf mode fallback) |
| `ACTION_ID` | 0x2016 | U32 | Callback id that gates event delivery (Event Guards) |
| `BINDING_ID` | 0x2017 | U32 | Two-way binding id (bound to the action's value, if any) |
| `ENABLED` | 0x2003 | U8 | 1 = interactive, 0 = disabled |
| `STATE` | 0x2002 | F32 (enum) | Accessibility state (see OPCODE.md) |
| `EVENT_LISTENERS` | 0x2005 | U32 | Raw pointer listeners (down/move/up) for app-side tap composition |
| `COLOR`/`FONT_*`/`PADDING`/`BACKGROUND_COLOR`/… | — | — | Text & appearance modifiers (MODIFIERS.md) |

- **Native Token Mode (leaf):** no children → native button; the label is the
  node's text (`SET_TEXT`). If the text is also absent, render an empty
  platform button shell (leaf fallback). Button styles are renderer/token-owned.
- **Composite Override Mode (container):** children present → suppress default
  button chrome, render the custom label tree, wrap it with a native
  click/press gesture that reports `POINTER_DOWN`/`POINTER_UP` for the button's
  `ACTION_ID` callback.
- **Events:** the tap is the app-side composition of the raw pointer events
  (`POINTER_DOWN` then `POINTER_UP` on the same target). Without `ACTION_ID` /
  `BINDING_ID` / `EVENT_LISTENERS`, a renderer MUST drop the interaction (Event
  Guards).
- **Note (SwiftUI `Link`)**: SwiftUI's `Link` is `BUTTON` with an associated
  URL; the app handles the tap and opens the URL. No separate primitive.

### TextField / SecureField — `TEXT_FIELD` 0x21

Single-line text input; `SecureField` is the same component with `IS_SECURE`.

| Property | ID | Type | Meaning |
|----------|----|------|---------|
| `LABEL` | 0x200A | STRING | Caption label |
| `PROMPT` | 0x200B | STRING | Placeholder text |
| `IS_SECURE` | 0x200D | U8 | 1 = masked input (`SecureField`) |
| `BINDING_ID` | 0x2017 | U32 | Two-way binding id (text value) |
| `ENABLED`/`STATE` | 0x2003/0x2002 | — | Enabled / accessibility state |

- **Events** (see [EVENTS.md](./EVENTS.md)): `TEXT_CHANGED` (0x07) on every
  edit; `FOCUS_CHANGED` (0x08), `EDITING_CHANGED` (0x09), `SUBMIT` (0x0A) —
  draft. All gated by `BINDING_ID` or the matching `EVENT_LISTENERS` bits.
- **Security**: a secure field MUST mask characters and MUST NOT echo the value.

### TextEditor — `TEXT_EDITOR` 0x22

Multi-line text editing area.

- **Protocol**: a leaf node; content via `SET_TEXT`, value binding via
  `BINDING_ID` (0x2017).
- **Properties**: `LINE_LIMIT`, `TEXT_ALIGNMENT`, `TRUNCATION_MODE`,
  `IS_SECURE` (unused), text modifiers.
- **Events**: same as `TextField` — `TEXT_CHANGED` (0x07), `FOCUS_CHANGED`
  (0x08), `EDITING_CHANGED` (0x09), `SUBMIT` (0x0A) — gated by `BINDING_ID` /
  `EVENT_LISTENERS`.

### Toggle — `TOGGLE` 0x24

A boolean switch, checkbox, or button control. The **visual style is a token**,
not a separate component:

| Property | ID | Type | Meaning |
|----------|----|------|---------|
| `TOGGLE_STYLE` | 0x2018 | F32 (enum) | `Switch`=0 (default), `Checkbox`=1, `Button`=2 |
| `SELECTED` | 0x2004 | U8 | Checked state (0 off, 1 on) |
| `BINDING_ID` | 0x2017 | U32 | Two-way binding id (bool value) |
| `LABEL` | 0x200A | STRING | Optional caption |
| `ENABLED`/`STATE` | 0x2003/0x2002 | — | Enabled / accessibility state |

- **Native Token Mode (leaf):** renders the native control for the
  `TOGGLE_STYLE` token — a native switch, checkbox, or toggle button. The style
  token switches native variants **without emitting child nodes**.
- **Composite Override Mode (container):** a custom body (e.g. label + icon)
  wrapped with a native toggle gesture; `SELECTED` still drives the state.
- **Events:** a user change emits `VALUE_CHANGED` (0x06) with `B` = 0/1 — gated
  by `BINDING_ID`. The app writes it into `SELECTED`; the engine re-emits the
  `SELECTED` property.

### Slider — `SLIDER` 0x25

A continuous or stepped numeric range control.

| Property | ID | Type | Meaning |
|----------|----|------|---------|
| `VALUE` | 0x2006 | F32 | Current value |
| `MIN_VALUE` | 0x2007 | F32 | Inclusive minimum |
| `MAX_VALUE` | 0x2008 | F32 | Inclusive maximum |
| `STEP_VALUE` | 0x2009 | F32 | Stepped mode increment (default 1.0 if absent; absent = continuous) |
| `BINDING_ID` | 0x2017 | U32 | Two-way binding id (numeric value) |
| `ENABLED`/`STATE` | 0x2003/0x2002 | — | Enabled / accessibility state |

- **Native Token Mode (leaf):** native slider; the renderer owns 120 FPS
  client-side thumb dragging and resolves the semantic value from its own track
  geometry.
- **Composite Override Mode (container):** a custom track/thumb body wrapped
  with a native drag gesture that reports `VALUE_CHANGED`.
- **Events:** `VALUE_CHANGED` (0x06, `A=targetId, B=value (f32)`) — gated by
  `BINDING_ID`.

### Stepper — `STEPPER` 0x26

A discrete increment/decrement control. `VALUE` (0x2006), `STEP_VALUE`
(0x2009), `MIN_VALUE` (0x2007), `MAX_VALUE` (0x2008), `BINDING_ID` (0x2017).
Events: `VALUE_CHANGED` after each press.

### DatePicker — `DATE_PICKER` 0x27

A date & time selection modal/popover control.

- **Protocol**: a leaf node whose value is set with the **`PARAMETER::SET_DATE`**
  command (draft 0x04, see OPCODE.md): `A=nodeId, B=days since epoch (I32,
  pre-1970 negative), C=millis of day (U32, 0..86,400,000)`.
- **Properties**: `DATE_PICKER_MODE` (0x2013, enum: `Date`=0, `Time`=1,
  `DateAndTime`=2), `BINDING_ID` (0x2017), size modifiers.
- **Events**: `DATE_CHANGED` (draft 0x0D, `A=targetId, B=days (I32),
  C=millis of day (U32)`) — inline, gated by `BINDING_ID`.

### Picker — `PICKER` 0x28

A selection control rendered as a segment, dropdown menu, or wheel.

- **Protocol**: a container node whose **children are the options**, in display
  order (each a leaf whose text is the option label) — the same "options are
  children" rule as `Menu` action items.
- **Properties**: `SELECTION` (0x2010, U32 child index), `PICKER_STYLE`
  (0x2014, enum: `Menu`=0, `Segmented`=1, `Wheel`=2, `RadioGroup`=3),
  `BINDING_ID` (0x2017).
- **Events**: `VALUE_CHANGED` with `B` = the new selected child index — gated
  by `BINDING_ID`.

### Menu — `MENU` 0x29

A **semantic control**: contextual action trigger and popover container. The
client platform owns dynamic popover presentation, overlay placement, tap/focus
management, and accessibility focus trapping.

#### Dual-Mode Rendering Rules

- **Native Token Mode (Leaf Trigger):** with no custom trigger child node, the
  client MUST render its native system drop-down button shell using the
  designated trigger label property (the node's text via `SET_TEXT`, or
  `LABEL` 0x200A).
- **Composite Override Mode (Custom Trigger Container):** when a custom view
  tree (e.g. an `HStack` with an avatar image and text label) is designated as
  the menu trigger, the client MUST:
  1. suppress default OS drop-down button styling,
  2. render the custom trigger layout tree sent by the server,
  3. wrap that custom layout tree with native menu tap/click handlers, hover
     states, and popover anchor bindings.

#### Child Composition Rules

A `Menu` manages **two types of child view trees**:

1. **Trigger View** — the custom layout tree representing the clickable visual
   trigger element.
2. **Action Items** — child semantic nodes representing menu options (`Button`,
   `Toggle`, or nested sub-`Menu` nodes).

Renderers **MUST** map child `Button` and `Toggle` nodes inside a `Menu` directly
to **native OS menu item slots** rather than standard canvas/layout nodes.

- **Events**: `VALUE_CHANGED` with `B` = the chosen action item index — gated
  by `BINDING_ID` (0x2017).

### ColorPicker — `COLOR_PICKER` 0x2A

A native system color picker control.

- **Protocol**: a leaf node with `COLOR_VALUE` (0x2012, COLOR — packed
  `0xAARRGGBB`, sRGB) and `BINDING_ID` (0x2017).
- **Events**: `VALUE_CHANGED` — the value field carries the packed color **as an
  f32 bit pattern** of `0xAARRGGBB` (the app reinterprets it) — gated by
  `BINDING_ID`.

---

## Reserved component type IDs

| Range | Use |
|-------|-----|
| `0x01`–`0x08` | Primitive drawing nodes (allocated, this file) |
| `0x09`–`0x0F` | Future drawing nodes (unallocated) |
| `0x10`–`0x16`, `0x1B`–`0x1D` | Layout & container primitives (this file) |
| `0x17`–`0x1A` | Reserved (formerly composites) — never reused |
| `0x1E`–`0x1F` | Future layout nodes (unallocated) |
| `0x20`–`0x22`, `0x24`–`0x2A` | Semantic control nodes (this file) |
| `0x23`, `0x2B`–`0x2F` | Future semantic controls (unallocated) |
| `0x30`–`0x7E` | Future categories (unallocated) |
| `0x7F` | `COMMENT` (utility) |
| `0x80`–`0xFF` | Reserved |
| `0x0100`–`0xFFFF` | Application/custom component types |

A renderer MUST handle an unknown component type by rendering a blank node and
continuing — it must not crash or stop decoding.

---

## Conformance

Each primitive's opcode footprint is covered by the golden vectors in
[CONFORMANCE.md](./CONFORMANCE.md). New primitives added here MUST add vectors
before implementation lands in any renderer.