# ADR-0006: Playback identity separate from media surfaces

**Status:** Accepted
**Recorded:** 2026-11-12 (extracted existing decision; original date not established)
**Scope:** Media, engine identity, structural reconciliation

## Context

The media specification documents that playback formerly belonged to AUDIO/VIDEO
view node IDs. Structural replacement destroyed playback, and controlling the
same playback from multiple places was inexpressible.

## Decision

Represent playback with one non-visual MEDIA_CONTROLLER for audio or video.
VIDEO surfaces reference that controller rather than owning its resource or
transport. Keep controller identity stable outside swapping branches. Audio has
no dedicated view; applications compose its controls from ordinary views.

## Rationale

The controller/surface split decouples playback lifetime from layout and
structural changes. Multiple surfaces or control locations can refer to the same
playback without reloading or duplicating it. A shared controller model reflects
the common transport needs of audio and video.

## Alternatives

The previous view-owned playback design is explicitly described and replaced in
the media introduction. Separate renderer-native audio controls are also
explicitly excluded; renderer-native video controls remain part of the contract.

## Consequences

Emitters must manage pinned identity and surface references. Renderers manage
playback independently of attached surfaces. Applications place controls freely
but must not accidentally couple controller lifetime to a replacing branch.

## Sources and current contract

- [Media model](../../spec/MEDIA.md#1-the-model).
- [Controller/surface independence](../../spec/MEDIA.md#24-independence-from-video-surfaces-must).
- [Engine fit, media, and navigation](../../spec/ENGINE.md#7-fit-media-navigation).
