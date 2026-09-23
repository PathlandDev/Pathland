//! Batch wire format: encode/decode opcodes + arena delta into network batches.

use alloc::vec::Vec;
use pathland_core::{Event, Frame, Opcode, category, event, meta};

use crate::{BATCH_MAGIC, BATCH_VERSION};

/// Fixed header byte length: magic(4) + version(2) + flags(2) + frameCount(4) + opcodeCount(4).
pub const BATCH_HEADER: usize = 16;

/// Errors produced while decoding a network batch.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BatchError {
    /// The batch is shorter than the fixed header.
    Truncated,
    /// The batch magic does not match `BATCH_MAGIC`.
    BadMagic,
    /// The batch version is not supported.
    BadVersion,
    /// The opcode payload is truncated (opcodeCount × 16 exceeds the buffer).
    BadOpcodeCount,
    /// The arena delta length exceeds the remaining buffer.
    BadArenaLen,
}

/// A decoded network batch: opcodes plus a mirrored arena, exposed as a
/// `pathland_core::Frame` so consumers (`RenderTree::apply_frame`) work
/// exactly as with shared-memory frames.
pub struct Batch<'a> {
    frame: Frame<'a>,
    frame_count: u32,
    flags: u16,
}

impl<'a> core::fmt::Debug for Batch<'a> {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("Batch")
            .field("frame_count", &self.frame_count)
            .field("flags", &self.flags)
            .field("len", &self.len())
            .finish()
    }
}

impl<'a> Batch<'a> {
    /// The opcodes carried by this batch, in order.
    pub fn opcodes(&self) -> impl Iterator<Item = Opcode> + '_ {
        self.frame.opcodes()
    }

    /// Number of opcodes in the batch.
    pub fn len(&self) -> usize {
        self.frame.len()
    }

    pub fn is_empty(&self) -> bool {
        self.frame.is_empty()
    }

    /// The guest frame count the batch spans.
    pub fn frame_count(&self) -> u32 {
        self.frame_count
    }

    /// Direction flags (see [`crate::direction`]).
    pub fn flags(&self) -> u16 {
        self.flags
    }

    /// View this batch as a `Frame` (the transport seam).
    pub fn as_frame(&self) -> &Frame<'a> {
        &self.frame
    }

    /// Read an arena entry by offset (see `Frame::arena_get`).
    pub fn arena_get(&self, offset: u32) -> Result<&'a [u8], pathland_core::ArenaError> {
        self.frame.arena_get(offset)
    }

    /// Read an arena string by offset.
    pub fn arena_str(&self, offset: u32) -> Result<&'a str, pathland_core::ArenaError> {
        self.frame.arena_str(offset)
    }
}

/// Serialize one batch from opcodes + an arena delta (bytes appended since the
/// previous batch). Stateless; see [`BatchEncoder`] for cursor tracking.
pub fn encode_batch(
    frame_count: u32,
    flags: u16,
    opcodes: &[Opcode],
    arena_delta: &[u8],
) -> Vec<u8> {
    let mut out = Vec::with_capacity(BATCH_HEADER + opcodes.len() * Opcode::SIZE + arena_delta.len() + 4);
    out.extend_from_slice(&BATCH_MAGIC.to_le_bytes());
    out.extend_from_slice(&BATCH_VERSION.to_le_bytes());
    out.extend_from_slice(&flags.to_le_bytes());
    out.extend_from_slice(&frame_count.to_le_bytes());
    out.extend_from_slice(&(opcodes.len() as u32).to_le_bytes());
    for op in opcodes {
        out.extend_from_slice(&op.to_bytes());
    }
    out.extend_from_slice(&(arena_delta.len() as u32).to_le_bytes());
    out.extend_from_slice(arena_delta);
    out
}

/// Fields parsed from a batch header + payload regions.
struct ParsedBatch<'a> {
    flags: u16,
    frame_count: u32,
    opcodes: &'a [u8],
    arena_delta: &'a [u8],
}

