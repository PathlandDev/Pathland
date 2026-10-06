//! # pathland-core
//!
//! The 16-byte fixed-size opcode engine for Pathland: fixed-size opcodes, a
//! single-producer ring buffer, a bump arena for variable-length data, and the
//! linear-memory layout shared with host renderers.
//!
//! This crate is `no_std` + `alloc`. It has no runtime dependencies.
//!
//! ## Producer (guest engine)
//!
//! ```no_run
//! use pathland_core::{init_memory, Guest, MemoryLayout};
//!
//! let layout = MemoryLayout::default();
//! let mut mem = vec![0u8; layout.total_bytes()];
//! init_memory(&mut mem, &layout);
//!
//! let mut guest = Guest::new(&mut mem, &layout);
//! guest.begin_frame();
//! guest.create_node(1, pathland_core::component_type::VSTACK).unwrap();
//! guest.insert_child(0, 1, pathland_core::APPEND).unwrap();
//! guest.set_property(1, pathland_core::property_id::SPACING, pathland_core::value_type::F32, 4.0f32.to_bits()).unwrap();
//! guest.end_frame(); // publishes frameCount
//! ```
//!
//! ## Consumer (host renderer)
//!
//! ```no_run
//! use pathland_core::{init_memory, Guest, Host, MemoryLayout};
//! # let layout = MemoryLayout::default();
//! # let mut mem = vec![0u8; layout.total_bytes()];
//! # init_memory(&mut mem, &layout);
//! # let mut guest = Guest::new(&mut mem, &layout);
//! # guest.begin_frame();
//! # guest.create_node(1, 1).unwrap();
//! # guest.end_frame();
//! let mut host = Host::new(&mut mem, &layout);
//! for frame in host.frames() {
//!     for op in frame.opcodes() {
//!         println!("{:?}", op);
//!     }
//! }
//! ```

#![no_std]
#![forbid(unsafe_op_in_unsafe_fn)]

#[cfg(test)]
extern crate std;

extern crate alloc;

mod arena;
mod conformance;
mod constants;
mod events;
mod memory;
mod opcode;
mod ring;
pub mod tokens;

pub use arena::ArenaError;
pub use constants::*;
pub use events::{Event, EventError};
pub use memory::MemoryLayout;
pub use opcode::{Opcode, OPCODE_SIZE};
pub use ring::RingError;

use alloc::vec;
use alloc::vec::Vec;
use arena as arena_fn;
use memory::header;
use memory::{OFF_FRAME_COUNT, OFF_READ_CURSOR, OFF_SLOT_COUNT, OFF_WRITE_CURSOR};
use ring as ring_fn;

/// Initialize the linear memory header for a layout. Call once at startup
/// before creating a `Guest` or `Host`.
pub fn init_memory(mem: &mut [u8], layout: &MemoryLayout) {
    header::init(mem, layout);
}

/// Accessor for a shared-memory ring header, for consumers that read the ring
/// in place (zero-copy) without a `Host` handle — e.g. a foreign driver that
/// holds a raw pointer to the shared linear memory.
///
/// Field offsets match [`memory::OFF_*`] constants and are part of the stable
/// wire layout (see `spec/OPCODE.md`).
pub struct SharedHeader<'a> {
    mem: &'a [u8],
}

impl<'a> SharedHeader<'a> {
    /// Wrap an immutable view of the shared linear memory.
    pub fn new(mem: &'a [u8]) -> Self {
        Self { mem }
    }

    fn u32(&self, off: usize) -> u32 {
        u32::from_le_bytes(self.mem[off..off + 4].try_into().unwrap())
    }

    /// `frameCount` — the completed-frame watermark.
    pub fn frame_count(&self) -> u32 {
        self.u32(memory::OFF_FRAME_COUNT)
    }

    /// `readCursor` — the slot the consumer has consumed up to (masked).
    pub fn read_cursor(&self) -> u32 {
        self.u32(memory::OFF_READ_CURSOR)
    }

    /// `writeCursor` — the slot the producer has written up to (masked).
    pub fn write_cursor(&self) -> u32 {
        self.u32(memory::OFF_WRITE_CURSOR)
    }

