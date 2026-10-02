# Pathland Conformance Test Vectors

**Wire protocol version:** 1
**Encoding:** little-endian
**Opcode size:** 16 bytes

This document provides **golden byte arrays** for the Pathland opcode protocol (see [OPCODE.md](./OPCODE.md)). A conforming implementation MUST:

- encode each opcode to exactly these 16 bytes, and
- decode these bytes to the equivalent opcode.

The vectors are enforced by the reference Rust encoder (`pathland-core`,
`crates/pathland-core/src/conformance.rs`).

Companion catalogs: [PRIMITIVES.md](./PRIMITIVES.md) (views), [MODIFIERS.md](./MODIFIERS.md)
(modifiers/properties), [EVENTS.md](./EVENTS.md) (events), [TOKENS.md](./TOKENS.md)
(design tokens / theming). New IDs allocated there must gain vectors before
implementations depend on them.

## Opcode Layout

```
[Category: u8][Command: u8][Flags: u16][A: u32][B: u32][C: u32]
```

All multi-byte fields little-endian. `A`/`B`/`C` may carry `f32` bit patterns.

## Vectors

### 1. TREE:CREATE_NODE (id=1, VSTACK=0x0010)

```
01 01 00 00 01 00 00 00 10 00 00 00 00 00 00 00
```

- `01` category = TREE
- `01` command = CREATE_NODE
- `00 00` flags = 0
- `01 00 00 00` A = nodeId = 1
- `10 00 00 00` B = componentType = 0x0010 (VSTACK)
- `00 00 00 00` C = 0

### 2. TREE:INSERT_CHILD (parent=0, child=1, index=APPEND)

```
01 03 00 00 00 00 00 00 01 00 00 00 FF FF FF FF
```

- `01` category = TREE
- `03` command = INSERT_CHILD
- `00 00` flags = 0
- `00 00 00 00` A = parentId = 0
- `01 00 00 00` B = childId = 1
- `FF FF FF FF` C = APPEND (`u32::MAX`)

### 3. TREE:MOVE_CHILD (parent=1, child=2, newIndex=0)

```
01 05 00 00 01 00 00 00 02 00 00 00 00 00 00 00
```

- `01` category = TREE
- `05` command = MOVE_CHILD
- `00 00` flags = 0
- `01 00 00 00` A = parentId = 1
- `02 00 00 00` B = childId = 2
- `00 00 00 00` C = newIndex = 0

### 4. PARAMETER:SET_PROPERTY (id=1, propertyId=SPACING=0x0001, valueType=F32, 4.0)

```
02 01 00 00 01 00 00 00 01 00 04 00 00 00 80 40
```

- `02` category = PARAMETER
- `01` command = SET_PROPERTY
- `00 00` flags = 0
- `01 00 00 00` A = nodeId = 1
- `01 00 04 00` B = `(valueType << 16) | propertyId` = `(0x04 << 16) | 0x0001`
  - low two bytes `01 00` = propertyId = 0x0001 (SPACING)
  - high byte `04` = valueType = 0x04 (F32)
- `00 00 80 40` C = 4.0 (f32 LE: 0x40800000)

### 5. PARAMETER:SET_PROPERTY (id=1, propertyId=COLOR=0x100A, valueType=COLOR=0x07, rgba=0xFF0000FF)

```
02 01 00 00 01 00 00 00 0A 10 07 00 FF 00 00 FF
```

- `02` category = PARAMETER
- `01` command = SET_PROPERTY
- `00 00` flags = 0
- `01 00 00 00` A = nodeId = 1
- `0A 10 07 00` B = `(valueType << 16) | propertyId` = `(0x07 << 16) | 0x100A`
  - low two bytes `0A 10` = propertyId = 0x100A (COLOR)
  - high byte `07` = valueType = 0x07 (COLOR)
- `FF 00 00 FF` C = 0xFF0000FF (opaque blue, `0xAARRGGBB`)

### 6. PARAMETER:SET_TEXT (id=1, arenaRef=0)

```
02 03 00 00 01 00 00 00 00 00 00 00 00 00 00 00
```

- `02` category = PARAMETER
- `03` command = SET_TEXT
- `00 00` flags = 0
- `01 00 00 00` A = nodeId = 1
- `00 00 00 00` B = arenaRef = 0 (entry is `[u32 length][bytes]` in the arena)
- `00 00 00 00` C = 0

