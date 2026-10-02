# Pathland Core Events

**Wire protocol version:** 1
**Status:** Draft
**Last Updated:** October 1, 2026

---

## Purpose

This document is the **catalog of core events** the Pathland protocol supports.
Events are the **raw inputs** a renderer reports about the user's interaction —
pointer motion, keys, value changes, text edits, scroll. Only the **core
events** are specified here; **gestures** (tap, long-press, drag, magnify,
rotate) are explicitly **out of scope** and are composed application-side from
the raw pointer events (see [Deferred: gestures](#deferred-gestures)).

- View catalog: [PRIMITIVES.md](./PRIMITIVES.md)
- Modifiers (properties): [MODIFIERS.md](./MODIFIERS.md)
- Wire format: [OPCODE.md](./OPCODE.md)

> **Implementation status** is tracked per implementing project (a `status.md`
> in each protocol crate/library), **not** in this specification. This document
> defines the protocol contract only.

---

## Event model

Events flow **host → guest**. The renderer is the producer; the app/engine is
the consumer. Both directions of the protocol are binary and share the same
16-byte opcode format; events are `EVENT`-category (0x03) opcodes.

### Who emits what

- The **renderer** observes native input and writes raw `EVENT` opcodes. It never
  interprets them into high-level actions.
- The **app** drains the events, composes gestures/handlers, and updates its
  bound `State` signals (which in turn re-emit `SET_PROPERTY`/`SET_TEXT`
  deltas).
- The renderer reports an event for a node **only if that node declared it**
  wants it, via the `EVENT_LISTENERS` semantic property (`0x2005`, a u32
  bitmask, `SET_PROPERTY` with the `U32` value type). This makes **any element**
  (a `Text`, a `VStack`, …) able to emit events, not just controls.

### Transport-aware event guards (MUST)

Renderers **MUST** implement event guards to prevent network noise. A user
interaction — tap, text change, drag, value edit — **MUST NEVER** produce an
outbound event opcode unless the target node explicitly declared intent in **at
least one** of these ways:

1. it set the matching `EVENT_LISTENERS` (0x2005) bits, **or**
2. it is a **value-bearing control** reporting by component type (`SLIDER`,
   `TOGGLE`, `PICKER`, `MENU`, `DATE_PICKER`, `COLOR_PICKER`, …), **or**
3. it carries a bound callback property — `ACTION_ID` (0x2016) or `BINDING_ID`
   (0x2017) — that identifies where the interaction must be routed.

Interactions that match none of these are **dropped at the renderer**; they are
never serialized to the event ring or a network batch. This is the wire-level
guard: a `Button` tap with no `ACTION_ID`, no `BINDING_ID`, and no
`EVENT_LISTENERS` produces no events.

### In-flight interaction (MUST)

A node with an **active continuous-input session** — a slider being dragged, a
text input being typed into, a pointer-drag the app is following — is the
authoritative owner of the state it is editing. Renderers **MUST** honor this
for the duration of the session:

1. **Do not apply inbound updates to the in-edit state.** While the session is
   active, a `SET_PROPERTY` of the state under edit (e.g. `VALUE` (0x2006) on a
   `SLIDER` being dragged) is **skipped at the renderer** — the interaction's
   position wins, so a server echo cannot fight the pointer and cause flicker.
   Structural properties (`MIN_VALUE`/`MAX_VALUE`/`STEP_VALUE`, size, style)
   still apply.
2. **Do not re-report events for skipped writes.** Because the write was not
   applied, no change signal fires and no `VALUE_CHANGED` is re-emitted — the
   echo loop is broken at the source.
3. **Resume on session end.** When the interaction ends (release / blur), the
   in-flight state clears and inbound updates apply again; the final position
   was already reported by the interaction's own events.

This is **renderer state, not application state** (AGENTS.md Principle 1): the
in-edit value is transient input/rendered-output state, like the scroll offset
and the text caret.

The session boundaries are also **reported as events** so the application can
suppress echoes of *derived* state the renderer cannot know about (a custom
drag offset, a drawn position):

- `FOCUS_CHANGED` (0x08) — the control gained/lost focus.
- `EDITING_CHANGED` (0x09) — a value/text edit session started/ended.

Both are reported **only when the target declared the matching `EVENT_LISTENERS`
bit** (`FOCUS` = `1<<5`, `EDITING` = `1<<6`). An application that observes
`EDITING_CHANGED { editing: true }` should hold back authoritative echoes of the
edited state until `{ editing: false }`.

The mechanism is **per-state / per-session**: implemented today for value drags
(`SLIDER`); text editing and other continuous inputs use the same path.

### Target resolution

The renderer resolves `targetId` from its own rendered-output tree (its
`id → native element` map): the event's position is hit-tested against that
tree to find the deepest node with the matching listener bit. If no listener is
declared, the event is not reported.

### Transport paths

| Path | Mechanism |
|------|-----------|
| Shared memory (desktop/native) | The host writes `EVENT` opcodes into the event ring (host → guest) at `eventWriteCursor`; the guest drains at `eventReadCursor`. Events are consumed as written (no frame batching). |
| Network batch (SSR → browser) | Host → guest batches use the `0x0001` direction flag of the `PLPL` batch format (see OPCODE.md Transport) |

### Encoding

Every event is a 16-byte opcode:

```
[Category=0x03][Command: u8][Flags: u16][A: u32][B: u32][C: u32]
```

`TEXT_CHANGED` is the exception: its payload is an offset into the host → guest
event arena (shared memory) or the batch's string section (network), so it is
only fully decodable with that string source (the same convention as
`PARAMETER::SET_TEXT`).

---

## Core event catalog

### Pointer

Pointer events carry a viewport-relative point in **logical points** (`B`=x,
`C`=y, f32).

| Command | Value | A | B | C | Flags | SwiftUI counterpart |
| --------- | ------- | --- | --- | --- | ------- | --------------------- |
| `POINTER_DOWN` | 0x01 | targetId | x (f32) | y (f32) | `POINTER_SECONDARY` (0x0001) | press (basis of `onTapGesture`, `Button` action) |
| `POINTER_MOVE` | 0x02 | targetId | x (f32) | y (f32) | `HOVER_ENTER` (0x0001) / `HOVER_LEAVE` (0x0002) | hover/enter/leave, drag basis |
| `POINTER_UP` | 0x03 | targetId | x (f32) | y (f32) | `POINTER_SECONDARY` (0x0001) | release (basis of `onTapGesture`) |

- **Flags**:
  - `POINTER_SECONDARY`: the secondary button (right mouse, long-press) rather
    than the primary.
  - `HOVER_ENTER` (bit 0 of `POINTER_MOVE`): the pointer just entered the
    node's bounds (is now hovering). `HOVER_LEAVE` (bit 1): the pointer just
    left. A plain move has neither bit set.
- **Listener bits**: `POINTER_DOWN` (bit 0), `POINTER_MOVE` (bit 1),
  `POINTER_UP` (bit 2).
- **Renderer**: attaches native press/hover recognition to the matching native
  element when the listener bit is set. Multiple pointer events may be reported
  for one physical gesture (down → several moves → up), all for the same
  `targetId` while the pointer stays over the node.

### Keyboard

| Command | Value | A | B | C | Flags | SwiftUI counterpart |
| --------- | ------- | --- | --- | --- | ------- | --------------------- |
| `KEY_DOWN` | 0x04 | targetId | keyCode (u16, low) | modifiers (u8, low) | `KEY_REPEAT` (0x0002) | `onKeyPress(.down)` |
| `KEY_UP` | 0x05 | targetId | keyCode (u16, low) | modifiers (u8, low) | — | `onKeyPress(.up)` |

- **`keyCode`**: a **canonical, platform-independent logical key code** (a
  renderer maps native scancodes/key-codes onto this canonical set). The
  canonical set (letters/digits/arrows/Enter/Tab/…) is renderer-shared and
  documented in the conformance vectors.
- **`modifiers`**: u8 bitmask — `Shift`=0x01, `Control`=0x02, `Alt`=0x04,
  `Meta/Super`=0x08.
- **`KEY_REPEAT`**: bit 1 of flags; set when the key is an OS auto-repeat.
- **Listener bits**: `KEY_DOWN` (bit 3), `KEY_UP` (bit 4).
- **Focus**: keyboard events are reported to the focused node (the node that
  most recently gained focus), or to the node that declared the listener and
  has keyboard focus. See `FOCUS_CHANGED` below.

### Value

Value events carry a control's semantic value, resolved by the renderer from
its own native control geometry.

| Command | Value | A | B | C | Flags | SwiftUI counterpart |
| --------- | ------- | --- | --- | --- | ------- | --------------------- |
| `VALUE_CHANGED` | 0x06 | targetId | value (f32) | 0 | — | `Slider`/`Stepper`/`Gauge`/`Toggle`/`Picker`/`Menu` binding changes |

- **Semantics per control** (see [PRIMITIVES.md](./PRIMITIVES.md) controls):
  - `SLIDER`/`STEPPER`/`GAUGE` — the numeric value in the control's range.
  - `TOGGLE` — 0/1 (checked state; the same value the app writes to
    `SELECTED`).
  - `PICKER`/`MENU` — the selected option/action-item index.
  - `COLOR_PICKER` — the packed `0xAARRGGBB` color reinterpreted as an f32 bit
    pattern.
  - `DATE_PICKER` uses the dedicated `DATE_CHANGED` event below (its value is
    two fields and does not fit `VALUE_CHANGED`).
- The app writes the received value into its bound `State`; the engine's
  signal flush re-emits the `SET_PROPERTY` delta back to the renderer (the
  renderer reflects its own native control).
- **No listener bit**: value-bearing controls are reported by the renderer by
  virtue of their component type, but still gated by the transport-aware event
  guards (a control without `BINDING_ID` reports nothing). The renderer owns
  the gesture (e.g. slider drag) that produces them.

### Date

| Command | Value | A | B | C | Flags | SwiftUI counterpart |
| --------- | ------- | --- | --- | --- | ------- | --------------------- |
| `DATE_CHANGED` | 0x0D | targetId | days since epoch (I32) | millis of day (U32) | — | `DatePicker` value binding |

- The date is encoded as two 32-bit fields — `B` = **days since epoch**
  (signed I32; pre-1970 is negative) and `C` = **millis of day** (U32,
  0..86,400,000) — matching the `PARAMETER::SET_DATE` command (OPCODE.md). No
  string/arena indirection: the event is fully inline. It works over the
  shared-memory ring today and will use the same inline encoding for host →
  guest network batches once that direction lands (see OPCODE.md Transport).
- **No listener bit**: like `VALUE_CHANGED`, reported by virtue of the
  component type.

### Text

| Command | Value | A | B | C | Flags | SwiftUI counterpart |
| --------- | ------- | --- | --- | --- | ------- | --------------------- |
| `TEXT_CHANGED` | 0x07 | targetId | string offset | 0 | — | `TextField` value binding |
| `FOCUS_CHANGED` | 0x08 | targetId | focused (0/1) | 0 | — | `@FocusState` |
| `EDITING_CHANGED` | 0x09 | targetId | editing (0/1) | 0 | — | `TextField`/`Slider` `onEditingChanged` |
| `SUBMIT` | 0x0A | targetId | 0 | 0 | — | `TextField` `onSubmit` / `onCommit` |

- **`TEXT_CHANGED`**: the new text is a length-prefixed entry
  (`[u32 byteLength][bytes…]`). Over the **shared-memory ring** the renderer
  bump-allocates it into the host-owned **event arena** and `B` is its absolute
  offset (see OPCODE.md); over the **network** it lives in the batch's string
  section referenced by a *relative* `B` offset — exactly the `PARAMETER::SET_TEXT`
  dual convention. A bare opcode cannot resolve the text; batch-aware decoders
  (`pathland-core-transport`, Java `FrameCodec`, TS `decoder.ts`) resolve it.
  The event is reported on **every** edit, including intermediate edits, not
  only on commit.
- **`FOCUS_CHANGED`**: `B` = 1 gained focus, 0 lost focus. The renderer reports
  it for nodes that declared the `FOCUS` listener bit (bit 5). Focus is what
  routes subsequent `KEY_*` events.
- **`EDITING_CHANGED`**: `B` = 1 editing session began, 0 ended. Reported for
  nodes that declared the `EDITING` listener bit (bit 6).
- **`SUBMIT`**: the user committed the field (Enter). Reported for nodes that
  declared the `SUBMIT` listener bit (bit 7). `B` MAY carry the current text as
  a string-section offset (reserved for future use; 0 today).

### Scroll

| Command | Value | A | B | C | Flags | SwiftUI counterpart |
| --------- | ------- | --- | --- | --- | ------- | --------------------- |
| `SCROLL` | 0x0B | targetId | offsetX (f32) | offsetY (f32) | — | `ScrollView` offset, `onScrollPhaseChange` |
| `WHEEL` | 0x0C | targetId | deltaX (f32) | deltaY (f32) | — | `onScrollWheelEvent` / trackpad |

- **`SCROLL`**: the content offset of a `SCROLLVIEW` (logical points), reported
  for nodes that declared the `SCROLL` listener bit (bit 8). The renderer
  reports the absolute offset after each scroll delta.
- **`WHEEL`**: raw wheel/trackpad deltas (logical points), reported for nodes
  that declared the `WHEEL` listener bit (bit 9). Renderers map native scroll
  deltas onto x/y deltas.

### Navigation

| Command | Value | A | B | C | Flags | SwiftUI counterpart |
| --------- | ------- | --- | --- | --- | ------- | --------------------- |
| `NAVIGATE` | 0x0E | 0 | URL string offset | 0 | `NAVIGATE_URL` (0x0001) | browser back/forward (`popstate`), a deep link, or a native back affordance |

### Media

Media events report a `AUDIO`/`VIDEO` node's playback state back to the app
(target = the media node id). They are reported by the renderer by virtue of the
media node's component type — no listener bit, mirroring `VALUE_CHANGED` — and
only when the app has bound the corresponding control property (so a
renderer-native media element with default controls reports nothing).

| Command | Value | A | B | C | Flags | SwiftUI counterpart |
| --------- | ------- | --- | --- | --- | ------- | --------------------- |
| `MEDIA_PLAY_STATE_CHANGED` | 0x0F | targetId | playing (0/1) | 0 | — | `AVPlayer` play/pause |
| `MEDIA_TIME_UPDATED` | 0x10 | targetId | seconds (f32) | 0 | — | periodic time observer |
| `MEDIA_ENDED` | 0x11 | targetId | 0 | 0 | — | `AVPlayerItemDidPlayToEndTime` |
| `MEDIA_VOLUME_CHANGED` | 0x12 | targetId | volume (f32, 0..1) | 0 | — | `AVPlayer.volume` |

- **`MEDIA_TIME_UPDATED`**: the media's current playback position in seconds.
  Reported **when the playback position advances ~1 second** (at most ~1/s)
  while playing; a seek initiated by the app (`MEDIA_POSITION` change) is not
  echoed back.
- **Commands vs reports (MUST)**: `MEDIA_POSITION`/`MEDIA_VOLUME` are **seek /
  volume commands** the app writes to control playback; `MEDIA_TIME_UPDATED` /
  `MEDIA_VOLUME_CHANGED` are **position / volume reports** the renderer emits.
  An app **MUST NOT** bind a command property to the signal its matching report
  updates — that echoes every report back as a command (e.g. each second's
  position report re-emits a `MEDIA_POSITION` seek, causing per-second seek
  jitter). The app binds the command to a **user-interaction-only** signal and
  routes the report to a separate display signal.
- **Renderer echo guard (MUST)**: a renderer **MUST NOT** act on a command write
  that matches the value it last **reported** (within an epsilon): the app is
  echoing the report back, not interacting. E.g. a `MEDIA_POSITION` write within
  `0.25 s` of the last `MEDIA_TIME_UPDATED` must not seek; a `MEDIA_VOLUME`
  write within `0.01` of the last `MEDIA_VOLUME_CHANGED` must not re-apply. This
  is defense-in-depth: it holds at **any** network latency (the echo *is* the
  last-reported value), so a delayed echo cannot fight the playback position.
- **Guards**: media events flow only when the app bound the matching control
  property (`PLAYBACK_STATE`/`MEDIA_POSITION`/`MEDIA_VOLUME`) on the node; a
  plain renderer-native media element reports nothing.

- **`NAVIGATE` is a global event — never node-gated.** It carries **no
  `targetId`** (`A` = 0) and is **not** subject to `EVENT_LISTENERS` or the
  transport-aware event guards: the renderer/host emits it only when the
  application has registered a navigation handler (a `Router`), and it is
  delivered directly to that handler. See [DSL.md §4.5](./DSL.md#45-navigation).
- **With a URL** (`NAVIGATE_URL` flag set): `B` is a string-section / event-arena
  offset to the destination URL the platform navigated to — a browser
  `popstate` (back/forward), a deep link, or a renderer-originated URL. The
  host calls `router.handlePlatformNavigation(url)`, which matches and re-emits.
- **Without a URL** (flag clear): the platform back affordance (Android
  predictive-back, iOS swipe-back, a desktop back button) — the host calls
  `router.pop()`. The renderer never decides navigation; it only requests it.
- The string encoding follows the exact `TEXT_CHANGED` dual convention: over
  the **shared-memory ring** the renderer bump-allocates the URL into the
  host-owned event arena and `B` is its absolute offset; over the **network**
  it lives in the batch's string section referenced by a *relative* `B` offset.
  The `NAVIGATE_URL` flag distinguishes "URL at `B`" from "back one step" even
  when `B` would otherwise be a valid offset.

---

## Event listeners (`EVENT_LISTENERS`)

`EVENT_LISTENERS` (0x2005) is a u32 bitmask set via `SET_PROPERTY` (U32). Each
bit requests one raw-input event kind for the node:

| Bit | Mask | Event |
| ----- | ------ | ------- |
| 0 | `0x00000001` | `POINTER_DOWN` |
| 1 | `0x00000002` | `POINTER_MOVE` (incl. hover enter/leave) |
| 2 | `0x00000004` | `POINTER_UP` |
| 3 | `0x00000008` | `KEY_DOWN` |
| 4 | `0x00000010` | `KEY_UP` |
| 5 | `0x00000020` | `FOCUS_CHANGED` |
| 6 | `0x00000040` | `EDITING_CHANGED` |
| 7 | `0x00000080` | `SUBMIT` |
| 8 | `0x00000100` | `SCROLL` |
| 9 | `0x00000200` | `WHEEL` |
| 10–31 | — | reserved |

Convenience aliases (DSL-side): `POINTER = bits 0|1|2`.

DSL surfaces:

- Rust: `pathland_view::pointer_events(mask)` modifier + `listener::*` bits;
  `on_tap_gesture` sets `POINTER_DOWN | POINTER_UP`.
- Java: `TapGestureView` / `EVENT_LISTENERS` via the emitter; host forwards raw
  `EVENT` batches into bound `State` signals.

---

## Deferred: gestures

SwiftUI gestures — `onTapGesture`, `onLongPressGesture`, `DragGesture`,
`MagnifyGesture`, `RotateGesture` — are **not protocol events**. Per the
protocol's raw-inputs-only rule, they are **composed application-side** from
the raw `POINTER_*` / `KEY_*` events above:

- **Tap** = `POINTER_DOWN` then `POINTER_UP` on the same `targetId` within the
  platform's tap window.
- **Long-press** = `POINTER_DOWN` held past a threshold.
- **Drag** = `POINTER_DOWN` + `POINTER_MOVE` sequence.
- **Magnify / rotate** = multi-pointer deltas; the underlying raw events are
  `POINTER_*` (multi-touch reported per pointer).

A future "gesture recognition" layer MAY define app-side recognizers, but the
wire protocol will never carry interpreted gestures — only raw inputs.

---

## Reserved event command IDs

| Range | Use |
|-------|-----|
| `0x01`–`0x0E` | Core events (allocated, this file) |
| `0x0F`–`0x3F` | Future core events (unallocated) |
| `0x40`–`0xFF` | Custom/application events |

A renderer/decoder MUST skip unknown event commands and continue.

---

## Conformance

Event encode/decode round-trips and golden byte vectors (incl. raw-input
`EVENT` and network-batch bytes) live in [CONFORMANCE.md](./CONFORMANCE.md).
New events added here MUST add vectors before implementation.