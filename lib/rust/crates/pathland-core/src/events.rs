//! Typed raw-input events (host → guest).
//!
//! Events are the **raw inputs** the renderer reports: pointer down/move/up and
//! key down/up, each carrying a host-resolved `targetId`. High-level
//! interpretations (tap, drag, long-press) are built by the guest/app from
//! these raw inputs and are NOT part of the protocol.
//!
//! Encode a raw input with `Event::encode`, decode a ring opcode with
//! `Event::try_from`.

use alloc::string::String;
use alloc::string::ToString;
use crate::category;
use crate::flag;
use crate::Opcode;

/// A raw-input event (host → guest).
///
/// `Event` is **not** `Copy`: [`Event::TextChanged`] owns the new field text
/// and [`Event::Navigate`] owns an optional URL. Simple events round-trip
/// through `encode`/`try_from` as pure 16-byte opcodes; `TextChanged` and
/// `Navigate` with a URL are encoded/decoded at the batch level (their strings
/// live in the string section / event arena, referenced by a relative/absolute
/// offset).
#[derive(Debug, Clone, PartialEq)]
pub enum Event {
    /// Pointer button pressed at a viewport-relative point.
    PointerDown {
        target: u32,
        x: f32,
        y: f32,
        secondary: bool,
    },
    /// Pointer moved; optionally entered/left the node (hover).
    PointerMove {
        target: u32,
        x: f32,
        y: f32,
        hovering: bool,
        leaving: bool,
    },
    /// Pointer button released at a viewport-relative point.
    PointerUp {
        target: u32,
        x: f32,
        y: f32,
        secondary: bool,
    },
    /// Key pressed (possibly an auto-repeat).
    KeyDown {
        target: u32,
        key_code: u16,
        modifiers: u8,
        repeat: bool,
    },
    /// Key released.
    KeyUp {
        target: u32,
        key_code: u16,
        modifiers: u8,
    },
    /// A control's value changed (host → guest). The renderer resolves the
    /// semantic value (e.g. a slider thumb dragged along its track) and reports
    /// it here; the guest/app writes it into its bound state.
    ValueChanged {
        target: u32,
        value: f32,
    },
    /// A text field's value changed (host → guest). The new text is resolved
    /// from the batch's string section (see the doc comment on this enum).
    TextChanged {
        target: u32,
        value: String,
    },
    /// A node gained/lost focus (host → guest). Draft command 0x08.
    FocusChanged { target: u32, focused: bool },
    /// An editing session began/ended (host → guest). Draft command 0x09.
    EditingChanged { target: u32, editing: bool },
    /// The user committed a field (host → guest). Draft command 0x0A.
    Submit { target: u32 },
    /// A scroll container's offset changed (host → guest). Draft command 0x0B.
    Scroll {
        target: u32,
        offset_x: f32,
        offset_y: f32,
    },
    /// Raw wheel/trackpad deltas (host → guest). Draft command 0x0C.
    Wheel {
        target: u32,
        delta_x: f32,
        delta_y: f32,
    },
    /// A `DATE_PICKER`'s value changed (host → guest). Draft command 0x0D;
    /// `days` is I32 (pre-1970 negative), `millis` is millis of day.
    DateChanged {
        target: u32,
        days: i32,
        millis: u32,
    },
    /// A global navigation request (host → guest). Draft command 0x0E; never
    /// node-keyed. `url: Some(...)` carries the destination URL the platform
    /// navigated to (browser `popstate`/back/forward/deep-link), resolved from
    /// the string section / event arena; `url: None` is a native back request.
    Navigate { url: Option<String> },
    /// A bound `AUDIO`/`VIDEO` node's playback state changed (host → guest).
    /// Draft command 0x0F; reported only when `PLAYBACK_STATE` is bound.
    MediaPlayStateChanged { target: u32, playing: bool },
    /// A bound media node's current playback position (host → guest). Draft
    /// command 0x10; `seconds` is the position in seconds (f32), reported while
    /// playing. An app-initiated seek (`MEDIA_POSITION` change) is not echoed.
    MediaTimeUpdated { target: u32, seconds: f32 },
    /// A bound media node reached the end of its media (host → guest). Draft
    /// command 0x11.
    MediaEnded { target: u32 },
    /// A bound media node's volume changed (host → guest). Draft command 0x12;
    /// `volume` is in the range 0..1 (f32).
    MediaVolumeChanged { target: u32, volume: f32 },
    /// A `SIZE_THAT_FITS` slot's selected candidate changed (host → guest).
    /// Draft command 0x13; `index` is the derived fit index (f32), reported once
    /// after the first measure and then only when it changes.
    FitChanged { target: u32, index: f32 },
}