### 7. META:RESET

```
04 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00
```

- `04` category = META
- `01` command = RESET
- `00 00` flags = 0
- `00 00 00 00` A = 0
- `00 00 00 00` B = 0
- `00 00 00 00` C = 0

### 8. EVENT:POINTER_DOWN (target=5, x=10.0, y=20.0)

```
03 01 00 00 05 00 00 00 00 00 20 41 00 00 A0 41
```

- `03` category = EVENT
- `01` command = POINTER_DOWN
- `00 00` flags = 0
- `05 00 00 00` A = targetId = 5
- `00 00 20 41` B = 10.0 (f32 LE: 0x41200000)
- `00 00 A0 41` C = 20.0 (f32 LE: 0x41A00000)

### 9. EVENT:POINTER_MOVE (target=5, x=10.0, y=20.0, hovering)

```
03 02 01 00 05 00 00 00 00 00 20 41 00 00 A0 41
```

- `03` category = EVENT
- `02` command = POINTER_MOVE
- `01 00` flags = 0x0001 (`HOVER_ENTER`)
- `05 00 00 00` A = targetId = 5
- `00 00 20 41` B = 10.0 (f32 LE: 0x41200000)
- `00 00 A0 41` C = 20.0 (f32 LE: 0x41A00000)

### 10. EVENT:POINTER_UP (target=5, x=10.0, y=20.0)

```
03 03 00 00 05 00 00 00 00 00 20 41 00 00 A0 41
```

- `03` category = EVENT
- `03` command = POINTER_UP
- `00 00` flags = 0
- `05 00 00 00` A = targetId = 5
- `00 00 20 41` B = 10.0 (f32 LE: 0x41200000)
- `00 00 A0 41` C = 20.0 (f32 LE: 0x41A00000)

### 11. EVENT:KEY_DOWN (target=5, keyCode='A'=0x41, modifiers=0x01, repeat)

```
03 04 02 00 05 00 00 00 41 00 00 00 01 00 00 00
```

- `03` category = EVENT
- `04` command = KEY_DOWN
- `02 00` flags = 0x0002 (`KEY_REPEAT`)
- `05 00 00 00` A = targetId = 5
- `41 00 00 00` B = keyCode = 0x41 (`'A'`, low u16)
- `01 00 00 00` C = modifiers = 0x01 (low u8)

### 12. EVENT:KEY_UP (target=5, keyCode='A'=0x41, modifiers=0x01)

```
03 05 00 00 05 00 00 00 41 00 00 00 01 00 00 00
```

- `03` category = EVENT
- `05` command = KEY_UP
- `00 00` flags = 0
- `05 00 00 00` A = targetId = 5
- `41 00 00 00` B = keyCode = 0x41 (`'A'`, low u16)
- `01 00 00 00` C = modifiers = 0x01 (low u8)

### 13. META:RESYNC

```
04 03 00 00 00 00 00 00 00 00 00 00 00 00 00 00
```

- `04` category = META
- `03` command = RESYNC (host → guest: request a full snapshot)
- `00 00` flags = 0
- `00 00 00 00` A = 0
- `00 00 00 00` B = 0
- `00 00 00 00` C = 0

---

## Directions

- `TREE` / `PARAMETER` are **guest → host** (application → renderer).
- `EVENT` is **host → guest** (renderer → application): raw pointer/keyboard
  inputs with a host-resolved `targetId`. A conforming EVENT encoder/decoder
  MUST reproduce vectors 8–12 and 21–22 exactly.
- `META` is both directions.

## Frame Conformance

A complete frame is: delta opcodes written into consecutive ring slots, then a
`frameCount` increment in the header block. Ring slot `i` occupies bytes
`[ringOffset + i*16, ringOffset + i*16 + 16)`.