    /// `slotCount` — number of ring slots (power of two).
    pub fn slot_count(&self) -> u32 {
        self.u32(memory::OFF_SLOT_COUNT)
    }

    /// Byte offset of the ring region within the linear memory.
    pub fn ring_offset(&self) -> usize {
        self.u32(memory::OFF_RING_OFFSET) as usize
    }

    /// Byte length of the ring region.
    pub fn ring_bytes(&self) -> usize {
        self.u32(memory::OFF_RING_BYTES) as usize
    }

    /// Byte offset of the arena region within the linear memory.
    pub fn arena_offset(&self) -> usize {
        self.u32(memory::OFF_ARENA_OFFSET) as usize
    }

    /// Byte offset of the arena cursor.
    pub fn arena_cursor(&self) -> u32 {
        self.u32(memory::OFF_ARENA_CURSOR)
    }
}

/// Number of ring slots per the header.
fn slot_count(header: &[u8]) -> usize {
    header::get_u32(header, OFF_SLOT_COUNT) as usize
}

/// Ring mask per the header (slot count is a power of two).
fn slot_mask(header: &[u8]) -> usize {
    let slots = slot_count(header);
    debug_assert!(slots.is_power_of_two());
    slots - 1
}

// ---------------------------------------------------------------------------
// Guest (producer)
// ---------------------------------------------------------------------------

/// Producer side: the guest engine writing opcodes and arena data into shared
/// linear memory. Owns the header, ring, and arena regions as disjoint slices.
pub struct Guest<'a> {
    header: &'a mut [u8],
    slots: &'a mut [u8],
    /// Host → guest event ring (guest reads raw inputs here).
    event_slots: &'a mut [u8],
    arena: &'a mut [u8],
    /// Host → guest event arena (host-owned bump; the guest resolves `TEXT_CHANGED`
    /// text here over the shared ring).
    event_arena: &'a mut [u8],
    /// Write cursor captured at `begin_frame`.
    frame_start: u32,
}

impl<'a> Guest<'a> {
    /// Create a guest view over the linear memory. `init_memory` MUST have been
    /// called on the same memory beforehand.
    pub fn new(mem: &'a mut [u8], layout: &MemoryLayout) -> Self {
        let (header, rest) = mem.split_at_mut(memory::HEADER_SIZE);
        let (slots, rest) = rest.split_at_mut(layout.ring_bytes());
        let (event_slots, rest) = rest.split_at_mut(layout.event_ring_bytes());
        let (arena, event_arena) = rest.split_at_mut(layout.arena_bytes);
        let frame_start = header::get_u32(header, OFF_WRITE_CURSOR);
        Self {
            header,
            slots,
            event_slots,
            arena,
            event_arena,
            frame_start,
        }
    }

    fn mask(&self) -> usize {
        slot_mask(self.header)
    }

    /// Record the start of a new frame.
    pub fn begin_frame(&mut self) {
        self.frame_start = header::get_u32(self.header, OFF_WRITE_CURSOR);
    }

    /// Number of opcodes written since the last `begin_frame`.
    pub fn pending(&self) -> usize {
        let write = header::get_u32(self.header, OFF_WRITE_CURSOR);
        let mask = self.mask() as u32;
        (write.wrapping_sub(self.frame_start) & mask) as usize
    }

    /// End the frame: increments `frameCount`.
    pub fn end_frame(&mut self) {
        let count = header::get_u32(self.header, OFF_FRAME_COUNT);
        header::set_u32(self.header, OFF_FRAME_COUNT, count.wrapping_add(1));
    }

    /// Total frames produced so far.
    pub fn frame_count(&self) -> u32 {
        header::get_u32(self.header, OFF_FRAME_COUNT)
    }

    // --- TREE ---

    pub fn create_node(&mut self, node_id: u32, component_type: u16) -> Result<(), RingError> {
        ring_fn::push_create_node(self.slots, self.header, self.mask(), node_id, component_type)
    }

