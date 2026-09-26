# Pathland Primitive Views

**Wire protocol version:** 1
**Status:** Draft
**Last Updated:** September 3, 2026

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
- **Native elements everywhere**: VStack → GTK box / CSS flex column; Text → GTK label / `<span>`; semantic controls →
  native OS controls in Native Token Mode (below). Never a generic canvas unless
  a platform has no native equivalent.

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
control**:

| Component | Native tokens |
|-----------|---------------|
| `Button` 0x20 | `UIButton`/`NSButton`, `GtkButton`, `<button>` |
| `TextField`/`SecureField` 0x21 | `UITextField`/`NSTextField`, `GtkEntry`, `<input>` |
| `Toggle` 0x24 | `UISwitch`/`NSSwitch`, `GtkSwitch`/`GtkCheckButton`, `<input type="checkbox">` |
| `Slider` 0x25 | `UISlider`/`NSSlider`, `GtkScale`, `<input type="range">` |
| `Stepper` 0x26 | `UIStepper`/`NSStepper`, `GtkSpinButton`, `-/+` buttons |
| `DatePicker` 0x27 | `UIDatePicker`/`NSDatePicker`, `GtkCalendar`, `<input type="date">` |
| `ColorPicker` 0x2A | native color panel, `GtkColorButton`, `<input type="color">` |
| `Picker` 0x28 | `UISegmentedControl`/popup, `GtkDropDown`, `<select>` |
| `Menu` 0x29 | `UIMenu`, `GtkPopoverMenu`, floating popover |

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
| `0x09`–`0x0F` | — | Future drawing/visual nodes |

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
| `0x1D`–`0x1F` | — | Future layout/container nodes |

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
| `Label` | `HStack` + `IMAGE` + `TEXT` |

---

## A. Primitive Drawing & Visual Nodes

### Text — `TEXT` 0x01

A run of styled text. The server dictates content and style; the client measures
and renders.

- **Protocol**: a leaf node; content via `STYLE::SET_TEXT` or a bound signal.
- **Properties**: `LINE_LIMIT` (0x000B, U32), `TEXT_ALIGNMENT` (0x000C, enum
  code), `TRUNCATION_MODE` (0x000D, enum code), plus all text-formatting and
  appearance modifiers from [MODIFIERS.md](./MODIFIERS.md).
- **Events**: none by default; any listener via `EVENT_LISTENERS`.
- **Renderer mapping**: GTK `GtkLabel`; HTML `<span>`/`<p>`.
  Font handling is client-owned (the renderer resolves `FONT_FAMILY` /
  `FONT_*` to its native text system).

### Image — `IMAGE` 0x02

A static image or icon asset. Asset loading is client-owned.

- **Protocol**: a leaf node; source via `IMAGE_SOURCE` (0x1002, STRING: a
  resource name, file path, or absolute URL).
- **Properties**: `IMAGE_SOURCE`, `CONTENT_MODE` (0x001C, enum `Fit`=0 /
  `Fill`=1), size modifiers, `OPACITY`, `CLIPS_TO_BOUNDS`.
- **Events**: none by default.
- **Renderer mapping**: GTK `GtkPicture`/`GtkImage`; HTML `<img>`.
- **Note (SwiftUI `AsyncImage`)**: remote/async loading is **not a separate
  primitive** — it is `IMAGE` with `IMAGE_SOURCE` set to an absolute URL; the
  renderer loads asynchronously and re-issues `SET_PROPERTY(IMAGE_SOURCE)` if
  the source changes.

### Audio — `AUDIO` 0x09

An audio playback node. Playback interaction (play/pause/volume/seek) is
**renderer-native** (the web renderer emits `controls`); the app supplies only
the source reference and size/layout.

- **Protocol**: a leaf node; source via `AUDIO_SOURCE` (0x1033, STRING: a
  resource name, file path, or absolute URL — an asset reference, never
  embedded in the opcode stream).
- **Properties**: `AUDIO_SOURCE`, size modifiers.
- **Events**: none by default.
- **Renderer mapping**: GTK `GtkMediaFile`/GStreamer; HTML `<audio controls>`.

### Video — `VIDEO` 0x0A

A video playback node. Playback interaction is **renderer-native** (`controls`);
the app supplies the source reference and size.

- **Protocol**: a leaf node; source via `VIDEO_SOURCE` (0x1034, STRING asset
  reference).
- **Properties**: `VIDEO_SOURCE`, `CONTENT_MODE`, size modifiers.
- **Events**: none by default.
- **Renderer mapping**: GTK `GtkVideo`/`GtkMediaFile`; HTML `<video controls>`.
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
- **Renderer mapping**: GTK `GtkDrawingArea`/colored box (expands); HTML `<div>`
  with `background-color` (`flex:1;align-self:stretch` — greedy). The renderer paints the pixel fill.
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
- **Renderer mapping**: GTK/HTML/CSS drawing (CSS `border-radius`, `clip-path`,
  or inline SVG for `Path`). `Path` is the documented
  case where a renderer paints directly (no native element equivalent).

### Divider — `DIVIDER` 0x05

An axis-aligned 1px separator line.

