//! Golden conformance vectors for the 16-byte opcode format.
//!
//! These byte arrays are the canonical wire representation (see
//! `spec/CONFORMANCE.md`). A conforming implementation MUST encode and decode
//! these exactly.

/// Vectors: (name, expected 16 bytes).
#[cfg(test)]
pub(crate) const VECTORS: &[(&str, [u8; 16])] = &[
    (
        "TREE:CREATE_NODE (id=1, VSTACK=0x0010)",
        [
            0x01, 0x01, 0x00, 0x00, // category TREE, command CREATE_NODE
            0x01, 0x00, 0x00, 0x00, // A = nodeId = 1
            0x10, 0x00, 0x00, 0x00, // B = componentType = 0x0010
            0x00, 0x00, 0x00, 0x00, // C = 0
        ],
    ),
    (
        "TREE:INSERT_CHILD (parent=0, child=1, index=MAX)",
        [
            0x01, 0x03, 0x00, 0x00, // category TREE, command INSERT_CHILD
            0x00, 0x00, 0x00, 0x00, // A = parentId = 0
            0x01, 0x00, 0x00, 0x00, // B = childId = 1
            0xFF, 0xFF, 0xFF, 0xFF, // C = APPEND
        ],
    ),
    (
        "TREE:MOVE_CHILD (parent=1, child=2, newIndex=0)",
        [
            0x01, 0x05, 0x00, 0x00, // category TREE, command MOVE_CHILD
            0x01, 0x00, 0x00, 0x00, // A = parentId = 1
            0x02, 0x00, 0x00, 0x00, // B = childId = 2
            0x00, 0x00, 0x00, 0x00, // C = newIndex = 0
        ],
    ),
    (
        "PARAMETER:SET_PROPERTY (id=1, SPACING=0x0001, valueType=F32, 4.0)",
        [
            0x02, 0x01, 0x00, 0x00, // category STYLE, command SET_PROPERTY
            0x01, 0x00, 0x00, 0x00, // A = nodeId = 1
            0x01, 0x00, 0x04, 0x00, // B = propertyId(0x0001) | valueType(0x04)<<16
            0x00, 0x00, 0x80, 0x40, // C = 4.0 (f32 LE: 0x40800000)
        ],
    ),
    (
        "PARAMETER:SET_PROPERTY (id=1, COLOR=0x100A, valueType=COLOR, rgba=0xFF0000FF)",
        [
            0x02, 0x01, 0x00, 0x00, // category STYLE, command SET_PROPERTY
            0x01, 0x00, 0x00, 0x00, // A = nodeId = 1
            0x0A, 0x10, 0x07, 0x00, // B = propertyId(0x100A) | valueType(0x07)<<16
            0xFF, 0x00, 0x00, 0xFF, // C = 0xFF0000FF (opaque blue)
        ],
    ),
    (
        "PARAMETER:SET_TEXT (id=1, arenaRef=0)",
        [
            0x02, 0x03, 0x00, 0x00, // category STYLE, command SET_TEXT
            0x01, 0x00, 0x00, 0x00, // A = nodeId = 1
            0x00, 0x00, 0x00, 0x00, // B = arenaRef = 0
            0x00, 0x00, 0x00, 0x00, // C = 0
        ],
    ),
    (
        "META:RESET",
        [
            0x04, 0x01, 0x00, 0x00, // category META, command RESET
            0x00, 0x00, 0x00, 0x00, // A = 0
            0x00, 0x00, 0x00, 0x00, // B = 0
            0x00, 0x00, 0x00, 0x00, // C = 0
        ],
    ),
    (
        "EVENT:POINTER_DOWN (target=5, x=10.0, y=20.0)",
        [
            0x03, 0x01, 0x00, 0x00, // category EVENT, command POINTER_DOWN
            0x05, 0x00, 0x00, 0x00, // A = targetId = 5
            0x00, 0x00, 0x20, 0x41, // B = 10.0 (f32 LE: 0x41200000)
            0x00, 0x00, 0xA0, 0x41, // C = 20.0 (f32 LE: 0x41A00000)
        ],
    ),
    (
        "EVENT:POINTER_MOVE (target=5, x=10.0, y=20.0, hovering)",
        [
            0x03, 0x02, 0x01, 0x00, // category EVENT, command POINTER_MOVE, HOVER_ENTER
            0x05, 0x00, 0x00, 0x00, // A = targetId = 5
            0x00, 0x00, 0x20, 0x41, // B = 10.0 (f32 LE: 0x41200000)
            0x00, 0x00, 0xA0, 0x41, // C = 20.0 (f32 LE: 0x41A00000)
        ],
    ),
    (
        "EVENT:POINTER_UP (target=5, x=10.0, y=20.0)",
        [
            0x03, 0x03, 0x00, 0x00, // category EVENT, command POINTER_UP
            0x05, 0x00, 0x00, 0x00, // A = targetId = 5
            0x00, 0x00, 0x20, 0x41, // B = 10.0 (f32 LE: 0x41200000)
            0x00, 0x00, 0xA0, 0x41, // C = 20.0 (f32 LE: 0x41A00000)
        ],
    ),
    (
        "EVENT:KEY_DOWN (target=5, keyCode='A'=0x41, modifiers=0x01, repeat)",
        [
            0x03, 0x04, 0x02, 0x00, // category EVENT, command KEY_DOWN, KEY_REPEAT
            0x05, 0x00, 0x00, 0x00, // A = targetId = 5
            0x41, 0x00, 0x00, 0x00, // B = keyCode = 0x41
            0x01, 0x00, 0x00, 0x00, // C = modifiers = 0x01
        ],
    ),
    (
        "EVENT:KEY_UP (target=5, keyCode='A'=0x41, modifiers=0x01)",
        [
            0x03, 0x05, 0x00, 0x00, // category EVENT, command KEY_UP
            0x05, 0x00, 0x00, 0x00, // A = targetId = 5
            0x41, 0x00, 0x00, 0x00, // B = keyCode = 0x41
            0x01, 0x00, 0x00, 0x00, // C = modifiers = 0x01
        ],
    ),
    (
        "PARAMETER:SET_DESIGN_TOKEN (path=\"color.primary\" arenaRef=0, valueType=COLOR=0x07, value=0xFF0000FF)",
        [
            0x02, 0x02, 0x00, 0x00, // category STYLE, command SET_DESIGN_TOKEN
            0x00, 0x00, 0x00, 0x00, // A = arenaRef = 0 ("color.primary")
            0x07, 0x00, 0x00, 0x00, // B = valueType = 0x07 (COLOR)
            0xFF, 0x00, 0x00, 0xFF, // C = 0xFF0000FF (opaque blue, 0xAARRGGBB)
        ],
    ),
    (
        "PARAMETER:SET_PROPERTY (id=1, COLOR=0x100A, valueType=DESIGN_TOKEN=0x08, arenaRef=0)",
        [
            0x02, 0x01, 0x00, 0x00, // category STYLE, command SET_PROPERTY
            0x01, 0x00, 0x00, 0x00, // A = nodeId = 1
            0x0A, 0x10, 0x08, 0x00, // B = (0x08 << 16) | 0x100A (DESIGN_TOKEN | COLOR)
            0x00, 0x00, 0x00, 0x00, // C = arenaRef = 0 ("color.primary")
        ],
    ),
    (
        "PARAMETER:SET_DESIGN_TOKEN (path=\"font.body.family\" arenaRef=0, valueType=STRING=0x05, valueArenaRef=20)",
        [
            0x02, 0x02, 0x00, 0x00, // category STYLE, command SET_DESIGN_TOKEN
            0x00, 0x00, 0x00, 0x00, // A = arenaRef = 0 ("font.body.family")
            0x05, 0x00, 0x00, 0x00, // B = valueType = 0x05 (STRING)
            0x14, 0x00, 0x00, 0x00, // C = arenaRef = 20 ("Inter")
        ],
    ),
    (
        "EVENT:NAVIGATE (URL=\"/users/42\" arenaRef=0, NAVIGATE_URL flag)",
        [
            0x03, 0x0E, 0x01, 0x00, // category EVENT, command NAVIGATE, NAVIGATE_URL
            0x00, 0x00, 0x00, 0x00, // A = 0 (global, no targetId)
            0x00, 0x00, 0x00, 0x00, // B = URL string offset = 0
            0x00, 0x00, 0x00, 0x00, // C = 0
        ],
    ),
    (
        "EVENT:NAVIGATE (back, no URL)",
        [
            0x03, 0x0E, 0x00, 0x00, // category EVENT, command NAVIGATE, no flag
            0x00, 0x00, 0x00, 0x00, // A = 0
            0x00, 0x00, 0x00, 0x00, // B = 0
            0x00, 0x00, 0x00, 0x00, // C = 0
        ],
    ),
    (
        "PARAMETER:SET_PROPERTY (id=1, ROUTE=0x2019, valueType=STRING=0x05, arenaRef=0)",
        [
            0x02, 0x01, 0x00, 0x00, // category STYLE, command SET_PROPERTY
            0x01, 0x00, 0x00, 0x00, // A = nodeId = 1
            0x19, 0x20, 0x05, 0x00, // B = (0x05 << 16) | 0x2019 (STRING | ROUTE)
            0x00, 0x00, 0x00, 0x00, // C = arenaRef = 0 ("/users/42")
        ],
    ),
    (
        "PARAMETER:SET_PROPERTY (id=1, TRANSITION=0x1031, valueType=F32=0x04, Slide=3)",
        [
            0x02, 0x01, 0x00, 0x00, // category STYLE, command SET_PROPERTY
            0x01, 0x00, 0x00, 0x00, // A = nodeId = 1
            0x31, 0x10, 0x04, 0x00, // B = (0x04 << 16) | 0x1031 (F32 | TRANSITION)
            0x00, 0x00, 0x40, 0x40, // C = 3.0 (f32 LE: 0x40400000) = Slide
        ],
    ),
    (
        "META:ENVIRONMENT (VIEWPORT_WIDTH=0x0001, width=800.0)",
        [
            0x04, 0x02, 0x00, 0x00, // category META, command ENVIRONMENT
            0x01, 0x00, 0x00, 0x00, // A = fieldId = 0x0001 (VIEWPORT_WIDTH)
            0x00, 0x00, 0x48, 0x44, // B = 800.0 (f32 LE: 0x44480000)
            0x00, 0x00, 0x00, 0x00, // C = 0
        ],
    ),
    (
        "META:ENVIRONMENT (ROUTE=0x0003, string offset 0)",
        [
            0x04, 0x02, 0x00, 0x00, // category META, command ENVIRONMENT
            0x03, 0x00, 0x00, 0x00, // A = fieldId = 0x0003 (ROUTE)
            0x00, 0x00, 0x00, 0x00, // B = string offset = 0 ("/users/42")
            0x00, 0x00, 0x00, 0x00, // C = 0
        ],
    ),
    (
        "PARAMETER:SET_PROPERTY (id=1, NAV_DEPTH=0x201A, valueType=U32=0x02, depth=3)",
        [
            0x02, 0x01, 0x00, 0x00, // category STYLE, command SET_PROPERTY
            0x01, 0x00, 0x00, 0x00, // A = nodeId = 1
            0x1A, 0x20, 0x02, 0x00, // B = (0x02 << 16) | 0x201A (U32 | NAV_DEPTH)
            0x03, 0x00, 0x00, 0x00, // C = depth = 3 (U32 LE)
        ],
    ),
    (
        "PARAMETER:SET_PROPERTY (id=1, NAV_CHROME=0x201B, valueType=F32=0x04, Custom=1)",
        [
            0x02, 0x01, 0x00, 0x00, // category STYLE, command SET_PROPERTY
            0x01, 0x00, 0x00, 0x00, // A = nodeId = 1
            0x1B, 0x20, 0x04, 0x00, // B = (0x04 << 16) | 0x201B (F32 | NAV_CHROME)
            0x00, 0x00, 0x80, 0x3F, // C = 1.0 (f32 LE: 0x3F800000) = Custom
        ],
    ),
    (
        "PARAMETER:SET_PROPERTY (id=1, GRID_COLUMNS=0x001E, valueType=F32=0x04, 2.0)",
        [
            0x02, 0x01, 0x00, 0x00, // category STYLE, command SET_PROPERTY
            0x01, 0x00, 0x00, 0x00, // A = nodeId = 1
            0x1E, 0x00, 0x04, 0x00, // B = (0x04 << 16) | 0x001E (F32 | GRID_COLUMNS)
            0x00, 0x00, 0x00, 0x40, // C = 2.0 (f32 LE: 0x40000000) = two columns
        ],
    ),
    (
        "TREE:CREATE_NODE (id=1, GRID_ROW=0x001D)",
        [
            0x01, 0x01, 0x00, 0x00, // category TREE, command CREATE_NODE
            0x01, 0x00, 0x00, 0x00, // A = nodeId = 1
            0x1D, 0x00, 0x00, 0x00, // B = componentType = 0x001D (GRID_ROW)
            0x00, 0x00, 0x00, 0x00, // C = 0
        ],
    ),
];