impl Event {
    /// Encode this event as a 16-byte opcode.
    ///
    /// `TextChanged` and `Navigate` with a URL cannot be fully encoded here —
    /// their strings live in the batch's string section / event arena — so this
    /// emits the opcode with a zero `B` offset; the batch-aware
    /// `encode_events`/`decode_events` pair in `pathland-core-transport` writes
    /// the string and its real offset.
    pub fn encode(&self) -> Opcode {
        match self {
            Event::PointerDown {
                target,
                x,
                y,
                secondary,
            } => Opcode::new(
                category::EVENT,
                crate::event::POINTER_DOWN,
                flag_for(*secondary, false, false),
                *target,
                x.to_bits(),
                y.to_bits(),
            ),
            Event::PointerMove {
                target,
                x,
                y,
                hovering,
                leaving,
            } => Opcode::new(
                category::EVENT,
                crate::event::POINTER_MOVE,
                flag_for(false, *hovering, *leaving),
                *target,
                x.to_bits(),
                y.to_bits(),
            ),
            Event::PointerUp {
                target,
                x,
                y,
                secondary,
            } => Opcode::new(
                category::EVENT,
                crate::event::POINTER_UP,
                flag_for(*secondary, false, false),
                *target,
                x.to_bits(),
                y.to_bits(),
            ),
            Event::KeyDown {
                target,
                key_code,
                modifiers,
                repeat,
            } => Opcode::new(
                category::EVENT,
                crate::event::KEY_DOWN,
                if *repeat { flag::KEY_REPEAT } else { 0 },
                *target,
                *key_code as u32,
                *modifiers as u32,
            ),
            Event::KeyUp {
                target,
                key_code,
                modifiers,
            } => Opcode::new(
                category::EVENT,
                crate::event::KEY_UP,
                0,
                *target,
                *key_code as u32,
                *modifiers as u32,
            ),
            Event::ValueChanged { target, value } => Opcode::new(
                category::EVENT,
                crate::event::VALUE_CHANGED,
                0,
                *target,
                value.to_bits(),
                0,
            ),
            Event::TextChanged { target, .. } => Opcode::new(
                category::EVENT,
                crate::event::TEXT_CHANGED,
                0,
                *target,
                0,
                0,
            ),
            Event::FocusChanged { target, focused } => Opcode::new(
                category::EVENT,
                crate::event::FOCUS_CHANGED,
                0,
                *target,
                u32::from(*focused),
                0,
            ),
            Event::EditingChanged { target, editing } => Opcode::new(
                category::EVENT,
                crate::event::EDITING_CHANGED,
                0,
                *target,
                u32::from(*editing),
                0,
            ),
            Event::Submit { target } => Opcode::new(
                category::EVENT,
                crate::event::SUBMIT,
                0,
                *target,
                0,
                0,
            ),
            Event::Scroll {
                target,
                offset_x,
                offset_y,
            } => Opcode::new(
                category::EVENT,
                crate::event::SCROLL,
                0,
                *target,
                offset_x.to_bits(),
                offset_y.to_bits(),
            ),
            Event::Wheel {
                target,
                delta_x,
                delta_y,
            } => Opcode::new(
                category::EVENT,
                crate::event::WHEEL,
                0,
                *target,
                delta_x.to_bits(),
                delta_y.to_bits(),
            ),
            Event::DateChanged {
                target,
                days,
                millis,
            } => Opcode::new(
                category::EVENT,
                crate::event::DATE_CHANGED,
                0,
                *target,
                *days as u32,
                *millis,
            ),
            Event::Navigate { url } => Opcode::new(
                category::EVENT,
                crate::event::NAVIGATE,
                if url.is_some() { flag::NAVIGATE_URL } else { 0 },
                0,
                0,
                0,
            ),
            Event::MediaPlayStateChanged { target, playing } => Opcode::new(
                category::EVENT,
                crate::event::MEDIA_PLAY_STATE_CHANGED,
                0,
                *target,
                u32::from(*playing),
                0,
            ),
            Event::MediaTimeUpdated { target, seconds } => Opcode::new(
                category::EVENT,
                crate::event::MEDIA_TIME_UPDATED,
                0,
                *target,
                seconds.to_bits(),
                0,
            ),
            Event::MediaEnded { target } => Opcode::new(
                category::EVENT,
                crate::event::MEDIA_ENDED,
                0,
                *target,
                0,
                0,
            ),
            Event::MediaVolumeChanged { target, volume } => Opcode::new(
                category::EVENT,
                crate::event::MEDIA_VOLUME_CHANGED,
                0,
                *target,
                volume.to_bits(),
                0,
            ),
            Event::FitChanged { target, index } => Opcode::new(
                category::EVENT,
                crate::event::FIT_CHANGED,
                0,
                *target,
                index.to_bits(),
                0,
            ),
        }
    }
}