- **Protocol**: a leaf node; orientation implied by the parent stack axis.
- **Properties**: `COLOR` (0x100A), `BORDER_WIDTH` (0x1003) — line thickness.
- **Events**: none.
- **Renderer mapping**: GTK `GtkSeparator`; HTML `<hr>` / `<div>` with a border.

### Spacer — `SPACER` 0x06

A flexible expanding layout filler.

- **Protocol**: a leaf node with no properties; expands to the remaining main
  axis space (`flex-grow:1` in HTML, expanding box in GTK).
- **Events**: none.

### ProgressView — `PROGRESS_VIEW` 0x07

Determinate progress or an activity indicator.

- **Protocol**: a leaf node. Determinate: `PROGRESS` (0x200E, F32 0.0–1.0, or
  0.0–`MAX_VALUE` if set). Indeterminate: `IS_INDETERMINATE` (0x200F, U8 1) —
  the renderer animates a native activity indicator.
- **Events**: none.
- **Renderer mapping**: GTK `GtkProgressBar`/`GtkSpinner`; HTML
  `<progress>`/`<div class="spinner">`.

### Gauge — `GAUGE` 0x08

A value shown against a scale (SwiftUI `Gauge`).

- **Protocol**: a leaf node with `VALUE` (0x2006), `MIN_VALUE` (0x2007),
  `MAX_VALUE` (0x2008). The gauge style (linear/circular) is renderer/token-owned.
- **Events**: none (read-only; an interactive gauge is a `Slider` 0x25).

---

## B. Layout & Container Primitives