/// Parse a batch header, returning flags, frame count, opcode bytes and arena delta.
fn parse(bytes: &[u8]) -> Result<ParsedBatch<'_>, BatchError> {
    if bytes.len() < BATCH_HEADER {
        return Err(BatchError::Truncated);
    }
    let magic = u32::from_le_bytes(bytes[0..4].try_into().unwrap());
    if magic != BATCH_MAGIC {
        return Err(BatchError::BadMagic);
    }
    let version = u16::from_le_bytes(bytes[4..6].try_into().unwrap());
    if version != BATCH_VERSION {
        return Err(BatchError::BadVersion);
    }
    let flags = u16::from_le_bytes(bytes[6..8].try_into().unwrap());
    let frame_count = u32::from_le_bytes(bytes[8..12].try_into().unwrap());
    let opcode_count = u32::from_le_bytes(bytes[12..16].try_into().unwrap()) as usize;

    let opcodes_bytes = opcode_count
        .checked_mul(Opcode::SIZE)
        .ok_or(BatchError::BadOpcodeCount)?;
    let opcodes_start = BATCH_HEADER;
    let opcodes_end = opcodes_start
        .checked_add(opcodes_bytes)
        .ok_or(BatchError::BadOpcodeCount)?;
    if opcodes_end > bytes.len() {
        return Err(BatchError::BadOpcodeCount);
    }
    let arena_len_pos = opcodes_end;
    if arena_len_pos + 4 > bytes.len() {
        return Err(BatchError::BadArenaLen);
    }
    let arena_len = u32::from_le_bytes(bytes[arena_len_pos..arena_len_pos + 4].try_into().unwrap())
        as usize;
    let arena_start = arena_len_pos + 4;
    let arena_end = arena_start
        .checked_add(arena_len)
        .ok_or(BatchError::BadArenaLen)?;
    if arena_end > bytes.len() {
        return Err(BatchError::BadArenaLen);
    }

    Ok(ParsedBatch {
        flags,
        frame_count,
        opcodes: &bytes[opcodes_start..opcodes_end],
        arena_delta: &bytes[arena_start..arena_end],
    })
}

/// A streamed network-batch encoder.
///
/// Tracks the last arena length so successive batches carry only the bytes
/// appended to the bump arena since the previous batch. If the arena is reset
/// (shrank — see `META::RESET`), the full used arena is sent and tracking
/// restarts.
#[derive(Debug, Default)]
pub struct BatchEncoder {
    last_arena_len: usize,
}

impl BatchEncoder {
    /// Create a fresh encoder (no arena bytes sent yet).
    pub fn new() -> Self {
        Self { last_arena_len: 0 }
    }

    /// Encode a frame's opcodes plus the arena bytes appended since the last
    /// call. `arena_used` is the arena region's used portion (`[0..cursor)`).
    ///
    /// If the frame contains `META::RESET`, arena tracking restarts (the arena
    /// cursor returns to 0) and the full used arena is sent.
    pub fn encode(&mut self, frame_count: u32, opcodes: &[Opcode], arena_used: &[u8]) -> Vec<u8> {
        let reset = opcodes.iter().any(|op| {
            op.category() == pathland_core::category::META
                && op.command() == pathland_core::meta::RESET
        });
        if reset {
            self.last_arena_len = 0;
        }
        let delta = if arena_used.len() >= self.last_arena_len {
            &arena_used[self.last_arena_len..]
        } else {
            // Arena shrank unexpectedly: send the full used arena again.
            arena_used
        };
        let bytes = encode_batch(frame_count, crate::direction::GUEST_TO_HOST, opcodes, delta);
        self.last_arena_len = arena_used.len();
        bytes
    }

    /// The arena length already shipped (diagnostics).
    pub fn arena_sent(&self) -> usize {
        self.last_arena_len
    }
}

/// A streamed network-batch decoder.
///
/// Maintains a **mirrored arena**: each batch's arena delta is appended, so
/// absolute `arenaRef` offsets resolve exactly as in shared memory. When a
/// batch contains `META::RESET`, the mirror is cleared before appending.
#[derive(Debug, Default)]
pub struct BatchDecoder {
    arena: Vec<u8>,
}

impl BatchDecoder {
    /// Create an empty decoder.
    pub fn new() -> Self {
        Self { arena: Vec::new() }
    }