#[cfg(test)]
mod tests {
    use super::*;
    use crate::opcode::Opcode;
    use alloc::vec;
    use alloc::vec::Vec;

    #[test]
    fn vectors_encode_exactly() {
        for (name, expected) in VECTORS {
            let op = Opcode::from_bytes(expected);
            assert_eq!(op.to_bytes(), *expected, "encode mismatch: {name}");
        }
    }

    #[test]
    fn vectors_decode_exactly() {
        // Rebuild each vector from its fields and confirm the documented bytes.
        let cases: &[(u8, u8, u16, u32, u32, u32, [u8; 16])] = &[
            (0x01, 0x01, 0x0000, 1, 0x0010, 0, VECTORS[0].1),
            (0x01, 0x03, 0x0000, 0, 1, u32::MAX, VECTORS[1].1),
            (0x01, 0x05, 0x0000, 1, 2, 0, VECTORS[2].1),
            (
                0x02,
                0x01,
                0x0000,
                1,
                (0x04u32 << 16) | 0x0001,
                4.0f32.to_bits(),
                VECTORS[3].1,
            ),
            (
                0x02,
                0x01,
                0x0000,
                1,
                (0x07u32 << 16) | 0x100A,
                0xFF00_00FF,
                VECTORS[4].1,
            ),
            (0x02, 0x03, 0x0000, 1, 0, 0, VECTORS[5].1),
            (0x04, 0x01, 0x0000, 0, 0, 0, VECTORS[6].1),
            (
                0x03,
                0x01,
                0x0000,
                5,
                10.0f32.to_bits(),
                20.0f32.to_bits(),
                VECTORS[7].1,
            ),
            (
                0x03,
                0x02,
                0x0001,
                5,
                10.0f32.to_bits(),
                20.0f32.to_bits(),
                VECTORS[8].1,
            ),
            (
                0x03,
                0x03,
                0x0000,
                5,
                10.0f32.to_bits(),
                20.0f32.to_bits(),
                VECTORS[9].1,
            ),
            (0x03, 0x04, 0x0002, 5, 0x41, 0x01, VECTORS[10].1),
            (0x03, 0x05, 0x0000, 5, 0x41, 0x01, VECTORS[11].1),
            (0x02, 0x02, 0x0000, 0, 0x07, 0xFF00_00FF, VECTORS[12].1),
            (
                0x02,
                0x01,
                0x0000,
                1,
                (0x08u32 << 16) | 0x100A,
                0,
                VECTORS[13].1,
            ),
            (0x02, 0x02, 0x0000, 0, 0x05, 0x14, VECTORS[14].1),
            (0x03, 0x0E, 0x0001, 0, 0, 0, VECTORS[15].1),
            (0x03, 0x0E, 0x0000, 0, 0, 0, VECTORS[16].1),
            (
                0x02,
                0x01,
                0x0000,
                1,
                (0x05u32 << 16) | 0x2019,
                0,
                VECTORS[17].1,
            ),
            (
                0x02,
                0x01,
                0x0000,
                1,
                (0x04u32 << 16) | 0x1031,
                3.0f32.to_bits(),
                VECTORS[18].1,
            ),
            (0x04, 0x02, 0x0000, 0x0001, 800.0f32.to_bits(), 0, VECTORS[19].1),
            (0x04, 0x02, 0x0000, 0x0003, 0, 0, VECTORS[20].1),
            (
                0x02,
                0x01,
                0x0000,
                1,
                (0x02u32 << 16) | 0x201A,
                3,
                VECTORS[21].1,
            ),
            (
                0x02,
                0x01,
                0x0000,
                1,
                (0x04u32 << 16) | 0x201B,
                1.0f32.to_bits(),
                VECTORS[22].1,
            ),
            (
                0x02,
                0x01,
                0x0000,
                1,
                (0x04u32 << 16) | 0x001E,
                2.0f32.to_bits(),
                VECTORS[23].1,
            ),
            (
                0x01,
                0x01,
                0x0000,
                1,
                0x001D,
                0,
                VECTORS[24].1,
            ),
        ];
        for (cat, cmd, flags, a, b, c, expected) in cases {
            let op = Opcode::new(*cat, *cmd, *flags, *a, *b, *c);
            assert_eq!(op.to_bytes(), *expected);
        }
    }

    #[test]
    fn set_design_token_emits_vector_17_exactly() {
        // A fresh ring puts the "color.primary" arena entry at offset 0, so the
        // emitted SET_DESIGN_TOKEN opcode must equal the golden bytes (vector 17).
        use crate::{init_memory, Guest, Host, MemoryLayout};
        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        let mut guest = Guest::new(&mut mem, &layout);
        guest.begin_frame();
        guest
            .set_design_token("color.primary", crate::value_type::COLOR, 0xFF00_00FF)
            .unwrap();
        guest.end_frame();

        let mut host = Host::new(&mut mem, &layout);
        let ops: Vec<_> = host.frames()[0].opcodes().collect();
        assert_eq!(ops.len(), 1);
        assert_eq!(ops[0].to_bytes(), VECTORS[12].1, "vector 17 byte-exact");
    }
}
