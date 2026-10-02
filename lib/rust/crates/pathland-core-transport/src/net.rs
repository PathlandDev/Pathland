//! Network batch transport (remote path).
//!
//! Producer side serializes Frames into network batches (opcodes + arena delta);
//! the consumer decodes them with `BatchDecoder` (see `crate::batch`) back into
//! `Frame` views consumed by `RenderTree::apply_frame`. This is the remote/SSR
//! path where producer and consumer do **not** share memory.
//!
//! The wire format is unchanged from `crate::batch` (magic/version/flags/
//! frameCount/opcodeCount/opcodes/arenaDeltaLen/arena).

use alloc::vec::Vec;

use pathland_core::{Frame, Opcode};

use crate::batch::encode_batch;

/// Producer-side network batch encoder.
///
/// Buffers opcodes (from Frames) and serializes them into a batch with the
/// arena's used-arena slice. Use the `Batcher` helper (`crate::batching`) for a
/// time/size flush policy; this type is the bare encoder for explicit flushes.
#[derive(Debug, Default)]
pub struct NetBatchEncoder {
    opcodes: Vec<Opcode>,
}

impl NetBatchEncoder {
    /// Append a frame's opcodes to the pending batch.
    pub fn push_frame(&mut self, frame: &Frame<'_>) {
        self.opcodes.extend(frame.opcodes());
    }

    /// Append raw opcodes to the pending batch.
    pub fn push_opcodes(&mut self, opcodes: &[Opcode]) {
        self.opcodes.extend_from_slice(opcodes);
    }

    /// Serialize the pending opcodes with a provided used-arena slice, clearing
    /// the buffer. `frame_count` is the covering frame counter.
    pub fn encode_with_arena(&mut self, frame_count: u32, arena_used: &[u8]) -> Vec<u8> {
        let bytes = encode_batch(
            frame_count,
            crate::direction::GUEST_TO_HOST,
            &self.opcodes,
            arena_used,
        );
        self.opcodes.clear();
        bytes
    }

    /// Whether any opcodes are buffered.
    pub fn is_empty(&self) -> bool {
        self.opcodes.is_empty()
    }

    /// Number of buffered opcodes.
    pub fn pending(&self) -> usize {
        self.opcodes.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use pathland_core::{category, tree, Opcode};

    #[test]
    fn encodes_and_clears() {
        let opcodes = [
            Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, 0x0010, 0),
            Opcode::new(
                category::PARAMETER,
                0x01,
                0,
                1,
                0x0001 | (0x04 << 16),
                4.0f32.to_bits(),
            ),
        ];
        let mut enc = NetBatchEncoder::default();
        enc.push_opcodes(&opcodes);
        assert_eq!(enc.pending(), 2);
        let arena = [1u8, 0, 0, 0, b'a'];
        let bytes = enc.encode_with_arena(3, &arena);
        assert_eq!(enc.pending(), 0);
        assert!(!bytes.is_empty());
    }
}