    /// Decode a batch, appending its arena delta to the mirror.
    ///
    /// The returned `Batch` borrows `bytes` (opcodes) and this decoder (arena);
    /// keep both alive while consuming it.
    pub fn decode<'a>(&'a mut self, bytes: &'a [u8]) -> Result<Batch<'a>, BatchError> {
        let parsed = parse(bytes)?;

        // A META::RESET in the batch clears the renderer; reset the arena mirror
        // to match (offsets restart at 0 after a reset).
        let opcode_count = parsed.opcodes.len() / Opcode::SIZE;
        let reset = (0..opcode_count).any(|i| {
            let op = Opcode::from_bytes(parsed.opcodes[i * Opcode::SIZE..(i + 1) * Opcode::SIZE].try_into().unwrap());
            op.category() == pathland_core::category::META
                && op.command() == pathland_core::meta::RESET
        });
        if reset {
            self.arena.clear();
        }
        self.arena.extend_from_slice(parsed.arena_delta);

        let frame = Frame::from_parts(parsed.opcodes, &self.arena, 0, parsed.opcodes.len());
        Ok(Batch {
            frame,
            frame_count: parsed.frame_count,
            flags: parsed.flags,
        })
    }

    /// The mirrored arena length (diagnostics).
    pub fn arena_len(&self) -> usize {
        self.arena.len()
    }
}

/// Encode raw-input events as a host → guest batch.
///
/// Events are `EVENT`-category opcodes; the batch reuses the wire format with
/// the `HOST_TO_GUEST` direction flag. `TextChanged` events carry their text in
/// the batch's string section (a length-prefixed entry referenced by the
/// opcode's `B` offset), as do `Navigate` URLs; the string section is only
/// empty when there are no such events.
pub fn encode_events(frame_count: u32, events: &[Event]) -> Vec<u8> {
    let mut opcodes = Vec::new();
    let mut strings = Vec::new();
    for ev in events {
        match ev {
            Event::TextChanged { target, value } => {
                let offset = strings.len() as u32;
                strings.extend_from_slice(&(value.len() as u32).to_le_bytes());
                strings.extend_from_slice(value.as_bytes());
                opcodes.push(Opcode::new(
                    category::EVENT,
                    event::TEXT_CHANGED,
                    0,
                    *target,
                    offset,
                    0,
                ));
            }
            Event::Navigate { url: Some(url) } => {
                let offset = strings.len() as u32;
                strings.extend_from_slice(&(url.len() as u32).to_le_bytes());
                strings.extend_from_slice(url.as_bytes());
                opcodes.push(Opcode::new(
                    category::EVENT,
                    event::NAVIGATE,
                    pathland_core::flag::NAVIGATE_URL,
                    0,
                    offset,
                    0,
                ));
            }
            other => opcodes.push(other.encode()),
        }
    }
    encode_batch(frame_count, crate::direction::HOST_TO_GUEST, &opcodes, &strings)
}

/// Decode a host → guest event batch, returning `(frame_count, events)`.
///
/// Unknown or malformed event opcodes are skipped. `TextChanged` opcodes and
/// `Navigate` URL opcodes resolve their strings from the batch's string section.
pub fn decode_events(bytes: &[u8]) -> Result<(u32, Vec<Event>), BatchError> {
    let mut decoder = BatchDecoder::new();
    let batch = decoder.decode(bytes)?;
    let events = batch
        .opcodes()
        .filter_map(|op| {
            if op.category() == category::EVENT && op.command() == event::TEXT_CHANGED {
                batch
                    .arena_str(op.b())
                    .ok()
                    .map(|text| Event::TextChanged {
                        target: op.a(),
                        value: text.to_string(),
                    })
            } else if op.category() == category::EVENT && op.command() == event::NAVIGATE {
                if op.flags() & pathland_core::flag::NAVIGATE_URL != 0 {
                    batch
                        .arena_str(op.b())
                        .ok()
                        .map(|url| Event::Navigate {
                            url: Some(url.to_string()),
                        })
                } else {
                    Some(Event::Navigate { url: None })
                }
            } else {
                Event::try_from(op).ok()
            }
        })
        .collect();
    Ok((batch.frame_count(), events))
}

