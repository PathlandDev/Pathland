# Pathland Media Kit

**Wire protocol version:** 1
**Status:** Draft
**Last Updated:** October 17, 2026

---

## Introduction

Media playback in Pathland is split into two roles:

- a **media controller** ([`MEDIA_CONTROLLER`](./PRIMITIVES.md#media-controller--media_controller-0x30)
  `0x30`) — a **non-visual** node that owns a media resource and its transport
  state (source, play/pause, position, volume, …); and
- a **video surface** ([`VIDEO`](./PRIMITIVES.md#video--video-0x0a) `0x0A`) — the
  only media *view*: it references a controller and renders its video output.

**Audio has no view of its own.** Audio is non-visual: an audio playback is a
controller plus the application's **own** views (buttons, sliders, labels) bound
to the controller's transport signals. There are **no renderer-native audio
controls** — the application defines the entire control surface in code. The
former `AUDIO` component is therefore **removed**.

Before this document, playback identity *was* the media node id: the source and
the transport properties lived on the `AUDIO`/`VIDEO` node itself, and the media
events reported against it. That makes a **structural swap** of the media node —
the normal case under `SIZE_THAT_FITS`, conditional rendering, or any subtree
replacement — destroy the playback, and it makes "control the same playback from
two places" inexpressible.

The controller/surface split fixes both: playback identity is the **controller**
node id, which the application keeps **outside** the swapping branch, so a swap
re-attaches a view to a playback that never stopped — and for audio the swapping
thing is just the application's own control views.

There is **one** controller component for both audio and video — the transport is
the same thing in both cases, exactly as SwiftUI has one `AVPlayer` for both and
`VideoPlayer` is merely a view over it. **Kind is decided by the views**: a
controller with a `VIDEO` surface renders video; a controller with no media view
is audio.

This document specifies the media contract: the controller node, the video
surface binding, transport commands vs. reports, the event family, and the swap
rules. It adds **one component**, **five properties**, and **one event**, and
removes the `AUDIO` component — no new opcode category, and no change to the wire
format.

### Relationship to the other specs

| File | Role |
|------|------|
| [OPCODE.md](./OPCODE.md) | Wire format (16-byte opcode, categories, value types, `PLPL` batches) |
| [PRIMITIVES.md](./PRIMITIVES.md) | The `MEDIA_CONTROLLER` component and the `VIDEO` surface |
| [MODIFIERS.md](./MODIFIERS.md) | The media property ids and their value types |
| [EVENTS.md](./EVENTS.md) | The `MEDIA_*` event family, its guards, and the echo rules |
| [LAYOUT.md](./LAYOUT.md) | The controller's layout transparency and media content fitting |
| [CONFORMANCE.md](./CONFORMANCE.md) | Golden vectors for the controller component and the binding property |
| **MEDIA.md** (this file) | The end-to-end media contract |

---

## 1. The model

```
MEDIA_CONTROLLER 0x30            non-visual; owns the resource + transport
  MEDIA_SOURCE
  PLAYBACK_STATE / MEDIA_POSITION / MEDIA_VOLUME
  MEDIA_MUTED / MEDIA_LOOP / MEDIA_RATE
        ▲                                        ▲
        │ MEDIA_CONTROLLER_ID                    │ bound transport signals
        │                                        │
   VIDEO 0x0A                            application views
   (renders the video output)            (buttons/sliders/labels —
                                          the ENTIRE audio control surface)
```

- The **controller** is a node in the application's retained tree, but it is
  **layout-transparent** (§2.2): it occupies no space and renders no element.
- A **video surface** is an ordinary view node wherever the application wants it
  in the layout. It names a controller with `MEDIA_CONTROLLER_ID` (0x103A). It is
  **not** a child of the controller — the relationship is a **reference**, not a
  parent/child edge. The two nodes may live anywhere in the tree, and their
  relative position carries no meaning.
- **Audio has no view.** An audio playback is controlled by ordinary application
  views whose actions/values write the controller's transport properties. Nothing
  in the protocol renders audio.
- The application **SHOULD** place a controller at a stable position that no
  structural swap removes — e.g. a sibling of (or ancestor of) the `SIZE_THAT_FITS`
  slot, or a child of the root. A controller inside a swapping branch would be
  created and destroyed with that branch, defeating the split.

### 1.1 What lives where

| Concern | Owner |
|---------|-------|
| The media resource (source URL/asset) | controller — `MEDIA_SOURCE` |
| Transport state (play, seek, volume, mute, loop, rate) | controller |
| Transport **commands** from the app | controller (properties) |
| Transport **reports** to the app | controller (events target its id) |
| Whether a visual is shown, and its box/content mode | `VIDEO` surface |
| The video control UI (transport buttons, seek bar, volume) | `VIDEO` surface |
| The **audio** control UI (transport buttons, seek bar, volume) | the application's own views (no protocol surface) |
| Renderer-native vs. custom **video** controls | `VIDEO` dual mode (native when it has no children, the children when it has) |

---

## 2. The controller — `MEDIA_CONTROLLER` 0x30

### 2.1 Protocol

- A **leaf** node: it has no children. A `VIDEO` surface references it by id;
  it is not that surface's child.
- It carries the media resource and the transport properties (§2.3).
- It is created, updated, and deleted like any other node (`TREE` `CREATE_NODE` /
  `DELETE_NODE`), so it participates in the ordinary structural diff, in a
  full-snapshot (`META::RESYNC`), and in proxy-id remapping
  ([PROXY.md](./PROXY.md)) with no media-specific machinery.
- A controller is **not** a media surface and never renders a visual.

### 2.2 Layout (MUST)

A `MEDIA_CONTROLLER` is **layout-transparent**, like `COMMENT` and the
`PROXY_VIEW` passthrough ([LAYOUT.md](./LAYOUT.md)):

- It renders **no native element**.
- It occupies **no space**: it is excluded from its parent's allocation and
  contributes **no spacing** in a stack or grid (it is not a slot).
- It imposes no alignment, sizing, or content-mode behavior.

Its position in the tree is therefore semantically irrelevant; applications place
it for **lifetime stability**, not for layout.

### 2.3 Transport properties

| Property | Id | Type | Meaning |
|----------|----|------|---------|
| `MEDIA_SOURCE` | `0x103B` | STRING (arena ref) | The media resource: a resource name, file path, or absolute URL. An **asset reference**, never embedded in the opcode stream. Changing it re-loads the resource (and resets position to 0 unless a `MEDIA_POSITION` is applied in the same batch). |
| `PLAYBACK_STATE` | `0x1035` | U32 (0 pause, 1 play) | Transport **command**: play/pause. |
| `MEDIA_POSITION` | `0x1036` | F32 (seconds) | Seek **command**. A change seeks; it is **not** a continuous position feed (see §4.3). |
| `MEDIA_VOLUME` | `0x1037` | F32 (0..1) | Volume **command**. |
| `MEDIA_MUTED` | `0x103C` | U8 (0/1) | Mute **command**. Independent of `MEDIA_VOLUME` (the volume value is preserved). |
| `MEDIA_LOOP` | `0x103D` | U8 (0/1) | Loop the resource on end. When set, `MEDIA_ENDED` is still reported, and playback continues from 0. |
| `MEDIA_RATE` | `0x103E` | F32 | Playback rate (1.0 = normal). 0 is equivalent to pause. |

- A controller with **no** `MEDIA_SOURCE` is valid: it is idle and reports
  nothing. The renderer MUST NOT create a playback for it.
- `PLAYBACK_STATE`, `MEDIA_POSITION`, `MEDIA_VOLUME`, `MEDIA_MUTED`,
  `MEDIA_LOOP`, and `MEDIA_RATE` are **commands** the application writes.
  `MEDIA_SOURCE` is the resource binding.

### 2.4 Independence from video surfaces (MUST)

A controller's playback is **independent of its video surfaces**:

- Attaching a surface does **not** start, stop, seek, or re-load playback.
- Detaching (or destroying) a surface does **not** stop playback. A controller
  with **zero** surfaces is valid and keeps playing — the application decides
  when to pause (a `PLAYBACK_STATE` write).
- One controller = **one** playback. Two surfaces referencing the same
  controller share that single playback: a transport command from either affects
  both, and the reports describe both.

---

## 3. The video surface — `VIDEO` 0x0A

`VIDEO` is the **only** media view. It names its controller with
`MEDIA_CONTROLLER_ID` (0x103A, U32 — the controller node id) and renders that
playback's video output in its own box, fitted per `CONTENT_MODE` (`Fit`/`Fill`).
With no children it presents renderer-native video controls; with children (a
`VideoStyle`) the children are the control UI.

- **Audio has no surface.** The removed `AUDIO` component is not replaced: an
  audio playback is rendered by **nothing** in the protocol — the application's
  own views are the entire control surface (§1).
- **Multiplicity**: any number of `VIDEO` surfaces may reference one controller
  (the same playback shown in two places), and any number of controllers may
  coexist.
- **A surface carries no source.** `VIDEO_SOURCE` (0x1034) — like `AUDIO_SOURCE`
  (0x1033) — is **removed**; the source lives on the controller as `MEDIA_SOURCE`.
  A `VIDEO` with no `MEDIA_CONTROLLER_ID` renders blank; one whose controller id
  is **unknown** (not yet created, or deleted) renders blank and MUST NOT crash —
  consistent with the unknown-component rule ([PRIMITIVES.md](./PRIMITIVES.md)).
- A `VIDEO` whose controller has no `MEDIA_SOURCE` renders blank/idle.
- `VIDEO` is an ordinary layout citizen: its box, size modifiers,
  `CONTENT_MODE`, `CLIPS_TO_BOUNDS`, and style modifiers behave as for any view.

---

## 4. Events

The media event family reports a **controller's** playback state back to the
application. `A` = the **controller** node id (never a surface id).

| Command | Value | A | B | C | Flags |
|---------|-------|----|----|----|-------|
| `MEDIA_PLAY_STATE_CHANGED` | `0x0F` | controller id | playing (0/1) | 0 | — |
| `MEDIA_TIME_UPDATED` | `0x10` | controller id | seconds (f32) | 0 | — |
| `MEDIA_ENDED` | `0x11` | controller id | 0 | 0 | — |
| `MEDIA_VOLUME_CHANGED` | `0x12` | controller id | volume (f32, 0..1) | 0 | — |
| `MEDIA_DURATION_CHANGED` | `0x14` | controller id | seconds (f32) | 0 | — |

The full event table, encoding, and cadence live in
[EVENTS.md](./EVENTS.md#media).

### 4.1 Guards

- Media events flow **by virtue of the controller** — no `EVENT_LISTENERS` bit,
  mirroring `VALUE_CHANGED`.
- They flow **only when the application bound the matching control property on
  the controller**: `PLAYBACK_STATE` gates `MEDIA_PLAY_STATE_CHANGED` and
  `MEDIA_ENDED`; `MEDIA_POSITION` gates `MEDIA_TIME_UPDATED` and
  `MEDIA_DURATION_CHANGED`; `MEDIA_VOLUME` gates `MEDIA_VOLUME_CHANGED`. An
  unbound controller reports nothing (so a purely renderer-native playback stays
  silent).
- The gates are evaluated **on the controller**, not on the surface: binding a
  transport property on a surface has no effect.

### 4.2 Commands vs. reports (MUST)

`MEDIA_POSITION` / `MEDIA_VOLUME` are **commands**; `MEDIA_TIME_UPDATED` /
`MEDIA_DURATION_CHANGED` / `MEDIA_VOLUME_CHANGED` are **reports**. An application
MUST NOT bind a command property to the signal its matching report updates — that
echoes every report back as a command. Bind the command to a
**user-interaction-only** signal and route the report to a separate display
signal.

### 4.3 Renderer echo guard (MUST)

A renderer MUST NOT act on a command write that matches the value it last
**reported** (within an epsilon): the application is echoing the report back, not
interacting. A `MEDIA_POSITION` write within `0.25 s` of the last
`MEDIA_TIME_UPDATED` must not seek; a `MEDIA_VOLUME` write within `0.01` of the
last `MEDIA_VOLUME_CHANGED` must not re-apply. The guard is **per controller**.

---

## 5. Structural swaps (the motivating contract)

The reason the controller exists. Consider a `SIZE_THAT_FITS` slot whose
candidates are two different **control layouts for the same track** — a compact
player bar and a wide now-playing sidebar (ordinary application views: buttons, a
seek slider, labels). Both write the same controller's transport signals:

```
VStack
  MEDIA_CONTROLLER (id 9)        ← stable: outside the slot
  SIZE_THAT_FITS (id 4)          ← the swapping slot
    candidate 0: PlayerBar       → controls controller 9
    candidate 1: NowPlayingBar   → controls controller 9
```

(For video the same shape holds with a `VIDEO` surface inside each candidate,
referencing the same controller.)

On a band crossing the application swaps the slot's child: the renderer
`DELETE_NODE`s the outgoing subtree and `CREATE_NODE`s the incoming one. What
MUST hold:

1. **Playback continues.** The renderer MUST NOT stop, restart, re-load, or
   re-seek the controller's playback because a view was destroyed/created. Its
   current position, play state, volume, mute, loop, and rate are preserved.
2. **The event target is stable.** Reports continue to carry the controller id
   (9), so the application's bindings never see a "moving target" across a swap.
3. **Zero opcodes for the controller.** The swap emits `TREE` deltas for the
   swapped views only; the controller emits nothing (nothing changed about it).
4. **Nothing is keyed by the swapped node.** The renderer MUST NOT key playback
   by any node other than the controller id. Playback is keyed **by controller
   id** — one playback per controller, for the life of the controller node.

A controller that is itself destroyed (its `DELETE_NODE`) releases its playback.
Creating a new controller starts a new playback.

---

## 6. Renderer obligations

- **One playback per controller.** A renderer MUST map a controller id to a
  single playback (a native media element / player) and reuse it for the
  controller's whole lifetime, regardless of how many views reference it or
  how often they are swapped.
- **Never restart on view change.** Creating, deleting, or reordering the views
  that reference a controller — including a `VIDEO` surface — is not a transport
  event (§5).
- **Video controls** follow the ordinary dual-mode rule: a `VIDEO` with no
  children presents renderer-native video controls; with children the children
  are the control UI. **Audio has neither a visual nor controls** — a renderer
  MUST NOT synthesize any audio UI, native or otherwise; the application's own
  views are the entire control surface (§1).
- **Playback is renderer output state, not application state.** The controller's
  authoritative transport values are the application's (written as commands and
  reported back as events). The renderer holds its playback — the media element,
  its position, its buffering — as a cache of its own output, exactly as a DOM
  `<audio>`/`<video>` element does. It MUST NOT invent transport state the
  application did not ask for, and it MUST NOT treat the media element's internal
  state as the application's model (that is what the reports are for).
- **Unknown component ids** (including a `MEDIA_CONTROLLER` from a newer spec
  revision) follow the standard rule: render blank, keep decoding.

---

## 7. Future / drafts

Not part of this version; listed so the ids can be allocated spec-first:

- `MEDIA_BUFFERING` (F32 0..1) + `MEDIA_BUFFERING_CHANGED` — readiness.
- Track selection (`MEDIA_AUDIO_TRACK`, `MEDIA_SUBTITLE_TRACK`) — video-ish, but
  the transport is shared.
- Picture-in-picture / fullscreen — currently renderer-owned chrome.
- Gapless / crossfade — an audio-only transport extension.
- `POSTER_SOURCE` (on a `VIDEO` surface) — a preview frame before playback.

---

## Conformance

The controller component and the surface binding are byte-covered by the golden
vectors in [CONFORMANCE.md](./CONFORMANCE.md) (vectors 39–41): the
`MEDIA_CONTROLLER` `CREATE_NODE`, the `MEDIA_CONTROLLER_ID` binding, and the
`MEDIA_SOURCE` resource property.