    pub fn delete_node(&mut self, node_id: u32) -> Result<(), RingError> {
        ring_fn::push_delete_node(self.slots, self.header, self.mask(), node_id)
    }

    pub fn insert_child(&mut self, parent: u32, child: u32, index: u32) -> Result<(), RingError> {
        ring_fn::push_insert_child(self.slots, self.header, self.mask(), parent, child, index)
    }

    pub fn remove_child(&mut self, parent: u32, child: u32) -> Result<(), RingError> {
        ring_fn::push_remove_child(self.slots, self.header, self.mask(), parent, child)
    }

    pub fn move_child(
        &mut self,
        parent: u32,
        child: u32,
        new_index: u32,
    ) -> Result<(), RingError> {
        ring_fn::push_move_child(self.slots, self.header, self.mask(), parent, child, new_index)
    }

    // --- Raw opcode push (guest → host) ---

    /// Push a caller-built raw opcode into the ring. This is the escape hatch
    /// for foreign hosts (C ABI / FFM) that construct the 16-byte opcode
    /// themselves and write it into the shared ring in place.
    pub fn push_opcode(&mut self, op: Opcode) -> Result<(), RingError> {
        ring_fn::push(self.slots, self.header, self.mask(), &op)
    }

    // --- STYLE ---

    pub fn set_property(
        &mut self,
        node_id: u32,
        property_id: u16,
        value_type: u8,
        value: u32,
    ) -> Result<(), RingError> {
        ring_fn::push_set_property(
            self.slots,
            self.header,
            self.mask(),
            node_id,
            property_id,
            value_type,
            value,
        )
    }

    /// Set a node's text content via the arena.
    pub fn set_text(&mut self, node_id: u32, text: &str) -> Result<u32, RingError> {
        let arena_ref = arena_fn::alloc_str(self.arena, self.header, text).map_err(|_| RingError::Full)?;
        ring_fn::push_set_text(self.slots, self.header, self.mask(), node_id, arena_ref)?;
        Ok(arena_ref)
    }

    /// Set a node's date value via `PARAMETER::SET_DATE` (draft): `days` since the
    /// epoch (I32, pre-1970 negative) and `millis` of day (U32, 0..86,400,000).
    pub fn set_date(&mut self, node_id: u32, days: i32, millis: u32) -> Result<(), RingError> {
        let op = Opcode::new(
            category::PARAMETER,
            crate::parameter::SET_DATE,
            0,
            node_id,
            days as u32,
            millis,
        );
        ring_fn::push(self.slots, self.header, self.mask(), &op)
    }

    /// Override a design token globally via `PARAMETER::SET_DESIGN_TOKEN`:
    /// `path` (arena string) → `value` of `value_type`. Applies everywhere the
    /// renderer resolves tokens (spec/TOKENS.md). A `dark.`-prefixed path
    /// overrides the dark variant. Returns the arena offset of the path.
    pub fn set_design_token(
        &mut self,
        path: &str,
        value_type: u8,
        value: u32,
    ) -> Result<u32, RingError> {
        let arena_ref = arena_fn::alloc_str(self.arena, self.header, path)
            .map_err(|_| RingError::Full)?;
        let op = Opcode::new(
            category::PARAMETER,
            crate::parameter::SET_DESIGN_TOKEN,
            0,
            arena_ref,
            u32::from(value_type),
            value,
        );
        ring_fn::push(self.slots, self.header, self.mask(), &op)?;
        Ok(arena_ref)
    }

    /// Override a design token with a **STRING value** via `PARAMETER::SET_DESIGN_TOKEN`
    /// (spec/TOKENS.md): both the token path and the value are arena strings —
    /// `A` = path ref, `B` = `STRING` value type, `C` = value ref. Returns the
    /// arena offset of the path.
    pub fn set_design_token_string(
        &mut self,
        path: &str,
        value: &str,
    ) -> Result<u32, RingError> {
        let arena_ref = arena_fn::alloc_str(self.arena, self.header, path)
            .map_err(|_| RingError::Full)?;
        let value_ref = arena_fn::alloc_str(self.arena, self.header, value)
            .map_err(|_| RingError::Full)?;
        let op = Opcode::new(
            category::PARAMETER,
            crate::parameter::SET_DESIGN_TOKEN,
            0,
            arena_ref,
            u32::from(crate::value_type::STRING),
            value_ref,
        );
        ring_fn::push(self.slots, self.header, self.mask(), &op)?;
        Ok(arena_ref)
    }

