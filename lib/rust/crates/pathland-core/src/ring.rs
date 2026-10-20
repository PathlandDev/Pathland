//! Producer-side ring helpers operating on the ring region and header cursors.
//!
//! The ring is a power-of-two slot array. The guest writes at `writeCursor` and
//! the host reads from `readCursor`. Both cursors live in the header block.

use alloc::vec::Vec;

use crate::opcode::Opcode;
use crate::{constants, memory, memory::header};

/// Errors produced by the ring producer.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RingError {
    /// The ring is full (consumer has not caught up). The producer SHOULD
    /// publish a frame before emitting further opcodes.
    Full,
}

/// Append `op` to the ring at `writeCursor` and advance the cursor.
///
/// `slots` is the full ring region; `mask` wraps the cursor. Fails with
/// `RingError::Full` when the next slot equals `readCursor`.
#[inline]
pub(crate) fn push(slots: &mut [u8], header: &mut [u8], mask: usize, op: &Opcode) -> Result<(), RingError> {
    let write = cursor(header, memory::OFF_WRITE_CURSOR, mask);
    let next = (write + 1) & mask;
    if next == cursor(header, memory::OFF_READ_CURSOR, mask) {
        return Err(RingError::Full);
    }
    let bytes = op.to_bytes();
    let off = write * Opcode::SIZE;
    slots[off..off + Opcode::SIZE].copy_from_slice(&bytes);
    header::set_u32(header, memory::OFF_WRITE_CURSOR, next as u32);
    Ok(())
}

/// Read the current cursor value for a header offset, masked.
#[inline]
pub(crate) fn cursor(header: &[u8], off: usize, mask: usize) -> usize {
    header::get_u32(header, off) as usize & mask
}

/// Emit a CREATE_NODE opcode.
pub(crate) fn push_create_node(
    slots: &mut [u8],
    header: &mut [u8],
    mask: usize,
    node_id: u32,
    component_type: u16,
) -> Result<(), RingError> {
    push(
        slots,
        header,
        mask,
        &Opcode::new(
            constants::category::TREE,
            constants::tree::CREATE_NODE,
            0,
            node_id,
            component_type as u32,
            0,
        ),
    )
}

pub(crate) fn push_delete_node(
    slots: &mut [u8],
    header: &mut [u8],
    mask: usize,
    node_id: u32,
) -> Result<(), RingError> {
    push(
        slots,
        header,
        mask,
        &Opcode::new(constants::category::TREE, constants::tree::DELETE_NODE, 0, node_id, 0, 0),
    )
}

pub(crate) fn push_insert_child(
    slots: &mut [u8],
    header: &mut [u8],
    mask: usize,
    parent: u32,
    child: u32,
    index: u32,
) -> Result<(), RingError> {
    push(
        slots,
        header,
        mask,
        &Opcode::new(
            constants::category::TREE,
            constants::tree::INSERT_CHILD,
            0,
            parent,
            child,
            index,
        ),
    )
}

pub(crate) fn push_remove_child(
    slots: &mut [u8],
    header: &mut [u8],
    mask: usize,
    parent: u32,
    child: u32,
) -> Result<(), RingError> {
    push(
        slots,
        header,
        mask,
        &Opcode::new(
            constants::category::TREE,
            constants::tree::REMOVE_CHILD,
            0,
            parent,
            child,
            0,
        ),
    )
}

pub(crate) fn push_move_child(
    slots: &mut [u8],
    header: &mut [u8],
    mask: usize,
    parent: u32,
    child: u32,
    new_index: u32,
) -> Result<(), RingError> {
    push(
        slots,
        header,
        mask,
        &Opcode::new(
            constants::category::TREE,
            constants::tree::MOVE_CHILD,
            0,
            parent,
            child,
            new_index,
        ),
    )
}

pub(crate) fn push_set_property(
    slots: &mut [u8],
    header: &mut [u8],
    mask: usize,
    node_id: u32,
    property_id: u16,
    value_type: u8,
    value: u32,
) -> Result<(), RingError> {
    push(
        slots,
        header,
        mask,
        &Opcode::new(
            constants::category::PARAMETER,
            constants::parameter::SET_PROPERTY,
            0,
            node_id,
            ((value_type as u32) << 16) | property_id as u32,
            value,
        ),
    )
}

pub(crate) fn push_set_text(
    slots: &mut [u8],
    header: &mut [u8],
    mask: usize,
    node_id: u32,
    arena_ref: u32,
) -> Result<(), RingError> {
    push(
        slots,
        header,
        mask,
        &Opcode::new(
            constants::category::PARAMETER,
            constants::parameter::SET_TEXT,
            0,
            node_id,
            arena_ref,
            0,
        ),
    )
}