/// Build a pointer-opcode flags word from the pointer/hover bits.
#[inline]
fn flag_for(secondary: bool, hovering: bool, leaving: bool) -> u16 {
    let mut flags = 0u16;
    if secondary {
        flags |= flag::POINTER_SECONDARY;
    }
    if hovering {
        flags |= flag::HOVER_ENTER;
    }
    if leaving {
        flags |= flag::HOVER_LEAVE;
    }
    flags
}

/// Error decoding a raw-input event from an opcode.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EventError {
    /// The opcode is not an `EVENT` category opcode.
    NotAnEvent,
    /// The opcode is an EVENT category opcode but an unknown command.
    UnknownCommand,
    /// The opcode is `TEXT_CHANGED`, which needs the batch's string section to
    /// resolve its text; decode it with `pathland-core-transport::decode_events`
    /// instead of a bare opcode.
    NeedsStringSection,
}

impl TryFrom<Opcode> for Event {
    type Error = EventError;

    fn try_from(op: Opcode) -> Result<Self, Self::Error> {
        if op.category() != category::EVENT {
            return Err(EventError::NotAnEvent);
        }
        let flags = op.flags();
        match op.command() {
            crate::event::POINTER_DOWN => Ok(Event::PointerDown {
                target: op.a(),
                x: op.b_f32(),
                y: op.c_f32(),
                secondary: flags & flag::POINTER_SECONDARY != 0,
            }),
            crate::event::POINTER_MOVE => Ok(Event::PointerMove {
                target: op.a(),
                x: op.b_f32(),
                y: op.c_f32(),
                hovering: flags & flag::HOVER_ENTER != 0,
                leaving: flags & flag::HOVER_LEAVE != 0,
            }),
            crate::event::POINTER_UP => Ok(Event::PointerUp {
                target: op.a(),
                x: op.b_f32(),
                y: op.c_f32(),
                secondary: flags & flag::POINTER_SECONDARY != 0,
            }),
            crate::event::KEY_DOWN => Ok(Event::KeyDown {
                target: op.a(),
                key_code: op.b() as u16,
                modifiers: op.c() as u8,
                repeat: flags & flag::KEY_REPEAT != 0,
            }),
            crate::event::KEY_UP => Ok(Event::KeyUp {
                target: op.a(),
                key_code: op.b() as u16,
                modifiers: op.c() as u8,
            }),
            crate::event::VALUE_CHANGED => Ok(Event::ValueChanged {
                target: op.a(),
                value: op.b_f32(),
            }),
            crate::event::FOCUS_CHANGED => Ok(Event::FocusChanged {
                target: op.a(),
                focused: op.b() != 0,
            }),
            crate::event::EDITING_CHANGED => Ok(Event::EditingChanged {
                target: op.a(),
                editing: op.b() != 0,
            }),
            crate::event::SUBMIT => Ok(Event::Submit { target: op.a() }),
            crate::event::SCROLL => Ok(Event::Scroll {
                target: op.a(),
                offset_x: op.b_f32(),
                offset_y: op.c_f32(),
            }),
            crate::event::WHEEL => Ok(Event::Wheel {
                target: op.a(),
                delta_x: op.b_f32(),
                delta_y: op.c_f32(),
            }),
            crate::event::DATE_CHANGED => Ok(Event::DateChanged {
                target: op.a(),
                days: op.b() as i32,
                millis: op.c(),
            }),
            crate::event::TEXT_CHANGED => Err(EventError::NeedsStringSection),
            crate::event::NAVIGATE => {
                if flags & flag::NAVIGATE_URL != 0 {
                    Err(EventError::NeedsStringSection)
                } else {
                    Ok(Event::Navigate { url: None })
                }
            }
            crate::event::MEDIA_PLAY_STATE_CHANGED => Ok(Event::MediaPlayStateChanged {
                target: op.a(),
                playing: op.b() != 0,
            }),
            crate::event::MEDIA_TIME_UPDATED => Ok(Event::MediaTimeUpdated {
                target: op.a(),
                seconds: op.b_f32(),
            }),
            crate::event::MEDIA_ENDED => Ok(Event::MediaEnded { target: op.a() }),
            crate::event::MEDIA_VOLUME_CHANGED => Ok(Event::MediaVolumeChanged {
                target: op.a(),
                volume: op.b_f32(),
            }),
            crate::event::FIT_CHANGED => Ok(Event::FitChanged {
                target: op.a(),
                index: op.b_f32(),
            }),
            _ => Err(EventError::UnknownCommand),
        }
    }
}