    /// Allocate arbitrary bytes into the arena, returning the offset.
    pub fn alloc(&mut self, bytes: &[u8]) -> Result<u32, ArenaError> {
        arena_fn::alloc(self.arena, self.header, bytes)
    }

    /// Allocate a string into the arena, returning the offset.
    pub fn alloc_str(&mut self, s: &str) -> Result<u32, ArenaError> {
        arena_fn::alloc_str(self.arena, self.header, s)
    }

    // --- META ---

    pub fn reset(&mut self) -> Result<(), RingError> {
        ring_fn::push_reset(self.slots, self.header, self.mask())
    }

    // --- EVENT ingress (host → guest) ---

    /// Drain all pending raw-input events written by the host into the event
    /// ring, decoding each 16-byte `EVENT` opcode into a typed [`Event`].
    ///
    /// The guest (app/engine) polls this to receive renderer-reported inputs.
    pub fn drain_events(&mut self) -> Vec<Event> {
        let mask = self.mask();
        let ops = ring_fn::read_events(&self.event_slots, self.header, mask, usize::MAX);
        ops.into_iter()
            .filter_map(|op| crate::events::decode_event(&op, self.event_arena))
            .collect()
    }

    /// Drain raw `EVENT`-category opcodes from the host → guest event ring as
    /// their 16-byte wire form (no decoding). Used by foreign hosts that want
    /// the raw bytes.
    pub fn drain_event_opcodes(&mut self) -> Vec<Opcode> {
        let mask = self.mask();
        ring_fn::read_events(&self.event_slots, self.header, mask, usize::MAX)
    }

    /// Drain at most `max` raw `EVENT`-category opcodes, leaving the rest
    /// pending for a later call (so a caller with a fixed-size buffer never
    /// drops events it couldn't copy).
    pub fn drain_event_opcodes_max(&mut self, max: usize) -> Vec<Opcode> {
        let mask = self.mask();
        ring_fn::read_events(&self.event_slots, self.header, mask, max)
    }

    /// The host → guest event arena bump cursor (diagnostics/tests).
    pub fn event_arena_cursor(&self) -> u32 {
        header::get_u32(self.header, memory::OFF_EVENT_ARENA_CURSOR)
    }

    /// Push a raw `EVENT`-category opcode into the host → guest event ring.
    ///
    /// This is the host-side write (the renderer reporting an input). Exposed
    /// here (guest can hold the ring too) so a C-ABI shim can inject raw event
    /// opcodes without round-tripping through the typed [`Event`] enum.
    pub fn push_event_opcode(&mut self, op: Opcode) -> Result<(), RingError> {
        ring_fn::push_event(self.event_slots, self.header, self.mask(), &op)
    }
}

// ---------------------------------------------------------------------------
// Host (consumer)
// ---------------------------------------------------------------------------

/// A completed frame produced by the guest: a sequence of opcodes plus arena
/// access.
///
/// `Frame` is the **transport seam**: both the shared-memory ring (`Host`) and
/// network batch decoders produce frame views, so consumers such as
/// `RenderTree::apply_frame` work identically regardless of origin.
pub struct Frame<'a> {
    slots: &'a [u8],
    arena: &'a [u8],
    start: usize,
    end: usize,
}

impl<'a> Frame<'a> {
    /// Construct a frame view over contiguous 16-byte opcode slots plus an
    /// arena region (`start`/`end` are byte offsets into `slots`).
    ///
    /// Used by transport backends to produce `Frame` views over their own
    /// buffers; `Host` builds these internally from the ring.
    pub fn from_parts(slots: &'a [u8], arena: &'a [u8], start: usize, end: usize) -> Self {
        Self {
            slots,
            arena,
            start,
            end,
        }
    }