The header block layout and ring semantics are defined in [OPCODE.md](./OPCODE.md#linear-memory-layout).

The host → guest event ring shares the same 16-byte slot format and lives at
`eventRingOffset` (header `0x2A`), with `eventReadCursor` (0x36, guest-owned)
and `eventWriteCursor` (0x3A, host-owned). A host writing `EVENT:POINTER_UP`
into the event ring must reproduce the exact vector-11 bytes in the slot at
`eventWriteCursor`, then advance `eventWriteCursor`; the guest advances
`eventReadCursor` after draining.

String-bearing events (`TEXT_CHANGED`, `NAVIGATE` with the `NAVIGATE_URL`
flag) resolve their text from the host → guest **event arena** (header
`eventArenaOffset` 0x40 / `eventArenaCursor` 0x48, host-owned, shared memory)
or the batch's string section (network) — the same dual absolute/relative
convention as `PARAMETER::SET_TEXT`. See OPCODE.md's
[event arena](./OPCODE.md#event-arena-host--guest) section.

## Network Batch Conformance

A network batch serializes opcodes + an arena delta (see
[OPCODE.md](./OPCODE.md#transport)). A conforming batch encoder/decoder MUST
reproduce the following bytes exactly.

### 14. NETWORK:BATCH (frameCount=1, one CREATE_NODE, arena delta = "Hi")

The single opcode is `TREE:CREATE_NODE (id=1, VSTACK)` from vector 1. The arena
delta is the self-describing entry for `"Hi"`: `[02 00 00 00][48 69]`.

```
4C 50 4C 50 01 00 00 00 01 00 00 00 01 00 00 00
01 01 00 00 01 00 00 00 10 00 00 00 00 00 00 00
06 00 00 00 02 00 00 00 48 69
```

- `4C 50 4C 50` = magic `PLPL` (u32 `0x504C504C`, little-endian)
- `01 00` = version 1
- `00 00` = flags (guest → host)
- `01 00 00 00` = frameCount = 1
- `01 00 00 00` = opcodeCount = 1
- `01 01 00 00 01 00 00 00 10 00 00 00 00 00 00 00` = the CREATE_NODE opcode (16 bytes)
- `06 00 00 00` = arenaDeltaLen = 6
- `02 00 00 00 48 69` = arena entry `[len=2]["Hi"]`

### 15. NETWORK:BATCH (frameCount=2, empty opcode list, empty arena delta)

```
4C 50 4C 50 01 00 00 00 02 00 00 00 00 00 00 00
00 00 00 00
```

- `4C 50 4C 50` = magic `PLPL` (u32 `0x504C504C`, little-endian)
- `01 00` = version 1
- `00 00` = flags
- `02 00 00 00` = frameCount = 2
- `00 00 00 00` = opcodeCount = 0
- `00 00 00 00` = arenaDeltaLen = 0

### 16. NETWORK:BATCH (META:RESYNC request, host → guest)

The single opcode is `META:RESYNC` (vector 13). The batch carries the
`HOST_TO_GUEST` direction flag and an empty arena delta.

```
4C 50 4C 50 01 00 01 00 00 00 00 00 01 00 00 00
04 03 00 00 00 00 00 00 00 00 00 00 00 00 00 00
00 00 00 00
```

- `4C 50 4C 50` = magic `PLPL` (u32 `0x504C504C`, little-endian)
- `01 00` = version 1
- `01 00` = flags = `HOST_TO_GUEST` (0x0001)
- `00 00 00 00` = frameCount = 0
- `01 00 00 00` = opcodeCount = 1
- `04 03 00 00 00 00 00 00 00 00 00 00 00 00 00 00` = the META:RESYNC opcode (16 bytes)
- `00 00 00 00` = arenaDeltaLen = 0

### 17. PARAMETER:SET_DESIGN_TOKEN (path="color.primary" arenaRef=0, valueType=COLOR=0x07, value=0xFF0000FF)

```
02 02 00 00 00 00 00 00 07 00 00 00 FF 00 00 FF
```

- `02` category = PARAMETER
- `02` command = SET_DESIGN_TOKEN
- `00 00` flags = 0
- `00 00 00 00` A = arenaRef = 0 (entry is `[u32 length][bytes]` in the arena; the string is `"color.primary"`)
- `07 00 00 00` B = valueType = 0x07 (COLOR)
- `FF 00 00 FF` C = 0xFF0000FF (opaque blue, `0xAARRGGBB`)

### 18. PARAMETER:SET_PROPERTY (id=1, propertyId=COLOR=0x100A, valueType=DESIGN_TOKEN=0x08, arenaRef=0)

A color property carrying a **token reference** instead of a literal: the
renderer resolves `"color.primary"` (arena entry at offset 0) against the token
tables and the current scheme.

```
02 01 00 00 01 00 00 00 0A 10 08 00 00 00 00 00
```

- `02` category = PARAMETER
- `01` command = SET_PROPERTY
- `00 00` flags = 0
- `01 00 00 00` A = nodeId = 1
- `0A 10 08 00` B = `(valueType << 16) | propertyId` = `(0x08 << 16) | 0x100A`
  - low two bytes `0A 10` = propertyId = 0x100A (COLOR)
  - high byte `08` = valueType = 0x08 (DESIGN_TOKEN)
- `00 00 00 00` C = arenaRef = 0 (`"color.primary"`)

### 19. NETWORK:BATCH (frameCount=3, one DESIGN_TOKEN-referencing SET_PROPERTY, arena delta = "color.primary")

The single opcode is vector 18. The arena delta is the self-describing entry for
`"color.primary"`: `[0D 00 00 00][63 6F 6C 6F 72 2E 70 72 69 6D 61 72 79]`.

```
4C 50 4C 50 01 00 00 00 03 00 00 00 01 00 00 00
02 01 00 00 01 00 00 00 0A 10 08 00 00 00 00 00
11 00 00 00 0D 00 00 00 63 6F 6C 6F 72 2E 70 72 69 6D 61 72 79
```

- `4C 50 4C 50` = magic `PLPL` (u32 `0x504C504C`, little-endian)
- `01 00` = version 1
- `00 00` = flags (guest → host)
- `03 00 00 00` = frameCount = 3
- `01 00 00 00` = opcodeCount = 1
- `02 01 00 00 01 00 00 00 0A 10 08 00 00 00 00 00` = the SET_PROPERTY opcode (16 bytes)
- `11 00 00 00` = arenaDeltaLen = 17
- `0D 00 00 00 63 6F 6C 6F 72 2E 70 72 69 6D 61 72 79` = arena entry `[len=13]["color.primary"]`

### 20. PARAMETER:SET_DESIGN_TOKEN (path="font.body.family" arenaRef=0, valueType=STRING=0x05, valueArenaRef=20)

A **STRING-valued** token override: the token path lives at arena offset 0
(`[len=16]["font.body.family"]`, 20 bytes), the value string `"Inter"` at
offset 20.

```
02 02 00 00 00 00 00 00 05 00 00 00 14 00 00 00
```

- `02` category = PARAMETER
- `02` command = SET_DESIGN_TOKEN
- `00 00` flags = 0
- `00 00 00 00` A = arenaRef = 0 (token path `"font.body.family"`)
- `05 00 00 00` B = valueType = 0x05 (STRING)
- `14 00 00 00` C = arenaRef = 20 (value string `"Inter"`)

### 21. EVENT:NAVIGATE (URL="/users/42" arenaRef=0, NAVIGATE_URL flag)

A **global** navigation request with a URL: the renderer/host reports the
browser moved to a URL (`popstate`/back/forward/deep-link). The URL is a
length-prefixed entry in the event arena (shared memory) or the batch's string
section (network) — the same dual convention as `TEXT_CHANGED`.

```
03 0E 01 00 00 00 00 00 00 00 00 00 00 00 00 00
```

- `03` category = EVENT
- `0E` command = NAVIGATE
- `01 00` flags = 0x0001 (`NAVIGATE_URL`)
- `00 00 00 00` A = 0 (global — no `targetId`, never node-keyed)
- `00 00 00 00` B = URL string offset = 0 (entry is `[u32 length][bytes]`; `"/users/42"`)
- `00 00 00 00` C = 0

### 22. EVENT:NAVIGATE (back, no URL)

A global **back** request (Android predictive-back, iOS swipe-back, a desktop
back button): no `NAVIGATE_URL` flag, so `B` is ignored and the host calls
`router.pop()`.

```
03 0E 00 00 00 00 00 00 00 00 00 00 00 00 00 00
```

- `03` category = EVENT
- `0E` command = NAVIGATE
- `00 00` flags = 0 (no URL — back one step)
- `00 00 00 00` A = 0
- `00 00 00 00` B = 0
- `00 00 00 00` C = 0

### 23. EVENT:MEDIA_PLAY_STATE_CHANGED (target=7, playing=1)

The app-bound audio/video node started playing.

```
03 0F 00 00 07 00 00 00 01 00 00 00 00 00 00 00
```

- `03` category = EVENT
- `0F` command = MEDIA_PLAY_STATE_CHANGED
- `00 00` flags = 0
- `07 00 00 00` A = targetId = 7 (the media node)
- `01 00 00 00` B = playing = 1
- `00 00 00 00` C = 0

### 24. EVENT:MEDIA_TIME_UPDATED (target=7, seconds=12.5)

The media's current playback position (seconds, f32).

```
03 10 00 00 07 00 00 00 00 00 48 41 00 00 00 00
```

- `03` category = EVENT
- `10` command = MEDIA_TIME_UPDATED
- `07 00 00 00` A = targetId = 7
- `00 00 48 41` B = 12.5f (0x41480000 LE)
- `00 00 00 00` C = 0

### 25. EVENT:MEDIA_ENDED (target=7)

The media reached its end.

```
03 11 00 00 07 00 00 00 00 00 00 00 00 00 00 00
```

- `03` category = EVENT
- `11` command = MEDIA_ENDED
- `07 00 00 00` A = targetId = 7
- `00 00 00 00` B = 0
- `00 00 00 00` C = 0

### 26. EVENT:MEDIA_VOLUME_CHANGED (target=7, volume=0.5)

The user changed the media volume (0..1, f32).

```
03 12 00 00 07 00 00 00 00 00 00 3F 00 00 00 00
```

- `03` category = EVENT
- `12` command = MEDIA_VOLUME_CHANGED
- `07 00 00 00` A = targetId = 7
- `00 00 00 3F` B = 0.5f (0x3F000000 LE)
- `00 00 00 00` C = 0

### 27. PARAMETER:SET_PROPERTY (id=1, propertyId=ROUTE=0x2019, valueType=STRING=0x05, arenaRef=0)

The `NavigationContainer` emits the current path as a STRING property; the DOM
renderer reacts with `history.pushState`.

```
02 01 00 00 01 00 00 00 19 20 05 00 00 00 00 00
```

- `02` category = PARAMETER
- `01` command = SET_PROPERTY
- `00 00` flags = 0
- `01 00 00 00` A = nodeId = 1
- `19 20 05 00` B = `(valueType << 16) | propertyId` = `(0x05 << 16) | 0x2019`
  - low two bytes `19 20` = propertyId = 0x2019 (ROUTE)
  - high byte `05` = valueType = 0x05 (STRING)
- `00 00 00 00` C = arenaRef = 0 (`"/users/42"`)

### 28. PARAMETER:SET_PROPERTY (id=1, propertyId=TRANSITION=0x1031, valueType=F32=0x04, Slide=3)

A transition hint on the slot (enum code carried as an `F32` bit pattern):
`Slide`=3 → `C` = 3.0 (0x40400000).

```
02 01 00 00 01 00 00 00 31 10 04 00 00 00 40 40
```

- `02` category = PARAMETER
- `01` command = SET_PROPERTY
- `00 00` flags = 0
- `01 00 00 00` A = nodeId = 1
- `31 10 04 00` B = `(valueType << 16) | propertyId` = `(0x04 << 16) | 0x1031`
  - low two bytes `31 10` = propertyId = 0x1031 (TRANSITION)
  - high byte `04` = valueType = 0x04 (F32)
- `00 00 40 40` C = 3.0 (f32 LE: 0x40400000) = `Slide`

### 29. Structural swap (slot 4: delete old destination 5, create new destination 6, insert)

A `NavigationContainer` (slot id 4) whose child changes from destination 5 to a
new destination 6 — the frame of `TREE` deltas a reconcile emits:

```
01 02 00 00 05 00 00 00 00 00 00 00 00 00 00 00
01 01 00 00 06 00 00 00 10 00 00 00 00 00 00 00
01 03 00 00 04 00 00 00 06 00 00 00 FF FF FF FF
```

- `01 02 00 00 05 00 00 00 00 00 00 00 00 00 00 00` = `TREE:DELETE_NODE` (id=5)
  — the old destination subtree root
- `01 01 00 00 06 00 00 00 10 00 00 00 00 00 00 00` = `TREE:CREATE_NODE`
  (id=6, VSTACK) — the new destination root
- `01 03 00 00 04 00 00 00 06 00 00 00 FF FF FF FF` = `TREE:INSERT_CHILD`
  (parent=4, child=6, APPEND)

An identical recompute (same structure, same ids) emits **zero** opcodes — the
reconcile is a diff.

### 30. META:ENVIRONMENT (VIEWPORT_WIDTH=0x0001, width=800.0)

An environment field opcode: `A` = field id, `B` = the field value. `800.0`
(f32 LE: 0x44480000).

```
04 02 00 00 01 00 00 00 00 00 48 44 00 00 00 00
```

- `04` category = META
- `02` command = ENVIRONMENT
- `00 00` flags = 0
- `01 00 00 00` A = fieldId = 0x0001 (VIEWPORT_WIDTH)
- `00 00 48 44` B = 800.0 (f32 LE: 0x44480000)
- `00 00 00 00` C = 0

### 31. META:ENVIRONMENT (ROUTE=0x0003, string offset 0)

A STRING-valued environment field: `B` is a string-section/event-arena offset
(the dual convention of `TEXT_CHANGED`/`NAVIGATE`) to the initial route —
`"/users/42"`.

```
04 02 00 00 03 00 00 00 00 00 00 00 00 00 00 00
```

- `04` category = META
- `02` command = ENVIRONMENT
- `00 00` flags = 0
- `03 00 00 00` A = fieldId = 0x0003 (ROUTE)
- `00 00 00 00` B = string offset = 0 (`"/users/42"`)
- `00 00 00 00` C = 0

### 32. PARAMETER:SET_PROPERTY (id=1, propertyId=NAV_DEPTH=0x201A, valueType=U32=0x02, depth=3)

The `NavigationContainer` emits its back-stack depth as a U32 property — three
destinations in the app's path (the current one included), so a native
navigation adapter reconciles its page stack by depth. `C` = 3 (U32 LE).

```
02 01 00 00 01 00 00 00 1A 20 02 00 03 00 00 00
```

- `02` category = PARAMETER
- `01` command = SET_PROPERTY
- `00 00` flags = 0
- `01 00 00 00` A = nodeId = 1
- `1A 20 02 00` B = `(valueType << 16) | propertyId` = `(0x02 << 16) | 0x201A`
  - low two bytes `1A 20` = propertyId = 0x201A (NAV_DEPTH)
  - high byte `02` = valueType = 0x02 (U32)
- `03 00 00 00` C = 3 (U32 LE) = depth

### 33. PARAMETER:SET_PROPERTY (id=1, propertyId=NAV_CHROME=0x201B, valueType=F32=0x04, Custom=1)

The `NavigationContainer`'s chrome mode (F32 enum code): `Custom`=1 → `C` =
1.0 (0x3F800000) — the developer owns all navigation UI and the renderer adds
none. `PlatformDefault`=0 is the missing/default value.

```
02 01 00 00 01 00 00 00 1B 20 04 00 00 00 80 3F
```

- `02` category = PARAMETER
- `01` command = SET_PROPERTY
- `00 00` flags = 0
- `01 00 00 00` A = nodeId = 1
- `1B 20 04 00` B = `(valueType << 16) | propertyId` = `(0x04 << 16) | 0x201B`
  - low two bytes `1B 20` = propertyId = 0x201B (NAV_CHROME)
  - high byte `04` = valueType = 0x04 (F32)
- `00 00 80 3F` C = 1.0 (f32 LE: 0x3F800000) = Custom

### 34. PARAMETER:SET_PROPERTY (id=1, propertyId=GRID_COLUMNS=0x001E, valueType=F32=0x04, 2.0)

A grid's column count (positive = count; `FILL`/absent = auto-fit). The count is
a **constructor property**, never a pixel width — the grid's box comes from
`WIDTH`/`HEIGHT`.

```
02 01 00 00 01 00 00 00 1E 00 04 00 00 00 00 40
```

- `02` category = PARAMETER
- `01` command = SET_PROPERTY
- `00 00` flags = 0
- `01 00 00 00` A = nodeId = 1
- `1E 00 04 00` B = `(valueType << 16) | propertyId` = `(0x04 << 16) | 0x001E`
  - low two bytes `1E 00` = propertyId = 0x001E (GRID_COLUMNS)
  - high byte `04` = valueType = 0x04 (F32)
- `00 00 00 40` C = 2.0 (f32 LE: 0x40000000) = two columns

### 35. TREE:CREATE_NODE (id=1, GRID_ROW=0x001D)

An explicit row grouping for a `GRID`: a grid child whose children are one row's
cells (the SwiftUI `GridRow` surface). Structural only — renders nothing outside
a `GRID`.

```
01 01 00 00 01 00 00 00 1D 00 00 00 00 00 00 00
```

- `01` category = TREE
- `01` command = CREATE_NODE
- `00 00` flags = 0
- `01 00 00 00` A = nodeId = 1
- `1D 00 00 00` B = componentType = 0x001D (GRID_ROW)
- `00 00 00 00` C = 0

### 36. PARAMETER:SET_PROPERTY (id=1, propertyId=GRID_TRACKS=0x0020, valueType=STRING=0x05, arenaRef=0)

The grid's per-track spec (`"flex,fixed:80,adaptive:50"`), a STRING property
referenced by an arena offset — the same convention as `ROUTE`.

```
02 01 00 00 01 00 00 00 20 00 05 00 00 00 00 00
```

- `02` category = PARAMETER
- `01` command = SET_PROPERTY
- `00 00` flags = 0
- `01 00 00 00` A = nodeId = 1
- `20 00 05 00` B = `(valueType << 16) | propertyId` = `(0x05 << 16) | 0x0020`
  - low two bytes `20 00` = propertyId = 0x0020 (GRID_TRACKS)
  - high byte `05` = valueType = 0x05 (STRING)
- `00 00 00 00` C = arenaRef = 0 (`"flex,fixed:80,adaptive:50"`)

### 37. PARAMETER:SET_PROPERTY (id=1, propertyId=ALIGNMENT=0x0002, valueType=F32=0x04, topCenter=3)

A `ZStack`/grid's 2D position code: `3` = horizontal center × vertical start
(`top-center`). Stacks read a single axis of the same code (`0/1/2`).

```
02 01 00 00 01 00 00 00 02 00 04 00 00 00 40 40
```

- `02` category = PARAMETER
- `01` command = SET_PROPERTY
- `00 00` flags = 0
- `01 00 00 00` A = nodeId = 1
- `02 00 04 00` B = `(valueType << 16) | propertyId` = `(0x04 << 16) | 0x0002`
  - low two bytes `02 00` = propertyId = 0x0002 (ALIGNMENT)
  - high byte `04` = valueType = 0x04 (F32)
- `00 00 40 40` C = 3.0 (f32 LE: 0x40400000) = topCenter

---

## Design-Token Resolution Conformance

The token system (catalog, override/default resolution, `dark.` color-scheme
layer) is defined in [TOKENS.md](./TOKENS.md). Resolution is a renderer behavior
and is not byte-encodable; a conforming renderer MUST produce these outcomes.

Given the overrides:

| Token override (via `SET_DESIGN_TOKEN`) | Value |
|------------------------------------------|-------|
| `color.primary` | `0xFF2563EB` |
| `dark.color.primary` | `0xFF60A5FA` |
| `space.base` | `4.0` (F32) |

| Query | Active scheme | Resolves to | Rationale |
|-------|---------------|-------------|-----------|
| `color.primary` | dark | `0xFF60A5FA` | `dark.color.primary` override wins (dark layer) |
| `color.primary` | light | `0xFF2563EB` | dark layer skipped; base override wins |
| `color.primary` | dark, no `dark.*` override | base override `0xFF2563EB` | base override falls through when the dark layer is empty |
| `font.body.size` | either | renderer default | no override; defaults are renderer-owned |
| `font.body.size` | either, override only `font.body` | the `font.body` override | parent-path fallback |
| `space.2` | either | `8.0` | generative family: `space.base` (4.0) × 2 |
| `space.0.5` | either | `2.0` | generative family: fractional leaf |
| `space.2` | either, `space.base` not overridden | renderer default × 2 | generative base falls back to renderer defaults |

A property carrying the `DESIGN_TOKEN` value type (vector 18) resolves through
the same algorithm and MUST re-resolve when the active scheme changes.