pub(crate) fn push_reset(
    slots: &mut [u8],
    header: &mut [u8],
    mask: usize,
) -> Result<(), RingError> {
    push(
        slots,
        header,
        mask,
        &Opcode::new(constants::category::META, constants::meta::RESET, 0, 0, 0, 0),
    )
}

// ---------------------------------------------------------------------------
// Host → guest event ring
// ---------------------------------------------------------------------------

/// Append an `EVENT`-category opcode to the host → guest event ring.
///
/// The host (renderer) writes at `eventWriteCursor`; the guest reads from
/// `eventReadCursor`. Same 16-byte slot layout as the guest → host ring.
#[inline]
pub(crate) fn push_event(
    event_slots: &mut [u8],
    header: &mut [u8],
    mask: usize,
    op: &Opcode,
) -> Result<(), RingError> {
    let write = cursor(header, memory::OFF_EVENT_WRITE_CURSOR, mask);
    let next = (write + 1) & mask;
    if next == cursor(header, memory::OFF_EVENT_READ_CURSOR, mask) {
        return Err(RingError::Full);
    }
    let bytes = op.to_bytes();
    let off = write * Opcode::SIZE;
    event_slots[off..off + Opcode::SIZE].copy_from_slice(&bytes);
    header::set_u32(header, memory::OFF_EVENT_WRITE_CURSOR, next as u32);
    Ok(())
}