    /// Iterate the opcodes in this frame, in order.
    pub fn opcodes(&self) -> FrameIter<'a> {
        FrameIter {
            slots: self.slots,
            cursor: self.start,
            end: self.end,
        }
    }

    /// Number of opcodes in the frame.
    pub fn len(&self) -> usize {
        (self.end - self.start) / Opcode::SIZE
    }

    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }

    /// Read an arena entry by offset (see `Guest::alloc` / `Guest::alloc_str`).
    pub fn arena_get(&self, offset: u32) -> Result<&'a [u8], ArenaError> {
        arena_fn::get(self.arena, offset)
    }

    /// Read an arena string by offset.
    pub fn arena_str(&self, offset: u32) -> Result<&'a str, ArenaError> {
        arena_fn::get_str(self.arena, offset)
    }

    /// The full arena region (for transport serialization of arena deltas).
    pub fn arena(&self) -> &'a [u8] {
        self.arena
    }
}

/// Iterator over the opcodes in a `Frame`.
pub struct FrameIter<'a> {
    slots: &'a [u8],
    cursor: usize,
    end: usize,
}

impl<'a> Iterator for FrameIter<'a> {
    type Item = Opcode;

    fn next(&mut self) -> Option<Opcode> {
        if self.cursor >= self.end {
            return None;
        }
        let bytes: [u8; 16] = self.slots[self.cursor..self.cursor + 16].try_into().unwrap();
        self.cursor += 16;
        Some(Opcode::from_bytes(&bytes))
    }
}

/// Consumer side: the host renderer reading completed frames.
///
/// SPSC contract: the host never reads past the producer's published
/// `writeCursor`, and the producer never overwrites an unconsumed `readCursor`.
/// If the host is slow, multiple produced frames are coalesced into one `Frame`
/// (opcodes remain in order); the `frameCount` simply signals "new data".
pub struct Host<'a> {
    header: &'a mut [u8],
    slots: &'a [u8],
    /// Host → guest event ring (host writes raw inputs here).
    event_slots: &'a mut [u8],
    arena: &'a [u8],
    /// Host → guest event arena (host-owned bump; the host writes event string
    /// payloads here, e.g. `TEXT_CHANGED` text over the shared ring).
    event_arena: &'a mut [u8],
    /// Number of frames already consumed by this host view.
    consumed: u32,
}

impl<'a> Host<'a> {
    /// Create a host view over the linear memory.
    pub fn new(mem: &'a mut [u8], layout: &MemoryLayout) -> Self {
        let (header, rest) = mem.split_at_mut(memory::HEADER_SIZE);
        let (slots, rest) = rest.split_at_mut(layout.ring_bytes());
        let (event_slots, rest) = rest.split_at_mut(layout.event_ring_bytes());
        let (arena, event_arena) = rest.split_at_mut(layout.arena_bytes);
        let slots: &'a [u8] = slots;
        let arena: &'a [u8] = arena;
        Self {
            header,
            slots,
            event_slots,
            arena,
            event_arena,
            consumed: 0,
        }
    }

    /// Total frames produced by the guest (for diagnostics).
    pub fn frame_count(&self) -> u32 {
        header::get_u32(self.header, OFF_FRAME_COUNT)
    }

    /// Consume all frames produced since the last call, advancing `readCursor`.
    ///
    /// Returns at most one `Frame` (all new opcodes since the last drain). If
    /// the producer produced multiple frames before this call they coalesce.
    pub fn frames(&mut self) -> Vec<Frame<'a>> {
        let frame_count = header::get_u32(self.header, OFF_FRAME_COUNT);
        if self.consumed >= frame_count {
            return Vec::new();
        }
        let mask = slot_mask(self.header);
        let start = ring_fn::cursor(self.header, OFF_READ_CURSOR, mask);
        let end = ring_fn::cursor(self.header, OFF_WRITE_CURSOR, mask);