/// Build a self-contained `Frame` view over in-memory opcodes + a string
/// section — the **in-process transport seam**.
///
/// `slots` is reused as the opcode byte buffer (cleared, then the 16-byte
/// opcodes written in), so the returned `Frame` borrows both `slots` and
/// `strings` with no serialization. This is how an in-process consumer receives
/// the same `Frame` view a ring or network decoder would produce.
pub fn frame_from_slices<'a>(
    slots: &'a mut Vec<u8>,
    strings: &'a [u8],
    opcodes: &[Opcode],
) -> Frame<'a> {
    slots.clear();
    slots.extend(opcodes.iter().flat_map(Opcode::to_bytes));
    let bytes: &'a [u8] = slots;
    Frame::from_parts(bytes, strings, 0, bytes.len())
}

/// Encode a **self-contained** frame as a network batch.
///
/// `opcodes` is the frame's commands and `strings` its per-frame string
/// section: every `SET_TEXT` opcode references its string by a *relative*
/// offset into `strings` (not an absolute arena offset). The batch therefore
/// decodes with no prior state — no mirrored arena, no delta tracking — which
/// is what makes each WebSocket message independent.
///
/// This is the network/stream codec. The shared-memory path uses
/// [`encode_batch`]/[`BatchEncoder`] instead, whose arena offsets are absolute
/// and mirrored on the consumer.
pub fn encode_frame(opcodes: &[Opcode], strings: &[u8]) -> Vec<u8> {
    encode_batch(0, crate::direction::GUEST_TO_HOST, opcodes, strings)
}

/// Encode a `META::RESYNC` request batch (host → guest): the host (renderer)
/// asks the guest (application) to re-send the full tree as a snapshot.
/// `A/B/C` are all zero; the guest answers with a full-snapshot batch.
pub fn encode_resync() -> Vec<u8> {
    encode_batch(
        0,
        crate::direction::HOST_TO_GUEST,
        &[Opcode::new(category::META, meta::RESYNC, 0, 0, 0, 0)],
        &[],
    )
}

/// Decode a self-contained frame batch, returning `(opcodes, strings)`.
///
/// Stateless: unlike [`BatchDecoder`], no arena mirror is maintained, because
/// the batch carries its own string section and the offsets are relative to it.
pub fn decode_frame(bytes: &[u8]) -> Result<(Vec<Opcode>, Vec<u8>), BatchError> {
    let parsed = parse(bytes)?;
    let count = parsed.opcodes.len() / Opcode::SIZE;
    let opcodes = (0..count)
        .map(|i| {
            let start = i * Opcode::SIZE;
            Opcode::from_bytes(parsed.opcodes[start..start + Opcode::SIZE].try_into().unwrap())
        })
        .collect();
    Ok((opcodes, parsed.arena_delta.to_vec()))
}