/// Drain pending `EVENT`-category opcodes from the host → guest event ring,
/// advancing `eventReadCursor`. The guest (app/engine) reads raw inputs here.
///
/// The masked cursors wrap independently — the writer can lap the reader
/// numerically — so the pending count is computed modulo the ring size. A
/// plain `write < read` guard would permanently deadlock the ring the moment
/// the writer wraps past the reader (the reader never advances, the writer is
/// stuck "full", and every drain returns empty). `max` bounds how many opcodes
/// are drained (the rest stay pending for the next call).
#[inline]
pub(crate) fn read_events(
    event_slots: &[u8],
    header: &mut [u8],
    mask: usize,
    max: usize,
) -> Vec<Opcode> {
    let read = cursor(header, memory::OFF_EVENT_READ_CURSOR, mask);
    let write = cursor(header, memory::OFF_EVENT_WRITE_CURSOR, mask);
    let count = write.wrapping_sub(read) & mask;
    let n = count.min(max);
    if n == 0 {
        return Vec::new();
    }
    let mut out = Vec::with_capacity(n);
    for i in 0..n {
        let slot = (read + i) & mask;
        let off = slot * Opcode::SIZE;
        let bytes: [u8; Opcode::SIZE] =
            event_slots[off..off + Opcode::SIZE].try_into().unwrap();
        out.push(Opcode::from_bytes(&bytes));
    }
    header::set_u32(header, memory::OFF_EVENT_READ_CURSOR, ((read + n) & mask) as u32);
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::memory::header;
    use alloc::vec;

    /// A tiny initialized linear memory with `slot_count` slots and a bare
    /// arena.
    fn tiny_mem(slot_count: usize) -> Vec<u8> {
        let layout = crate::memory::MemoryLayout {
            slot_count,
            arena_bytes: 64,
            event_arena_bytes: 64,
        };
        let mut mem = vec![0u8; layout.total_bytes()];
        crate::init_memory(&mut mem, &layout);
        mem
    }

    #[test]
    fn push_reports_full_at_exact_capacity() {
        let slot_count = 4usize;
        let mut mem = tiny_mem(slot_count);
        let mask = slot_count - 1;

        // With a 4-slot ring, capacity is slot_count - 1 = 3 opcodes.
        let (head, rest) = mem.split_at_mut(crate::memory::HEADER_SIZE);
        let (slots, _rest) = rest.split_at_mut(slot_count * Opcode::SIZE);

        for i in 0..3 {
            push(slots, head, mask, &Opcode::new(crate::category::TREE, 1, 0, i, 0, 0)).unwrap();
        }
        assert_eq!(
            push(slots, head, mask, &Opcode::new(crate::category::TREE, 1, 0, 9, 0, 0)),
            Err(RingError::Full)
        );
        // writeCursor stopped at slot_count - 1, never wrapping.
        assert_eq!(header::get_u32(head, crate::memory::OFF_WRITE_CURSOR), 3);
    }

    #[test]
    fn cursor_masks_out_of_range_header_values() {
        let mut mem = tiny_mem(8);
        let (head, _) = mem.split_at_mut(crate::memory::HEADER_SIZE);
        header::set_u32(head, crate::memory::OFF_WRITE_CURSOR, 17);
        // mask = 7, so 17 & 7 = 1.
        assert_eq!(cursor(head, crate::memory::OFF_WRITE_CURSOR, 7), 1);
    }

    #[test]
    fn push_event_and_read_events_round_trip() {
        let slot_count = 4usize;
        let mut mem = tiny_mem(slot_count);
        let mask = slot_count - 1;

        let (head, rest) = mem.split_at_mut(crate::memory::HEADER_SIZE);
        let (_slots, rest) = rest.split_at_mut(slot_count * Opcode::SIZE);
        let (event_slots, _arena) = rest.split_at_mut(slot_count * Opcode::SIZE);

        let op = Opcode::new(crate::category::EVENT, crate::event::POINTER_UP, 0, 5, 0, 0);
        push_event(event_slots, head, mask, &op).unwrap();

        let drained = read_events(event_slots, head, mask, usize::MAX);
        assert_eq!(drained, vec![op]);
        // Second read is empty (readCursor advanced).
        assert!(read_events(event_slots, head, mask, usize::MAX).is_empty());
        assert_eq!(
            header::get_u32(head, crate::memory::OFF_EVENT_READ_CURSOR),
            header::get_u32(head, crate::memory::OFF_EVENT_WRITE_CURSOR)
        );
    }

    #[test]
    fn push_event_reports_full_and_recovers() {
        let slot_count = 2usize;
        let mut mem = tiny_mem(slot_count);
        let mask = slot_count - 1;

        let (head, rest) = mem.split_at_mut(crate::memory::HEADER_SIZE);
        let (_slots, rest) = rest.split_at_mut(slot_count * Opcode::SIZE);
        let (event_slots, _arena) = rest.split_at_mut(slot_count * Opcode::SIZE);

        let op = Opcode::new(crate::category::EVENT, crate::event::POINTER_UP, 0, 5, 0, 0);
        assert!(push_event(event_slots, head, mask, &op).is_ok());
        assert_eq!(
            push_event(event_slots, head, mask, &op),
            Err(RingError::Full)
        );

        // Guest drains, freeing the slot; the host can write again.
        let drained = read_events(event_slots, head, mask, usize::MAX);
        assert_eq!(drained.len(), 1);
        assert!(push_event(event_slots, head, mask, &op).is_ok());
    }

    #[test]
    fn read_events_drains_a_wrapped_producer() {
        let slot_count = 4usize;
        let mut mem = tiny_mem(slot_count);
        let mask = slot_count - 1;

        let (head, rest) = mem.split_at_mut(crate::memory::HEADER_SIZE);
        let (_slots, rest) = rest.split_at_mut(slot_count * Opcode::SIZE);
        let (event_slots, _arena) = rest.split_at_mut(slot_count * Opcode::SIZE);

        let op = Opcode::new(crate::category::EVENT, crate::event::POINTER_UP, 0, 5, 0, 0);
        // Write 3 events into slots 0,1,2 (capacity is slot_count - 1 = 3).
        for _ in 0..3 {
            push_event(event_slots, head, mask, &op).unwrap();
        }
        // Reader drains ONE (bounded), so readCursor advances to 1.
        assert_eq!(read_events(event_slots, head, mask, 1).len(), 1);
        // Writer can write again: slot 3, writeCursor wraps 3 -> 0. Now the
        // masked write(0) is numerically BELOW the masked read(1) — the wrap
        // that used to deadlock the ring.
        push_event(event_slots, head, mask, &op).unwrap();
        assert_eq!(
            header::get_u32(head, crate::memory::OFF_EVENT_READ_CURSOR),
            1
        );
        assert_eq!(
            header::get_u32(head, crate::memory::OFF_EVENT_WRITE_CURSOR),
            0
        );

        // The wrapped run [1..4) + [0..0) = slots 1,2,3 must drain in order,
        // and readCursor must advance to the (masked) write cursor.
        let drained = read_events(event_slots, head, mask, usize::MAX);
        assert_eq!(drained, vec![op; 3]);
        assert_eq!(
            header::get_u32(head, crate::memory::OFF_EVENT_READ_CURSOR),
            0
        );
        // And the writer can wrap again without ever deadlocking.
        assert!(push_event(event_slots, head, mask, &op).is_ok());
        assert_eq!(
            read_events(event_slots, head, mask, usize::MAX).len(),
            1
        );
    }
}