        // The masked write cursor can wrap past the read cursor (the producer
        // laps the reader numerically). The pending run is counted modulo the
        // ring size; a plain `end < start` guard would permanently deadlock the
        // ring at the wrap boundary (the reader never advances again). A
        // wrapped run is drained in two calls — first the [start..slots) head,
        // then the [0..end) tail — advancing `readCursor` between them, so the
        // opcodes still reach the consumer in ring order.
        let count = end.wrapping_sub(start) & mask;
        if count == 0 {
            return Vec::new();
        }
        let run_end = (start + count) & mask; // exclusive slot index of the full run
        let (drain_to_slot, frame_byte_end) = if run_end > start {
            (run_end, run_end * Opcode::SIZE)
        } else {
            (0, self.slots.len()) // wrapped: drain the head now, the tail next call
        };

        let frames = vec![Frame {
            slots: self.slots,
            arena: self.arena,
            start: start * Opcode::SIZE,
            end: frame_byte_end,
        }];

        header::set_u32(self.header, OFF_READ_CURSOR, drain_to_slot as u32);
        self.consumed = frame_count;

        // A `META::RESET` invalidates prior state: reset the host → guest event
        // arena cursor so the host starts bump-allocating fresh (matching the
        // guest arena's reset-on-RESET semantics).
        let drained_end_byte = if drain_to_slot > start {
            drain_to_slot * Opcode::SIZE
        } else {
            self.slots.len() // wrapped head: drained [start .. ring end)
        };
        let reset_seen = self.slots[start * Opcode::SIZE..drained_end_byte]
            .chunks_exact(Opcode::SIZE)
            .any(|slot| {
                let bytes: [u8; 16] = slot.try_into().unwrap();
                let op = Opcode::from_bytes(&bytes);
                op.category() == category::META && op.command() == crate::meta::RESET
            });
        if reset_seen {
            memory::header::set_u32(self.header, memory::OFF_EVENT_ARENA_CURSOR, 0);
        }

