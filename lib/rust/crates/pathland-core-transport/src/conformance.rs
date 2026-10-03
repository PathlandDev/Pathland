//! Golden conformance vectors for the network batch format.
//!
//! These byte arrays are the canonical wire representation (see
//! `spec/CONFORMANCE.md`). A conforming implementation MUST encode and decode
//! these exactly.

#[cfg(test)]
mod tests {
    use crate::batch::{encode_batch, BatchDecoder};
    use pathland_core::{category, Opcode};

    /// The canonical bytes for `NETWORK:BATCH` vector 13 (see CONFORMANCE.md).
    const VECTOR_13: &[u8] = &[
        0x4C, 0x50, 0x4C, 0x50, // magic PLPL (LE)
        0x01, 0x00, // version 1
        0x00, 0x00, // flags guest->host
        0x01, 0x00, 0x00, 0x00, // sequence = 1
        0x01, 0x00, 0x00, 0x00, // opcodeCount = 1
        0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, // CREATE_NODE (id=1, VSTACK)
        0x06, 0x00, 0x00, 0x00, // arenaDeltaLen = 6
        0x02, 0x00, 0x00, 0x00, 0x48, 0x69, // arena entry [len=2]["Hi"]
    ];

    /// The canonical bytes for `NETWORK:BATCH` vector 14 (see CONFORMANCE.md).
    const VECTOR_14: &[u8] = &[
        0x4C, 0x50, 0x4C, 0x50, // magic PLPL (LE)
        0x01, 0x00, // version 1
        0x00, 0x00, // flags
        0x02, 0x00, 0x00, 0x00, // sequence = 2
        0x00, 0x00, 0x00, 0x00, // opcodeCount = 0
        0x00, 0x00, 0x00, 0x00, // arenaDeltaLen = 0
    ];

    /// The canonical bytes for `NETWORK:BATCH` vector 19 (see CONFORMANCE.md):
    /// sequence = 3, one DESIGN_TOKEN-referencing SET_PROPERTY, arena delta
    /// = `[len=13]["color.primary"]`.
    const VECTOR_19: &[u8] = &[
        0x4C, 0x50, 0x4C, 0x50, // magic PLPL (LE)
        0x01, 0x00, // version 1
        0x00, 0x00, // flags guest->host
        0x03, 0x00, 0x00, 0x00, // sequence = 3
        0x01, 0x00, 0x00, 0x00, // opcodeCount = 1
        // PARAMETER:SET_PROPERTY (id=1, COLOR=0x100A, valueType=DESIGN_TOKEN=0x08, arenaRef=0)
        0x02, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x0A, 0x10, 0x08, 0x00, 0x00, 0x00, 0x00,
        0x00,
        0x11, 0x00, 0x00, 0x00, // arenaDeltaLen = 17
        0x0D, 0x00, 0x00, 0x00, 0x63, 0x6F, 0x6C, 0x6F, 0x72, 0x2E, 0x70, 0x72, 0x69, 0x6D, 0x61,
        0x72, 0x79, // [len=13]["color.primary"]
    ];

    #[test]
    fn vector_13_encode_exactly() {
        let opcodes = [Opcode::new(category::TREE, 0x01, 0, 1, 0x0010, 0)];
        let arena_delta = [0x02, 0x00, 0x00, 0x00, 0x48, 0x69];
        let bytes = encode_batch(1, 0, &opcodes, &arena_delta);
        assert_eq!(bytes, VECTOR_13);
    }

    #[test]
    fn vector_14_encode_exactly() {
        let bytes = encode_batch(2, 0, &[], &[]);
        assert_eq!(bytes, VECTOR_14);
    }

    #[test]
    fn vector_19_encode_exactly() {
        // The opcode is conformance vector 18 (DESIGN_TOKEN-typed SET_PROPERTY).
        let op = Opcode::new(category::PARAMETER, 0x01, 0, 1, (0x08u32 << 16) | 0x100A, 0);
        let arena_delta: &[u8] = &[0x0D, 0x00, 0x00, 0x00, b'c', b'o', b'l', b'o', b'r', b'.', b'p',
            b'r', b'i', b'm', b'a', b'r', b'y'];
        let bytes = encode_batch(3, 0, &[op], arena_delta);
        assert_eq!(bytes, VECTOR_19, "vector 19 byte-exact");
    }

    #[test]
    fn vectors_decode_and_round_trip() {
        for bytes in [VECTOR_13, VECTOR_14, VECTOR_19] {
            let mut decoder = BatchDecoder::new();
            let batch = decoder.decode(bytes).unwrap();
            assert_eq!(batch.flags(), 0);
            let reencoded = {
                let opcodes: Vec<Opcode> = batch.opcodes().collect();
                // The decoder has already mirrored the arena delta; re-encoding
                // with an empty delta produces a valid follow-up batch.
                encode_batch(batch.sequence(), batch.flags(), &opcodes, &[])
            };
            // Decoding again (empty delta appended to mirror) must stay valid.
            let _ = decoder.decode(&reencoded).unwrap();
        }
    }
}
