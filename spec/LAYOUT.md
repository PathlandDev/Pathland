# Pathland Layout Allocation Contract

**Wire protocol version:** 1
**Status:** Draft
**Last Updated:** October 1, 2026

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
  Fixed/Hug children within leftover space; it does not stretch them. With no
  `ALIGNMENT`, the default cross-axis position is **Leading/Start**. The enum's
  `Fill` (3) therefore means *default (hug) positioning*, not stretch.
- Only a **`Fill`** child fills the container's cross size.
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
| `SCROLLVIEW` | both axes | SwiftUI `ScrollView` / Compose scroll fills the available space (the point of a scroll region) |
| `COLOR` | both axes | SwiftUI `Color` is layout-greedy and expands unless a frame constrains it |
| `SPACER` | main-axis | `Spacer()` absorbs leftover main-axis space by definition |

Rules:

- Greedy fills **only the axis the primitive is designed to fill** — never both
  for `DIVIDER`/`SPACER`.
- An **explicit `WIDTH`/`HEIGHT` always overrides** the greedy default (`Fixed`
  = that box; `FILL` = stretch anyway).
- Every other primitive (Text, Image, stacks, buttons, controls) is a plain
  **Fixed / Fill / Hug** node governed by the size model — no implicit
  greediness.

### A container that is itself Fixed

A container with a **Fixed** `WIDTH`/`HEIGHT` is allocated exactly that box and
lays its children out *within* it. A Fixed box **constrains layout but does not
clip**: content that overflows it is drawn outside the box unless
`CLIPS_TO_BOUNDS` clips it (SwiftUI `.clipped()` parity); it scrolls instead
when the container is a `SCROLLVIEW`.

### Rationale

These rules are the bugs-fixed-and-avoided model of the reference renderers:

- A **Fixed image** in a row stays its box (never scaled to content or stretched
  by a tall row).
- A **Fixed/Hug child in a `Fill` container** keeps its size; leftover space
  goes to `Fill` children (or stays empty).
- A **composite button** (e.g. a library row) sizes to its content on the main
  axis unless the node is explicitly `Fill`.

---

## Content fitting, truncation & clipping

Content-bearing views fit their content **inside** their allocated box:

- **Media** (`IMAGE`, `VIDEO`, and the visual of `AUDIO`): fitted per
  `CONTENT_MODE` — `Fit` (0) scales to contain (letterboxed), `Fill` (1) scales
  to cover (cropped). The box is the allocation, never the content's intrinsic
  size.

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
| C11 | `Grid` `WIDTH=2`, `SPACING=8`, Hug cells | a definite-width parent | two **equal `1fr` columns** with an 8pt gap; Hug cells keep their natural size, positioned at Leading in their track |
| C12 | `Grid` `WIDTH=2` with a `FILL`-width cell | a definite-width parent | the `FILL` cell **stretches to fill its column**; Hug cells keep their size; the grid does **not** propagate the `FILL` up to a Hug parent on the auto axis |
| C13 | `ScrollView` (greedy, no frame) | a `FILL`-sized column | fills the available width and height; content taller/wider than the viewport **scrolls**, never clips or overflows the viewport |
| C14 | `ScrollView` `HEIGHT=HUG_CONTENT` | a Hug column | the viewport's height is the content's **natural height** (no vertical scroll); the horizontal axis stays greedy |

Grid/scroll/lazy allocation is specified in [PRIMITIVES.md §Grid / §ScrollView](./PRIMITIVES.md#grid--grid-0x13); the lazy containers are identical to their eager base with windowed realization only.

Conformance tests (golden vectors) are wired in the renderer alignment passes
that follow this document; see each project's `status.md`.