        frames
    }

    /// Write a raw-input event (host → guest) into the event ring, encoded as a
    /// 16-byte `EVENT` opcode. The renderer reports inputs here; the guest
    /// drains them via [`Guest::drain_events`].
    ///
    /// `TextChanged` text and `Navigate` URLs are bump-allocated into the
    /// host → guest **event arena** and referenced by their absolute offset in
    /// `B` (the `SET_TEXT` shared-memory convention); the guest resolves them
    /// from the same region. If the event arena is full the event is rejected
    /// with [`RingError::Full`] (backpressure).
    pub fn send_event(&mut self, event: &Event) -> Result<(), RingError> {
        let mask = slot_mask(self.header);
        let op = match event {
            Event::TextChanged { target, value } => {
                let offset = crate::arena::alloc_event(&mut self.event_arena, self.header, value.as_bytes())
                    .map_err(|_| RingError::Full)?;
                Opcode::new(category::EVENT, crate::event::TEXT_CHANGED, 0, *target, offset, 0)
            }
            Event::Navigate { url: Some(url) } => {
                let offset = crate::arena::alloc_event(&mut self.event_arena, self.header, url.as_bytes())
                    .map_err(|_| RingError::Full)?;
                Opcode::new(
                    category::EVENT,
                    crate::event::NAVIGATE,
                    crate::flag::NAVIGATE_URL,
                    0,
                    offset,
                    0,
                )
            }
            _ => event.encode(),
        };
        ring_fn::push_event(self.event_slots, self.header, mask, &op)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::constants::{category, component_type, property_id, value_type};
    use alloc::vec;

    #[test]
    fn guest_host_round_trip() {
        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        let mut guest = Guest::new(&mut mem, &layout);
        guest.begin_frame();
        guest.create_node(1, component_type::VSTACK).unwrap();
        guest.insert_child(0, 1, APPEND).unwrap();
        guest.set_property(1, property_id::SPACING, value_type::F32, 4.0f32.to_bits()).unwrap();
        guest
            .set_property(1, property_id::PADDING, value_type::F32, 8.0f32.to_bits())
            .unwrap();
        let arena_ref = guest.set_text(1, "Hello").unwrap();
        assert_eq!(guest.pending(), 5);
        guest.end_frame();
        assert_eq!(guest.frame_count(), 1);

        let mut host = Host::new(&mut mem, &layout);
        assert_eq!(host.frame_count(), 1);
        let frames = host.frames();
        assert_eq!(frames.len(), 1);
        let frame = &frames[0];
        let ops: Vec<Opcode> = frame.opcodes().collect();
        assert_eq!(ops.len(), 5);
        assert_eq!(ops[0].category(), category::TREE);
        assert_eq!(ops[0].a(), 1);
        assert_eq!(ops[1].a(), 0); // parent root
        assert_eq!(ops[2].b() as u16, property_id::SPACING);
        assert_eq!(ops[2].c_f32(), 4.0);
        assert_eq!(frame.arena_str(arena_ref).unwrap(), "Hello");
        // Second drain is empty.
        assert!(host.frames().is_empty());
    }

    #[test]
    fn set_design_token_round_trips() {
        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        let mut guest = Guest::new(&mut mem, &layout);
        guest.begin_frame();
        let arena_ref = guest
            .set_design_token("color.primary", value_type::COLOR, 0xFF_2563EB)
            .unwrap();
        let dark_ref = guest
            .set_design_token("dark.color.primary", value_type::COLOR, 0xFF_60A5FA)
            .unwrap();
        guest.end_frame();

        let mut host = Host::new(&mut mem, &layout);
        let frame = &host.frames()[0];
        let ops: Vec<Opcode> = frame.opcodes().collect();
        assert_eq!(ops.len(), 2);
        assert_eq!(ops[0].category(), category::PARAMETER);
        assert_eq!(ops[0].command(), parameter::SET_DESIGN_TOKEN);
        assert_eq!(ops[0].b(), u32::from(value_type::COLOR));
        assert_eq!(ops[0].c(), 0xFF_2563EB);
        assert_eq!(frame.arena_str(arena_ref).unwrap(), "color.primary");
        assert_eq!(frame.arena_str(dark_ref).unwrap(), "dark.color.primary");
    }

    #[test]
    fn set_design_token_string_round_trips() {
        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        let mut guest = Guest::new(&mut mem, &layout);
        guest.begin_frame();
        let path_ref = guest.set_design_token_string("font.body.family", "Inter").unwrap();
        guest.end_frame();

        let mut host = Host::new(&mut mem, &layout);
        let frame = &host.frames()[0];
        let ops: Vec<Opcode> = frame.opcodes().collect();
        assert_eq!(ops.len(), 1);
        assert_eq!(ops[0].category(), category::PARAMETER);
        assert_eq!(ops[0].command(), parameter::SET_DESIGN_TOKEN);
        assert_eq!(ops[0].b(), u32::from(value_type::STRING));
        assert_eq!(frame.arena_str(ops[0].a()).unwrap(), "font.body.family");
        assert_eq!(frame.arena_str(ops[0].c()).unwrap(), "Inter");
        assert_eq!(path_ref, ops[0].a());
    }

    #[test]
    fn ring_full_is_reported() {
        let mut layout = MemoryLayout::default();
        layout.slot_count = 4; // tiny ring
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        let mut guest = Guest::new(&mut mem, &layout);
        // 4 slots -> capacity is slot_count-1 = 3 opcodes before full.
        assert!(guest.create_node(1, component_type::VSTACK).is_ok());
        assert!(guest.create_node(2, component_type::HSTACK).is_ok());
        assert!(guest.create_node(3, component_type::TEXT).is_ok());
        assert_eq!(
            guest.create_node(4, component_type::TEXT).unwrap_err(),
            RingError::Full
        );
    }

    #[test]
    fn host_event_round_trips_to_guest() {
        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        // Host (renderer) writes an event.
        let mut host = Host::new(&mut mem, &layout);
        host.send_event(&Event::PointerUp {
            target: 4,
            x: 10.0,
            y: 20.0,
            secondary: false,
        })
        .unwrap();
        drop(host);

        // Guest drains and decodes it.
        let mut guest = Guest::new(&mut mem, &layout);
        let events = guest.drain_events();
        assert_eq!(
            events,
            vec![Event::PointerUp {
                target: 4,
                x: 10.0,
                y: 20.0,
                secondary: false,
            }]
        );
        // Second drain is empty.
        assert!(guest.drain_events().is_empty());
    }

    #[test]
    fn drain_events_skips_unknown_event_commands() {
        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        // Write a valid event into the event ring, then release the host borrow.
        {
            let mut host = Host::new(&mut mem, &layout);
            host.send_event(&Event::PointerUp {
                target: 4,
                x: 10.0,
                y: 20.0,
                secondary: false,
            })
            .unwrap();
        }

        // Push a second, malformed EVENT opcode directly into the ring.
        let mask = slot_mask(&mem[..memory::HEADER_SIZE]);
        let (header, rest) = mem.split_at_mut(memory::HEADER_SIZE);
        let (_slots, rest) = rest.split_at_mut(layout.ring_bytes());
        let (event_slots, _arena) = rest.split_at_mut(layout.event_ring_bytes());
        let bogus = Opcode::new(crate::category::EVENT, 0xFF, 0, 1, 0, 0);
        ring_fn::push_event(event_slots, header, mask, &bogus).unwrap();

        // The guest sees only the decodable event; the unknown command is dropped.
        let mut guest = Guest::new(&mut mem, &layout);
        let events = guest.drain_events();
        assert_eq!(events.len(), 1);
        assert_eq!(
            events[0],
            Event::PointerUp {
                target: 4,
                x: 10.0,
                y: 20.0,
                secondary: false,
            }
        );
    }

    #[test]
    fn host_frames_survives_a_wrapped_write_cursor() {
        let layout = MemoryLayout {
            slot_count: 4,
            arena_bytes: MemoryLayout::default().arena_bytes,
            event_arena_bytes: MemoryLayout::default().event_arena_bytes,
        };
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        // Guest writes three opcodes (slots 0,1,2; capacity is slot_count-1).
        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            guest.create_node(1, component_type::VSTACK).unwrap();
            guest.create_node(2, component_type::HSTACK).unwrap();
            guest.create_node(3, component_type::TEXT).unwrap();
            guest.end_frame();
        }
        // Host drains everything -> readCursor = 3.
        {
            let mut host = Host::new(&mut mem, &layout);
            assert_eq!(host.frames().len(), 1);
        }
        // Guest writes one more opcode -> slot 3, writeCursor wraps 3 -> 0.
        // Now masked write(0) < masked read(3) — the wrap that used to
        // permanently deadlock the frame ring.
        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            guest.create_node(4, component_type::TEXT).unwrap();
            guest.end_frame();
        }

        // The wrapped run is the head [3..4) + a (here empty) tail [0..0): the
        // first read drains the head in ring order...
        let mut host = Host::new(&mut mem, &layout);
        let frames = host.frames();
        assert_eq!(frames.len(), 1);
        assert_eq!(frames[0].opcodes().count(), 1);
        assert_eq!(frames[0].opcodes().next().unwrap().a(), 4);
        // ...and readCursor advanced to 0, so nothing is left pending.
        assert!(host.frames().is_empty());
        // And the producer can wrap again without ever deadlocking.
        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            guest.create_node(5, component_type::TEXT).unwrap();
            guest.end_frame();
        }
        let mut host = Host::new(&mut mem, &layout);
        assert_eq!(host.frames()[0].opcodes().next().unwrap().a(), 5);
    }

    #[test]
    fn icon_vocabulary_names_are_canonical_and_unique() {
        use crate::constants::icon;
        use alloc::collections::BTreeSet;

        let mut seen = BTreeSet::new();
        for name in icon::NAMES {
            assert!(icon::is_canonical(name), "{} not canonical", name);
            assert!(seen.insert(name), "duplicate canonical icon {}", name);
        }
        // Every documented constant is in the canonical list.
        for name in [
            icon::HOME,
            icon::SETTINGS,
            icon::PLAY,
            icon::SKIP_FORWARD,
            icon::VOLUME_MUTE,
            icon::FILTER,
        ] {
            assert!(icon::is_canonical(name));
        }
        assert!(!icon::is_canonical("definitely-not-an-icon"));
    }
}