Layout primitives arrange children. They carry `SPACING` (0x0001),
`ALIGNMENT` (0x0002), and `CONTENT_MARGINS` (0x0005), plus layout modifiers from
[MODIFIERS.md](./MODIFIERS.md#1-layout). Renderers map them to their native flex
box / grid primitives (GTK `GtkBox`/`GtkGrid`, CSS flex/grid).

### VStack — `VSTACK` 0x10

Vertical flex stack. **Properties**: `SPACING`, `ALIGNMENT` (enum: `Leading`=0,
`Center`=1, `Trailing`=2, `Fill`=3), `CONTENT_MARGINS`.

### HStack — `HSTACK` 0x11

Horizontal flex stack. Same properties as `VStack`.

### ZStack — `ZSTACK` 0x12

Depth-overlapping layer stack; child index = draw order (later = on top).
**Properties**: `ALIGNMENT`. Renderer: GTK `GtkOverlay`; HTML absolutely
positioned children.

### Grid — `GRID` 0x13

Static 2D matrix grid, eagerly rendered with aligned rows & columns. Children
are cells, row-major in insertion order. **Properties**: `ALIGNMENT`, `SPACING`,
`WIDTH`/`HEIGHT` (cell-axis count; `FILL` = auto-fit). Renderer: GTK `GtkGrid`;
HTML CSS grid.

### ScrollView — `SCROLLVIEW` 0x14

Scrollable content container. **Events**: with the `SCROLL` (bit 8) listener the
renderer reports the scroll offset via `EVENT::SCROLL`; with `WHEEL` (bit 9) it
reports deltas via `EVENT::WHEEL` (both draft — see [EVENTS.md](./EVENTS.md)).
Renderer: GTK `GtkScrolledWindow`; HTML overflow container.

### LazyVGrid — `LAZY_VGRID` 0x15

Virtualized vertical grid: windowed rendering for large datasets; children are
cells, row-major. **Properties**: `ALIGNMENT`, `SPACING`, `WIDTH` (column count;
`FILL` = auto-fit). A renderer MAY defer realizing off-screen cells until
scrolled into view.

### LazyHGrid — `LAZY_HGRID` 0x16

Virtualized horizontal grid; children are cells, column-major. **Properties**:
`ALIGNMENT`, `SPACING`, `HEIGHT` (row count; `FILL` = auto-fit).

### LazyVStack — `LAZY_VSTACK` 0x1B

Virtualized vertical stack; same properties as `VStack`, with windowed
realization of children near the visible region.

### LazyHStack — `LAZY_HSTACK` 0x1C

Virtualized horizontal stack; same properties as `HStack`, with windowed
realization.

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

- **Native Token Mode (leaf):** no children → native button
  (`UIButton`/`NSButton`, `GtkButton`, `<button>`); the label is the node's text
  (`SET_TEXT`). If the text is also absent, render an empty platform button
  shell (leaf fallback).
- **Composite Override Mode (container):** children present → suppress default
  button chrome, render the custom label tree, wrap it with a native
  click/press gesture that reports `POINTER_DOWN`/`POINTER_UP` for the button's
  `ACTION_ID` callback.
- **Events:** the tap is the app-side composition of the raw pointer events
  (`POINTER_DOWN` then `POINTER_UP` on the same target). Without `ACTION_ID` /
  `BINDING_ID` / `EVENT_LISTENERS`, a renderer MUST drop the interaction (Event
  Guards).
- **Renderer mapping**: GTK `GtkButton`; HTML `<button>`.
  Button styles are renderer/token-owned.
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
- **Renderer mapping**: GTK `GtkEntry`; HTML `<input type="text">`.
  A secure field MUST mask characters and MUST NOT echo the value.

### TextEditor — `TEXT_EDITOR` 0x22

Multi-line text editing area.

- **Protocol**: a leaf node; content via `SET_TEXT`, value binding via
  `BINDING_ID` (0x2017).
- **Properties**: `LINE_LIMIT`, `TEXT_ALIGNMENT`, `TRUNCATION_MODE`,
  `IS_SECURE` (unused), text modifiers.
- **Events**: same as `TextField` — `TEXT_CHANGED` (0x07), `FOCUS_CHANGED`
  (0x08), `EDITING_CHANGED` (0x09), `SUBMIT` (0x0A) — gated by `BINDING_ID` /
  `EVENT_LISTENERS`.
- **Renderer mapping**: GTK `GtkTextView`; HTML `<textarea>`.

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
  `TOGGLE_STYLE` token — `UISwitch`/`NSSwitch`, `GtkSwitch`, checkbox input
  (`TOGGLE_STYLE=Checkbox`), or a toggle button. The style token switches native
  variants **without emitting child nodes**.
- **Composite Override Mode (container):** a custom body (e.g. label + icon)
  wrapped with a native toggle gesture; `SELECTED` still drives the state.
- **Events:** a user change emits `VALUE_CHANGED` (0x06) with `B` = 0/1 — gated
  by `BINDING_ID`. The app writes it into `SELECTED`; the engine re-emits the
  `SELECTED` property.
- **Renderer mapping**: GTK `GtkSwitch`/`GtkCheckButton`; HTML checkbox / styled
  switch / toggle button.

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

- **Native Token Mode (leaf):** native slider (`UISlider`/`NSSlider`,
  `GtkScale`, `<input type="range">`); the renderer owns 120 FPS client-side
  thumb dragging and resolves the semantic value from its own track geometry.
- **Composite Override Mode (container):** a custom track/thumb body wrapped
  with a native drag gesture that reports `VALUE_CHANGED`.
- **Events:** `VALUE_CHANGED` (0x06, `A=targetId, B=value (f32)`) — gated by
  `BINDING_ID`.
- **Renderer mapping**: GTK `GtkScale`; HTML `<input type="range">`.

### Stepper — `STEPPER` 0x26

A discrete increment/decrement control. `VALUE` (0x2006), `STEP_VALUE`
(0x2009), `MIN_VALUE` (0x2007), `MAX_VALUE` (0x2008), `BINDING_ID` (0x2017).
Events: `VALUE_CHANGED` after each press.

### DatePicker — `DATE_PICKER` 0x27

A date & time selection modal/popover control.

- **Protocol**: a leaf node whose value is set with the **`STYLE::SET_DATE`**
  command (draft 0x04, see OPCODE.md): `A=nodeId, B=days since epoch (I32,
  pre-1970 negative), C=millis of day (U32, 0..86,400,000)`.
- **Properties**: `DATE_PICKER_MODE` (0x2013, enum: `Date`=0, `Time`=1,
  `DateAndTime`=2), `BINDING_ID` (0x2017), size modifiers.
- **Events**: `DATE_CHANGED` (draft 0x0D, `A=targetId, B=days (I32),
  C=millis of day (U32)`) — inline, gated by `BINDING_ID`.
- **Renderer mapping**: GTK `GtkCalendar`/`GtkSpinButton`; HTML `<input
  type="date">`/`<input type="time">`.

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
- **Renderer mapping**: GTK `GtkComboBoxText`/`GtkDropDown`; HTML `<select>` /
  segmented buttons.

### Menu — `MENU` 0x29

A **semantic control**: contextual action trigger and popover container. The
client platform owns dynamic popover presentation, overlay placement, tap/focus
management, and accessibility focus trapping (`UIMenu` on iOS, `GtkPopoverMenu`
on GTK, floating popover on Web).

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
- **Renderer mapping**: GTK `GtkMenuButton`/`GtkPopoverMenu`; HTML `<div
  role="menu">`.

### ColorPicker — `COLOR_PICKER` 0x2A

A native system color picker control.

- **Protocol**: a leaf node with `COLOR_VALUE` (0x2012, COLOR — packed
  `0xAARRGGBB`, sRGB) and `BINDING_ID` (0x2017).
- **Events**: `VALUE_CHANGED` — the value field carries the packed color **as an
  f32 bit pattern** of `0xAARRGGBB` (the app reinterprets it) — gated by
  `BINDING_ID`.
- **Renderer mapping**: native color panel / `GtkColorButton`; HTML `<input
  type="color">`.

---

## Reserved component type IDs

| Range | Use |
|-------|-----|
| `0x01`–`0x08` | Primitive drawing nodes (allocated, this file) |
| `0x09`–`0x0F` | Future drawing nodes (unallocated) |
| `0x10`–`0x16`, `0x1B`–`0x1C` | Layout & container primitives (this file) |
| `0x17`–`0x1A` | Reserved (formerly composites) — never reused |
| `0x1D`–`0x1F` | Future layout nodes (unallocated) |
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