#[test]
    fn encode_resync_is_a_host_to_guest_meta_batch() {
        let bytes = encode_resync();
        let parsed = parse(&bytes).unwrap();
        let count = parsed.opcodes.len() / Opcode::SIZE;
        assert_eq!(count, 1);
        let op = Opcode::from_bytes(parsed.opcodes[0..Opcode::SIZE].try_into().unwrap());
        assert_eq!(op.category(), category::META);
        assert_eq!(op.command(), meta::RESYNC);
        assert_eq!(op.a(), 0);
        assert_eq!(op.b(), 0);
        assert_eq!(op.c(), 0);
        assert_eq!(parsed.flags, crate::direction::HOST_TO_GUEST);
        assert_eq!(parsed.arena_delta.len(), 0);
    }

    #[cfg(test)]
    mod tests {
        use super::*;
        use pathland_core::{category, meta, Opcode};

        fn sample_opcodes() -> Vec<Opcode> {
        vec![
            Opcode::new(category::TREE, 0x01, 0, 1, 0x0010, 0),
            Opcode::new(category::STYLE, 0x03, 0, 1, 0, 0),
        ]
    }

    #[test]
    fn decoded_batch_feeds_render_tree() {
        // Emit a real frame from the engine, serialize it as a network batch,
        // decode it, and apply to the shared RenderTree — proving the
        // network path is interchangeable with the shared-memory ring.
        use pathland_render_core::RenderTree;
        use pathland_engine::Engine;
        use pathland_core::{init_memory, Guest, MemoryLayout};
        use pathland_view::{assign_ids, text, vstack, View};

        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        let mut root = vstack![text("hello"), text("world")].build();
        assign_ids(&mut root, &mut 1);

        let mut engine = Engine::new();
        let frame_count;
        let opcodes;
        let arena_used;
        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            engine.emit(&root, &mut guest).unwrap();
            guest.end_frame();
            frame_count = guest.frame_count();

            let mut host = pathland_core::Host::new(&mut mem, &layout);
            let frame = &host.frames()[0];
            opcodes = frame.opcodes().collect::<Vec<Opcode>>();
            arena_used = {
                // The arena's used portion is everything written so far.
                let arena = frame.arena();
                arena.to_vec()
            };
        }

        let mut enc = BatchEncoder::new();
        let batch_bytes = enc.encode(frame_count, &opcodes, &arena_used);

        let mut decoder = BatchDecoder::new();
        let mut tree = RenderTree::default();
        {
            let batch = decoder.decode(&batch_bytes).unwrap();
            tree.apply_frame(batch.as_frame());
        }

        // Root VStack + two texts, text "hello" resolved via the mirrored arena.
        assert_eq!(tree.nodes.len(), 3);
        let child = tree.node(2).unwrap();
        assert_eq!(child.text.as_deref(), Some("hello"));
    }

    #[test]
    fn encode_then_decode_round_trips() {
        let opcodes = sample_opcodes();
        let arena_delta = [5u8, 0, 0, 0, b'h', b'e', b'l', b'l', b'o'];
        let bytes = encode_batch(7, 0, &opcodes, &arena_delta);

        let mut decoder = BatchDecoder::new();
        {
            let batch = decoder.decode(&bytes).unwrap();
            assert_eq!(batch.frame_count(), 7);
            assert_eq!(batch.len(), opcodes.len());
            assert_eq!(batch.arena_str(0).unwrap(), "hello");
            let decoded: Vec<Opcode> = batch.opcodes().collect();
            assert_eq!(decoded, opcodes);
        }
        assert_eq!(decoder.arena_len(), arena_delta.len());
    }

    #[test]
    fn bad_magic_is_rejected() {
        let opcodes = sample_opcodes();
        let mut bytes = encode_batch(0, 0, &opcodes, &[]);
        bytes[0] = 0xFF;
        assert_eq!(
            BatchDecoder::new().decode(&bytes).unwrap_err(),
            BatchError::BadMagic
        );
    }

    #[test]
    fn truncated_is_rejected() {
        assert_eq!(
            BatchDecoder::new().decode(&[0u8; 4]).unwrap_err(),
            BatchError::Truncated
        );
    }

    #[test]
    fn encoder_tracks_arena_delta() {
        let opcodes = sample_opcodes();
        let mut enc = BatchEncoder::new();
        // Entry 0: len 1 "a"  -> used = [1,0,0,0,'a'] (5 bytes)
        let first = enc.encode(1, &opcodes, &[1u8, 0, 0, 0, b'a']);
        // Append entry 1: len 4 "bcde" -> used = [1,0,0,0,'a', 4,0,0,0,'b','c','d','e'] (13)
        let second = enc.encode(
            2,
            &opcodes,
            &[1, 0, 0, 0, b'a', 4, 0, 0, 0, b'b', b'c', b'd', b'e'],
        );

        let mut decoder = BatchDecoder::new();
        {
            let b1 = decoder.decode(&first).unwrap();
            assert_eq!(b1.arena_str(0).unwrap(), "a");
        }
        let first_arena_len = decoder.arena_len();
        {
            let b2 = decoder.decode(&second).unwrap();
            assert_eq!(b2.arena_str(5).unwrap(), "bcde");
        }
        let second_arena_len = decoder.arena_len();
        assert_eq!(first_arena_len, 5);
        assert_eq!(second_arena_len, 13);
        // Second batch carries only the appended 8 bytes; the mirror now has both.
    }

    #[test]
    fn reset_restarts_arena_delta() {
        let reset = Opcode::new(category::META, meta::RESET, 0, 0, 0, 0);
        let mut enc = BatchEncoder::new();
        // Frame 1 allocates entry 0 (len 1 "a") but then resets.
        let first = enc.encode(1, &[reset], &[1u8, 0, 0, 0, b'a']);
        // Frame 2 starts a fresh arena with entry 0 (len 3 "xyz") at offset 0.
        let after_reset = enc.encode(2, &[reset], &[3u8, 0, 0, 0, b'x', b'y', b'z']);

        let mut decoder = BatchDecoder::new();
        decoder.decode(&first).unwrap();
        assert_eq!(decoder.arena_len(), 5);
        {
            let batch = decoder.decode(&after_reset).unwrap();
            assert_eq!(batch.arena_str(0).unwrap(), "xyz");
        }
        // Mirror cleared on RESET, then appended the fresh 7-byte entry.
        assert_eq!(decoder.arena_len(), 7);
    }

    #[test]
    fn events_round_trip_host_to_guest() {
        let events = vec![
            Event::PointerUp {
                target: 4,
                x: 10.0,
                y: 20.0,
                secondary: false,
            },
            Event::PointerDown {
                target: 5,
                x: 1.0,
                y: 2.0,
                secondary: true,
            },
        ];
        let bytes = encode_events(7, &events);
        let (frame_count, decoded) = decode_events(&bytes).unwrap();
        assert_eq!(frame_count, 7);
        assert_eq!(decoded, events);
    }

    #[test]
    fn text_changed_round_trips_through_batch() {
        let events = vec![
            Event::TextChanged {
                target: 9,
                value: "hello world".to_string(),
            },
            Event::PointerUp {
                target: 4,
                x: 1.0,
                y: 2.0,
                secondary: false,
            },
        ];
        let bytes = encode_events(3, &events);
        let (frame_count, decoded) = decode_events(&bytes).unwrap();
        assert_eq!(frame_count, 3);
        assert_eq!(decoded, events);
    }

    #[test]
    fn navigate_round_trips_through_batch() {
        let events = vec![
            Event::Navigate {
                url: Some("/users/42".to_string()),
            },
            Event::Navigate { url: None },
        ];
        let bytes = encode_events(5, &events);
        let (frame_count, decoded) = decode_events(&bytes).unwrap();
        assert_eq!(frame_count, 5);
        assert_eq!(decoded, events);
    }

    #[test]
    fn draft_events_round_trip_through_batch() {
        let events = vec![
            Event::FocusChanged { target: 1, focused: true },
            Event::EditingChanged { target: 2, editing: false },
            Event::Submit { target: 3 },
            Event::Scroll { target: 4, offset_x: 10.0, offset_y: 20.0 },
            Event::Wheel { target: 5, delta_x: -1.0, delta_y: 2.0 },
            Event::DateChanged { target: 6, days: 19_723, millis: 0 },
        ];
        let bytes = encode_events(4, &events);
        let (frame_count, decoded) = decode_events(&bytes).unwrap();
        assert_eq!(frame_count, 4);
        assert_eq!(decoded, events);
    }

    #[test]
    fn self_contained_frame_round_trips() {
        // SET_TEXT with a relative offset into the frame's string section.
        let mut strings = Vec::new();
        strings.extend_from_slice(&(5u32).to_le_bytes());
        strings.extend_from_slice(b"Hello");
        let opcodes = vec![
            Opcode::new(category::TREE, 0x01, 0, 1, 0x0010, 0),
            Opcode::new(category::STYLE, 0x03, 0, 2, 0, 0), // SET_TEXT, B = relative offset 0
        ];

        let bytes = encode_frame(&opcodes, &strings);
        let (decoded_ops, decoded_strings) = decode_frame(&bytes).unwrap();

        assert_eq!(decoded_ops, opcodes);
        assert_eq!(decoded_strings, strings);
    }
}
