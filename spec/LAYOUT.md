# Pathland Layout Allocation Contract

**Wire protocol version:** 1
**Status:** Draft
**Last Updated:** November 7, 2026

---

## Purpose

This document pins down the **allocation semantics** of the size/frame
properties (`WIDTH`/`HEIGHT`, `ALIGNMENT`, `CONTENT_MODE`, and the `FILL` /
`HUG_CONTENT` sentinels) so that **the same frame produces the same logical-points
layout on every renderer** (HTML, GTK4, and any future SwiftUI/Compose target).
The protocol's sizes are **logical points** (SwiftUI points / Compose `dp` / CSS
pixels) — see the units note in [OPCODE.md](./OPCODE.md) — and this contract
defines what a frame *means*, not how many device pixels it is.

The wire format is unchanged by this document. It is a **contract clarification**:
renderers are aligned to it incrementally (their implementation status lives in
each project's `status.md`, not here).

- Wire format and value types: [OPCODE.md](./OPCODE.md)
- View catalog: [PRIMITIVES.md](./PRIMITIVES.md)
- Modifiers (the `frame` DSL surface): [MODIFIERS.md](./MODIFIERS.md)

> **Implementation status** is tracked per implementing project (a `status.md`
> in each protocol crate/library), **not** in this specification. This document
> defines the protocol contract only.

---

## The size model

Each axis (`WIDTH`/`HEIGHT`) of every view carries exactly one of three size
kinds:

| Kind | Wire value | Meaning | SwiftUI | Compose | CSS |
|------|-----------|---------|---------|---------|-----|
| **Fixed** | a finite positive `F32` | The view occupies **exactly that many points** on the axis. Content fits *inside* the box; a smaller parent constrains it (a `ScrollView` enables overflow). | `.frame(width: 44, height: 44)` | `Modifier.size(44.dp)` / `.width(…)` / `.height(…)` | `width: 44px; height: 44px` |
| **Fill** | `-1` (`FILL`, also `∞` normalized to it) | The view **expands to fill the available space** on the axis — it absorbs the parent's leftover space. | `.frame(maxWidth: .infinity)` | `Modifier.fillMaxWidth()` / `fillMaxHeight()` | flex `flex-grow` / `100%` |
| **Hug** | `-2` (`HUG_CONTENT`) or **absent** | The view takes its **natural / intrinsic** size on the axis. | ideal size (`.fixedSize()` forces it) | `Modifier.wrapContentWidth()` / `height(IntrinsicSize.Min)` | `fit-content` / auto |

Notes:

- **`HUG_CONTENT` and absent are the same size kind.** A view with no `WIDTH`/
  `HEIGHT` property behaves as `HUG_CONTENT` on that axis.
- **Fixed is a hard allocation, not a minimum or a hint.** A renderer must not
  over-allocate a Fixed view beyond its box because the parent has extra space,
  and must not under-allocate it below its box when the parent can provide it
  (a smaller parent constrains it — a parent always has the final say, as in all
  three reference frameworks).
- **Content is fitted within the box** (see [Content fitting](#content-fitting)),
  never used to grow the box.

### Frame bounds

`MIN_WIDTH`/`MIN_HEIGHT` and `MAX_WIDTH`/`MAX_HEIGHT` are per-axis **bounds**,
not additional size kinds. They are applied after the size kind and intrinsic
size are resolved:

1. Resolve the axis size from its kind: Fixed = its box, Fill = the available
   space, Hug = the content's natural size (plus any greedy-primitive rule
   below).
2. Apply bounds:
   - `MAX_*` caps the resolved size.
   - `MIN_*` raises the resolved size.
   - When both are present and conflict, `MIN_*` wins.
3. Position the result with the parent's `ALIGNMENT` when it is smaller than
   the available cross-axis space.

A finite `MAX_*` makes that axis **bounded**:

- A bounded `Fill` or greedy node still fills/grows, but only up to the cap.
- A bounded `Hug` node remains Hug; it does not become Fill.
- A bounded node does **not** propagate an unbounded fill demand to its
  ancestors. Ancestors size to the capped content rather than stretching to
  `100%`.
- A bounded node is **not** stretched on the cross axis. Once capped, it is
  positioned by the parent's `ALIGNMENT` (the default is start).
- In the CSS renderers this is `width/height: auto` plus `max-width/max-height`
  for a Hug node, and `width/height: 100%` plus the max for an explicit `Fill`;
  either way, no `align-self: stretch` is added for the bounded axis.

`MIN_*` does not create Fill; it only raises the resolved size. An explicit
`HUG_CONTENT`/absent axis is Hug and is never stretched.

`IDEAL_WIDTH` (`0x0013`) and `IDEAL_HEIGHT` (`0x0016`) are retired in v1; those
ids must not be reused. `MAX_* = ∞` is not a constraint (the bound is omitted);
`WIDTH/HEIGHT = ∞` normalizes to `Fill`.

---

## Container allocation rules

Containers (`VSTACK`/`HSTACK`/`ZSTACK`, `GRID`, `SCROLLVIEW`, and the semantic
composites) allocate their children by these rules. The **main axis** is the
container's layout direction; the **cross axis** is the other one.

### Main axis

A child's main-axis size comes from its size kind:

- **Fixed** → the child is allocated exactly its box.
- **Fill** → the child expands; **leftover main-axis space goes only to `Fill`
  children** (shared between them). A container with no `Fill` child leaves the
  leftover space empty at the end of its main axis.
- **Hug** (or absent) → the child's natural size, positioned at the start of
  the leftover space.

### Cross axis

- A **Fixed** or **Hug** child keeps its own size on the cross axis and is
  **positioned** by the container's `ALIGNMENT`.
- **`ALIGNMENT` positions but never resizes.** It selects the placement of
  Fixed/Hug children within leftover space; it does not stretch them. The value
  is a **2D position code** (the [ZStack table](./PRIMITIVES.md#zstack--zstack-0x12)):
  a stack reads a single axis (a `VStack` the horizontal component, an `HStack`
  the vertical — codes `0/1/2`), a `ZStack`/grid both axes. With no `ALIGNMENT`
  (code `0`/absent) the default cross-axis position is the **start**. Code `3`
  is `top-center`, never a stretch; stretching lives in the **child's size
  kind** (`WIDTH`/`HEIGHT` = `FILL`).
- Only a **`Fill`-sized child** fills the container's cross size.
- **The default is *hug*, not stretch.** A child with no cross-axis size is NOT
  stretched to fill the cross axis. This deliberately matches **SwiftUI**
  (children report their ideal size; a `VStack`/`HStack` aligns them) and
  **Compose** (Row/Column children wrap content cross-axis unless
  `fillMaxWidth/Height`), rather than CSS flexbox's `align-items: stretch`
  default. A renderer must therefore NOT stretch cross-axis children implicitly
  (e.g. the web renderer uses `align-items: flex-start`, not the flex default).

### Layout-greedy primitives

The cross-axis hug default has **four intrinsic exceptions** — primitives whose
native behavior is to fill space (SwiftUI/Compose precedent), so an app does not
need an explicit `FILL` frame to get their natural behavior:

| Primitive | Greedy on | Precedent |
|-----------|-----------|-----------|
| `DIVIDER` | cross-axis | SwiftUI `Divider()` fills the stack's available width/height; CSS `hr` is block-width |
| `SCROLLVIEW` | its **scroll axis** only (`SCROLL_AXIS`, default vertical) | SwiftUI `ScrollView` expands along its scroll axis; the cross axis hugs its content unless a `Fill` child propagates |
| `COLOR` | both axes | SwiftUI `Color` is layout-greedy and expands unless a frame constrains it |
| `SPACER` | main-axis | `Spacer()` absorbs leftover main-axis space by definition |

Rules:

- Greedy fills **only the axis the primitive is designed to fill** — never both
  for `DIVIDER`/`SPACER`/`SCROLLVIEW`; a `SCROLLVIEW` fills its scroll axis and
  **hugs on the cross axis** (a `Fill`-width content child propagates `Fill`).
- An **explicit `WIDTH`/`HEIGHT` always overrides** the greedy default (`Fixed`
  = that box; `FILL` = stretch anyway; `HUG_CONTENT` = natural size).
- Every other primitive (Text, Image, stacks, buttons, controls) is a plain
  **Fixed / Fill / Hug** node governed by the size model — no implicit
  greediness.

### Non-visual nodes (layout-transparent)

Some nodes exist in the retained tree but render **no** element and take part in
**no** layout. They are **layout-transparent**:

- They are **not slots**: a stack/grid parent MUST NOT allocate space to them and
  MUST NOT contribute spacing for them (they are skipped entirely, as if absent).
- They have no box, no intrinsic size, and no content mode; `WIDTH`/`HEIGHT` and
  alignment do not apply.
- Their position in the tree carries **no layout meaning** — applications place
  them for lifetime stability, not for geometry.

| Node | Role |
|------|------|
| `COMMENT` 0x7F | Opaque/debug marker |
| `MEDIA_CONTROLLER` 0x30 | Owns a media resource + transport; referenced by a `VIDEO` surface ([MEDIA.md](./MEDIA.md)) |

Audio has no node at all: an audio playback is a controller plus the
application's own views, which are ordinary layout nodes.

### A container that is itself Fixed

A container with a **Fixed** `WIDTH`/`HEIGHT` is allocated exactly that box and
lays its children out *within* it. A Fixed box **constrains layout but does not
clip**: content that overflows it is drawn outside the box unless
`CLIPS_TO_BOUNDS` clips it (SwiftUI `.clipped()` parity); it scrolls instead
when the container is a `SCROLLVIEW`.

### Rationale

The choice of explicit allocation and the renderer bugs it addresses are
recorded in [ADR-0011](../docs/decisions/0011-explicit-layout-allocation.md).
The complete allocation requirements remain above.

---

## Virtual extent (lazy containers)

A `LAZY_VSTACK`/`LAZY_HSTACK`/`LAZY_VGRID`/`LAZY_HGRID` that carries
`ITEM_COUNT` (`0x0022`) and `ITEM_EXTENT` (`0x0023`) is a **windowed data
container**. It lays out only the units the application emitted, but occupies
the full list extent so a scroll viewport reports the correct scrollbar and
offsets.

The **window unit** is one item for a stack, one **row** for a `LAZY_VGRID`,
and one **column** for a `LAZY_HGRID`. Let `N = ITEM_COUNT` (unit count),
`E = ITEM_EXTENT` (the unit's main-axis size: item extent, row height, or
column width), `S = max(0, SPACING)` (absent `SPACING` is `0`), and
`F = FIRST_ITEM_INDEX` (default `0`). Windowing requires `E > 0`; an absent or
non-positive `ITEM_EXTENT` means the lazy container is an ordinary eager
container.

- **Virtual extent** — the container's main-axis size is
  `N*E + max(0, N-1)*S`. `N = 0` gives `0`.
- **Item offset** — absolute item `i` starts at `i*(E+S)`.
- **Window placement** — emitted unit `j` is absolute unit `F+j` and starts at
  `(F+j)*(E+S)`. Children are emitted in ascending absolute-unit order. A grid
  unit emits all of its cells (row-major for `LAZY_VGRID`, column-major for
  `LAZY_HGRID`); a stack unit emits one item.
- **Child size** — the container constrains each child to exactly `E` on the
  main axis (a Fixed box; overflow is visible unless `CLIPS_TO_BOUNDS`). The
  cross axis is the ordinary stack contract: the child keeps its size and is
  positioned by `ALIGNMENT`; a `Fill`-sized child stretches on the cross axis.
- **Missing children** — the window need not cover every unit. Units with no
  emitted children are empty virtual space; the renderer never invents rows.
  Emitted children beyond `N` units are ignored.
- **Fill propagation** — the virtual extent is the container's main-axis size;
  it does not propagate `Fill` from a window child on the main axis. Cross-axis
  fill propagation is unchanged.
- **Visible range** — the renderer computes which absolute units intersect the
  nearest scrolling ancestor's viewport (or the root surface when there is
  none). In the lazy container's main-axis content coordinates, with the
  viewport spanning `[viewportStart, viewportEnd)`:
  `first = clamp(floor(viewportStart/(E+S)), 0, N)` and
  `endExclusive = clamp(ceil(viewportEnd/(E+S)), 0, N)`. The range is
  conservative: it may include a partially scrolled item, but never excludes a
  visible one. The renderer reports `EVENT::VISIBLE_RANGE` for nodes with
  listener bit 10, at most once per frame and only when `(first, endExclusive)`
  changes. A renderer SHOULD report the final range when a scroll gesture ends.
- **Engine prefetch** — the engine MAY add a bounded, directional prefetch
  margin around the reported range, derived from the report velocity (the
  distance between successive reports over elapsed time). The margin is
  engine-owned and not author-configured; a small base margin applies to the
  first report and to slow scrolling. A report with no movement returns to the
  base margin. The application may still emit a larger window.

**Variable item extents.** A lazy **stack** (not a grid) MAY carry
`ITEM_EXTENTS` (`0x0025`, LIST of F32), `FIRST_ITEM_OFFSET` (`0x0026`, F32), and
`TOTAL_EXTENT` (`0x0027`, F32) alongside the fixed properties. When present:

- `ITEM_EXTENTS` holds one main-axis extent per **emitted window item**, in
  window order. `FIRST_ITEM_OFFSET` is the offset of the first emitted item in
  the full content, and `TOTAL_EXTENT` is the full content extent.
- The window starts at `FIRST_ITEM_OFFSET`; each emitted item uses its extent;
  the window extent is `sum(ITEM_EXTENTS) + max(0, k-1)*S`.
- The container's virtual extent is `TOTAL_EXTENT`; the fixed formula is not
  used. `ITEM_EXTENT` remains the **default/estimated** extent for items outside
  the emitted window.
- The renderer computes the visible range by walking the window extents from
  `FIRST_ITEM_OFFSET`; if the viewport lies outside the emitted window, it
  estimates with `ITEM_EXTENT` and reports that estimate so the application can
  emit a covering window.
- A `LAZY_VGRID`/`LAZY_HGRID` uses the uniform `ITEM_EXTENT` model; variable
  per-row/per-column extents are not defined for grids yet.

Without `ITEM_COUNT` + `ITEM_EXTENT`, a lazy stack is an ordinary eager stack
and this section does not apply. `ITEM_EXTENTS` does not replace
`ITEM_EXTENT` — the default/estimate is still required.

---

## Content fitting, truncation & clipping

Content-bearing views fit their content **inside** their allocated box:

- **Media** (`IMAGE`, `VIDEO`): fitted per `CONTENT_MODE` — `Fit` (0) scales to
  contain (letterboxed), `Fill` (1) scales to cover (cropped). The box is the
  allocation, never the content's intrinsic size. A `VIDEO` surface's playback
  lives on a separate `MEDIA_CONTROLLER` node; audio has no visual at all (§
  Non-visual nodes) and is controlled by the application's own views.

### Text

- **Wrapping.** An unconstrained `TEXT` lays out on a **single line at its
  natural width**. It **wraps at word boundaries only when its width is
  constrained** — a Fixed `WIDTH`, or a `LINE_LIMIT` that needs multiple lines.
  An unbreakable word wider than the box may break by character. A wrapped
  Text's natural height is its **line count × line height** (so a Hug container
  can compute its size). A Fixed `HEIGHT` constrains the box; overflow follows
  the overflow rules ([above](#a-container-that-is-itself-fixed)).
- **`LINE_LIMIT`.** A positive `LINE_LIMIT` (N) clamps the rendered text to **at
  most N lines**; any overflow beyond the last visible line is replaced by an
  ellipsis `…` at its end (**tail** truncation). `0`/absent = unlimited. It does
  not force a single line — lines are formed by wrapping at the available width
  and by explicit line breaks.
- **`TRUNCATION_MODE`.** `Head`=0, `Middle`=1, `Tail`=2 **positions the
  ellipsis** (at the start / middle / end of the last visible line) whenever a
  `LINE_LIMIT` clamp truncates. It applies **only when such a clamp truncates**:
  on its own it has **no observable effect** (SwiftUI-aligned) — it never forces
  a single line and never truncates by itself.

### Clipping — `CLIPS_TO_BOUNDS`

`CLIPS_TO_BOUNDS` (0x1010, U8) is a **post-layout visual clip**: the node's
content and its own painted decoration (fills, backgrounds, borders) are
clipped to its **allocated bounds box** — on **both axes**. Layout is
unaffected: the node is still allocated and positioned exactly as if unclipped;
only what is painted is clipped. `0`/absent = no clipping. A `SHAPE_KIND`
(0x0006) carried alongside constrains the clip to that geometry instead of the
plain bounds box (a bare `.clipped()` is `CLIPS_TO_BOUNDS` with no shape, so the
clip is the bounds box). A `SCROLLVIEW` scrolls its content rather than
clipping it.

### Containers

Stacks, scroll, and other containers lay their children out inside; a Fixed box
constrains them (scroll for `SCROLLVIEW`, otherwise overflow is visible unless
`CLIPS_TO_BOUNDS` clips it).

### View that fits (`VIEW_THAT_FITS` 0x17)

A fit slot is **size-taking on both axes**: it stretches to its parent's
proposal (`FILL` semantics — never hugs), because its **allocated width is the
unit of fitting**. It holds at most one (selected) child in the live form, laid
out normally inside the box; a **full-snapshot emission** (the web SSR first
document) carries every candidate and the renderer lays each one out, selecting
the visible branch by CSS container queries keyed to the slot's own width (a
no-JS correct first paint — see [PRIMITIVES.md](./PRIMITIVES.md#view-that-fits)).
The renderer measures its allocated width and derives the fit index (threshold
rule in [PRIMITIVES.md](./PRIMITIVES.md#view-that-fits)). No content-based
measurement of candidates is ever done or transmitted — the query thresholds are
app-declared, so the pick is a pure function of `(thresholds, allocated width)`.

---

## Conformance cases

These are golden vectors derived from real cross-renderer divergences. For each
case, **the same frame must yield the same logical-points size on every
renderer** (HTML + GTK4 today; SwiftUI/Compose as they land).

| # | Frame | Container context | Expected |
|---|-------|-------------------|----------|
| C1 | `Image` `WIDTH=44, HEIGHT=44` | a row with leftover space | exactly `44×44` points (not the content's intrinsic size, not stretched) |
| C2 | Fixed `220×220` child | a `280`-wide column, cross axis | exactly `220` on the cross axis, aligned (not filled to `280`) |
| C3 | `Fill`-height container | a `HUG`-height child | the child keeps its natural height; leftover stays empty / goes to a `Fill` sibling |
| C4 | Composite button, no frame | a `Fill`-height column | content height (not absorbed/stretched to the viewport) |
| C5 | Hug `Text` | a stack | natural size on both axes (not stretched cross-axis) |
| C6 | Fixed-width `VStack` | fixed `200` box, taller children | children lay out within `200`; overflow is visible unless `CLIPS_TO_BOUNDS` clips it |
| C7 | `Text` `WIDTH=120`, longer content | a column | wraps at word boundaries within `120`; natural height = line count × line height |
| C8 | `Text` long content, `LINE_LIMIT=2` | a `200`-wide column | at most 2 lines; overflow on the last visible line tail-ellipsized (`…`) |
| C9 | `Text` `LINE_LIMIT=1`, `TRUNCATION_MODE=Head` | a `200`-wide column | single line, ellipsis at the start, the tail preserved |
| C10 | `CLIPS_TO_BOUNDS=1` | a Fixed box with overflowing content | content (and the node's painted decoration) clipped to the allocated box; layout unchanged |
| C11 | `Grid` `GRID_COLUMNS=2`, `SPACING=8`, Hug cells | a definite-width parent | two **equal `1fr` columns** with an 8pt gap; Hug cells keep their natural size, positioned at Leading in their track |
| C12 | `Grid` `GRID_COLUMNS=2` with a `FILL`-width cell | a definite-width parent | the `FILL` cell **stretches to fill its column**; Hug cells keep their size; the grid does **not** propagate the `FILL` up to a Hug parent on the auto axis |
| C13 | `ScrollView` (`SCROLL_AXIS=Vertical`, no frame) | a `FILL`-sized column, content `FILL`-width | fills the available height (scroll axis); the cross (horizontal) axis hugs — it fills only because the content has a `Fill`-width child; content taller than the viewport **scrolls**, never clips or overflows the viewport |
| C14 | `ScrollView` `HEIGHT=HUG_CONTENT` | a Hug column | the viewport's height is the content's **natural height** (no vertical scroll); the cross axis hugs its content (no implicit width greed) |
| C15 | `ScrollView` `SCROLL_AXIS=Horizontal`, no frame | a Hug row | fills the available **width** (scroll axis); the height hugs its content unless a `Fill`-height child propagates |
| C16 | `ScrollView` `SCROLL_AXIS=Vertical`, content `WIDTH=Fill` | a Fill column | fills the available height; the width fills only because the content has a `Fill`-width child |
| C17 | Hug child with `MAX_WIDTH=620` | a 1000-wide centered column | width is the content's natural width up to `620`, centered; no `width:100%`, no cross-axis stretch |
| C18 | `WIDTH=Fill` child with `MAX_WIDTH=620` | a 1000-wide centered column | fills up to `620` and is centered; the max bounds the fill, and ancestors do not become unbounded Fill |
| C19 | `MIN_WIDTH=700, MAX_WIDTH=620` | any parent | resolved width is at least `700` (min wins over max) |
| C20 | `MAX_HEIGHT=200` on a ScrollView's cross-axis content | a bounded viewport | the content is capped at `200` and positioned by the parent's alignment; the viewport does not stretch past the cap |
| C21 | `LazyVStack` `ITEM_COUNT=1000`, `ITEM_EXTENT=44`, `SPACING=0`, `FIRST_ITEM_INDEX=100`, 10 children | a bounded scroll viewport | main-axis size `44000`; child 0 starts at `4400`, child 9 at `4796`; only the emitted window exists |
| C22 | `LazyVStack` `ITEM_COUNT=10`, `ITEM_EXTENT=44`, `SPACING=8`, `FIRST_ITEM_INDEX=3`, 2 children | any parent | main-axis size `512`; child 0 starts at `156`, child 1 at `208` |
| C23 | `LazyVStack` `ITEM_COUNT=100`, `ITEM_EXTENT=44`, `FIRST_ITEM_INDEX=98`, 5 children | any parent | only indices `98`–`99` render; children `100`–`102` are ignored; the container still occupies `4400` |
| C24 | `LazyVStack` with `ITEM_COUNT` but no `ITEM_EXTENT` | any parent | eager `VStack` behavior; no virtual extent, no `VISIBLE_RANGE` reporting |

Grid/scroll allocation is specified in [PRIMITIVES.md §Grid / §ScrollView](./PRIMITIVES.md#grid--grid-0x13). Lazy stacks use the virtual-extent contract above; lazy grids are identical to their eager base until a grid window contract lands.

Conformance tests (golden vectors) are wired in the renderer alignment passes
that follow this document; see each project's `status.md`.