/// Decode an event opcode for the **shared-memory path**, resolving
/// `TEXT_CHANGED` text and `NAVIGATE` URLs from the host → guest event arena
/// (`B` is an absolute offset into that region). Non-string events decode as
/// usual.
///
/// Returns `None` for non-event opcodes, unknown commands, or string events
/// whose offset cannot be resolved (invalid or out of range).
pub fn decode_event(op: &Opcode, event_arena: &[u8]) -> Option<Event> {
    if op.category() != category::EVENT {
        return None;
    }
    if op.command() == crate::event::TEXT_CHANGED {
        let text = crate::arena::get_str(event_arena, op.b()).ok()?;
        return Some(Event::TextChanged {
            target: op.a(),
            value: text.to_string(),
        });
    }
    if op.command() == crate::event::NAVIGATE && op.flags() & flag::NAVIGATE_URL != 0 {
        let url = crate::arena::get_str(event_arena, op.b()).ok()?;
        return Some(Event::Navigate {
            url: Some(url.to_string()),
        });
    }
    Event::try_from(*op).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn round_trip(e: Event) -> Event {
        Event::try_from(Opcode::from_bytes(&e.encode().to_bytes())).unwrap()
    }

    #[test]
    fn pointer_down_round_trips() {
        let e = Event::PointerDown {
            target: 5,
            x: 10.0,
            y: 20.0,
            secondary: true,
        };
        assert_eq!(round_trip(e.clone()), e);
    }

    #[test]
    fn pointer_move_round_trips_with_hover_flags() {
        let e = Event::PointerMove {
            target: 5,
            x: 10.0,
            y: 20.0,
            hovering: true,
            leaving: false,
        };
        assert_eq!(round_trip(e.clone()), e);

        let leaving = Event::PointerMove {
            target: 5,
            x: 10.0,
            y: 20.0,
            hovering: false,
            leaving: true,
        };
        assert_eq!(round_trip(leaving.clone()), leaving);
    }

    #[test]
    fn pointer_up_round_trips() {
        let e = Event::PointerUp {
            target: 5,
            x: 10.0,
            y: 20.0,
            secondary: false,
        };
        assert_eq!(round_trip(e.clone()), e);
    }

    #[test]
    fn key_down_round_trips_with_repeat() {
        let e = Event::KeyDown {
            target: 5,
            key_code: 0x41,
            modifiers: 0x01,
            repeat: true,
        };
        assert_eq!(round_trip(e.clone()), e);
    }

    #[test]
    fn key_up_round_trips() {
        let e = Event::KeyUp {
            target: 5,
            key_code: 0x41,
            modifiers: 0x01,
        };
        assert_eq!(round_trip(e.clone()), e);
    }

    #[test]
    fn value_changed_round_trips() {
        let e = Event::ValueChanged {
            target: 7,
            value: 0.625,
        };
        assert_eq!(round_trip(e.clone()), e);
    }

    #[test]
    fn draft_events_round_trip() {
        for e in [
            Event::FocusChanged { target: 1, focused: true },
            Event::FocusChanged { target: 1, focused: false },
            Event::EditingChanged { target: 2, editing: true },
            Event::Submit { target: 3 },
            Event::Scroll { target: 4, offset_x: 10.0, offset_y: 20.5 },
            Event::Wheel { target: 5, delta_x: -1.5, delta_y: 2.0 },
            Event::DateChanged { target: 6, days: 19_723, millis: 3_600_000 },
            Event::DateChanged { target: 6, days: -1, millis: 0 },
        ] {
            assert_eq!(round_trip(e.clone()), e);
        }
    }

    #[test]
    fn media_events_round_trip() {
        for e in [
            Event::MediaPlayStateChanged { target: 7, playing: true },
            Event::MediaPlayStateChanged { target: 7, playing: false },
            Event::MediaTimeUpdated { target: 7, seconds: 12.5 },
            Event::MediaEnded { target: 7 },
            Event::MediaVolumeChanged { target: 7, volume: 0.5 },
            Event::FitChanged { target: 42, index: 2.0 },
        ] {
            assert_eq!(round_trip(e.clone()), e);
        }
        // Conformance vector bytes (spec/CONFORMANCE.md vectors 23–26, 26a).
        assert_eq!(
            Event::MediaPlayStateChanged { target: 7, playing: true }.encode().to_bytes(),
            [0x03, 0x0F, 0, 0, 7, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0]
        );
        assert_eq!(
            Event::MediaTimeUpdated { target: 7, seconds: 12.5 }.encode().to_bytes(),
            [0x03, 0x10, 0, 0, 7, 0, 0, 0, 0, 0, 0x48, 0x41, 0, 0, 0, 0]
        );
        assert_eq!(
            Event::MediaEnded { target: 7 }.encode().to_bytes(),
            [0x03, 0x11, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
        );
        assert_eq!(
            Event::MediaVolumeChanged { target: 7, volume: 0.5 }.encode().to_bytes(),
            [0x03, 0x12, 0, 0, 7, 0, 0, 0, 0, 0, 0x00, 0x3F, 0, 0, 0, 0]
        );
        assert_eq!(
            Event::FitChanged { target: 42, index: 2.0 }.encode().to_bytes(),
            [0x03, 0x13, 0, 0, 0x2A, 0, 0, 0, 0, 0, 0, 0x40, 0, 0, 0, 0]
        );
    }

    #[test]
    fn non_event_opcode_is_rejected() {
        let op = Opcode::new(crate::category::TREE, 0x01, 0, 1, 0, 0);
        assert_eq!(Event::try_from(op), Err(EventError::NotAnEvent));
    }

    #[test]
    fn unknown_event_command_is_rejected() {
        // EVENT category with a command that does not exist in the protocol.
        let op = Opcode::new(crate::category::EVENT, 0xFF, 0, 1, 0, 0);
        assert_eq!(Event::try_from(op), Err(EventError::UnknownCommand));
    }

    #[test]
    fn text_changed_needs_string_section() {
        // `TextChanged` is only decodable from a batch (its text lives in the
        // string section); a bare opcode cannot resolve it.
        let op = Opcode::new(crate::category::EVENT, crate::event::TEXT_CHANGED, 0, 7, 0, 0);
        assert_eq!(Event::try_from(op), Err(EventError::NeedsStringSection));
    }

    #[test]
    fn navigate_back_round_trips() {
        let e = Event::Navigate { url: None };
        assert_eq!(round_trip(e.clone()), e);
    }

    #[test]
    fn navigate_with_url_needs_string_section() {
        // `Navigate` with a URL is only decodable where the URL can be resolved
        // (batch string section / shared-memory event arena); a bare opcode
        // cannot resolve it.
        let op = Opcode::new(
            crate::category::EVENT,
            crate::event::NAVIGATE,
            flag::NAVIGATE_URL,
            0,
            0,
            0,
        );
        assert_eq!(Event::try_from(op), Err(EventError::NeedsStringSection));
    }

    #[test]
    fn navigate_back_matches_conformance_vector_bytes() {
        // EVENT:NAVIGATE (back, no URL) — vector 22.
        let e = Event::Navigate { url: None };
        assert_eq!(
            e.encode().to_bytes(),
            [0x03, 0x0E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]
        );
    }

    #[test]
    fn decode_event_resolves_navigate_url_from_arena() {
        // The shared-memory path resolves a `Navigate` URL from the event arena.
        use alloc::vec;
        let mut arena = vec![0u8; 32];
        // "[u32 len=9][/users/42]" at offset 0.
        arena[0..4].copy_from_slice(&9u32.to_le_bytes());
        arena[4..13].copy_from_slice(b"/users/42");
        let op = Opcode::new(
            crate::category::EVENT,
            crate::event::NAVIGATE,
            flag::NAVIGATE_URL,
            0,
            0,
            0,
        );
        assert_eq!(
            decode_event(&op, &arena),
            Some(Event::Navigate {
                url: Some("/users/42".into())
            })
        );
    }

    #[test]
    fn pointer_move_round_trips_with_combined_hover_flags() {
        // Both hovering and leaving set simultaneously must survive the round trip.
        let e = Event::PointerMove {
            target: 5,
            x: 10.0,
            y: 20.0,
            hovering: true,
            leaving: true,
        };
        assert_eq!(round_trip(e.clone()), e);
        // The flags word carries both bits.
        assert_eq!(e.encode().flags(), flag::HOVER_ENTER | flag::HOVER_LEAVE);
    }

    #[test]
    fn matches_conformance_vector_bytes() {
        // EVENT:POINTER_DOWN (target=5, x=10.0, y=20.0)
        let down = Event::PointerDown {
            target: 5,
            x: 10.0,
            y: 20.0,
            secondary: false,
        };
        assert_eq!(
            down.encode().to_bytes(),
            [
                0x03, 0x01, 0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x41, 0x00,
                0x00, 0xA0, 0x41,
            ]
        );
    }
}
