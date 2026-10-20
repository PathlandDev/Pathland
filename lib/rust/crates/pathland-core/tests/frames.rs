//! Frame semantics over the shared ring: coalescing of multiple produced
//! frames, `pending()` accounting, `frame_count` monotonicity, the fresh-host
//! edge case, and value-type coverage for `set_property`.

use pathland_core::{
    component_type, init_memory, property_id, value_type, Guest, Host, MemoryLayout, Opcode,
    APPEND,
};

struct Memory {
    mem: Vec<u8>,
    layout: MemoryLayout,
}

impl Memory {
    fn new(layout: MemoryLayout) -> Self {
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);
        Self { mem, layout }
    }

    fn guest(&mut self) -> Guest<'_> {
        Guest::new(&mut self.mem, &self.layout)
    }

    fn host(&mut self) -> Host<'_> {
        Host::new(&mut self.mem, &self.layout)
    }
}

#[test]
fn multiple_frames_coalesce_into_one() {
    let mut m = Memory::new(MemoryLayout::default());

    // Frame 1: two opcodes.
    {
        let mut g = m.guest();
        g.begin_frame();
        g.create_node(1, component_type::VSTACK).unwrap();
        g.create_node(2, component_type::HSTACK).unwrap();
        g.end_frame();
        assert_eq!(g.frame_count(), 1);
    }
    // Frame 2: one opcode.
    {
        let mut g = m.guest();
        g.begin_frame();
        g.create_node(3, component_type::TEXT).unwrap();
        g.end_frame();
        assert_eq!(g.frame_count(), 2);
    }

    // A single drain returns one coalesced frame with all three opcodes, in order.
    let mut h = m.host();
    let frames = h.frames();
    assert_eq!(frames.len(), 1);
    let ops: Vec<Opcode> = frames[0].opcodes().collect();
    assert_eq!(ops.len(), 3);
    assert_eq!(ops[0].a(), 1);
    assert_eq!(ops[1].a(), 2);
    assert_eq!(ops[2].a(), 3);

    // Subsequent drains are empty.
    assert!(h.frames().is_empty());
}

#[test]
fn fresh_host_after_another_host_drained_returns_empty() {
    let mut m = Memory::new(MemoryLayout::default());

    {
        let mut g = m.guest();
        g.begin_frame();
        g.create_node(1, component_type::VSTACK).unwrap();
        g.end_frame();
    }

    // Host 1 drains the frame, advancing readCursor.
    {
        let mut h1 = m.host();
        assert_eq!(h1.frames().len(), 1);
    }

    // A fresh Host 2 starts with consumed=0 but the cursors already agree
    // (readCursor == writeCursor), so it must NOT spin out empty frames.
    let mut h2 = m.host();
    assert!(h2.frames().is_empty());
}

#[test]
fn pending_counts_opcodes_since_begin_frame() {
    let mut m = Memory::new(MemoryLayout::default());

    {
        let mut g = m.guest();
        g.begin_frame();
        assert_eq!(g.pending(), 0);
        g.create_node(1, component_type::VSTACK).unwrap();
        g.create_node(2, component_type::TEXT).unwrap();
        assert_eq!(g.pending(), 2);
        g.end_frame();
    }

    // A new begin_frame resets the accounting window.
    {
        let mut g = m.guest();
        g.begin_frame();
        assert_eq!(g.pending(), 0);
        g.create_node(3, component_type::HSTACK).unwrap();
        assert_eq!(g.pending(), 1);
        g.end_frame();
    }
}

#[test]
fn frame_count_is_monotonic() {
    let mut m = Memory::new(MemoryLayout::default());
    let mut g = m.guest();
    assert_eq!(g.frame_count(), 0);
    for expected in 1..=5 {
        g.begin_frame();
        g.create_node(expected, component_type::VSTACK).unwrap();
        g.end_frame();
        assert_eq!(g.frame_count(), expected);
    }
}

#[test]
fn all_value_types_encode_into_b_field() {
    let mut m = Memory::new(MemoryLayout::default());
    let mut g = m.guest();
    g.begin_frame();

    // (value_type, property_id) -> expected B
    let cases: &[(u8, u16, u32)] = &[
        (value_type::U8, property_id::LINE_LIMIT, 1),
        (value_type::U32, property_id::Z_INDEX, 42),
        (value_type::I32, property_id::PADDING_TOP, 7),
        (value_type::F32, property_id::SPACING, 4.0f32.to_bits()),
        (value_type::STRING, property_id::FONT_FAMILY, 0),
        (value_type::ENUM, property_id::ALIGNMENT, 1),
        (value_type::COLOR, property_id::COLOR, 0xFF00_00FF),
        (value_type::DESIGN_TOKEN, property_id::PADDING, 0),
    ];

    for (vt, pid, value) in cases {
        g.set_property(1, *pid, *vt, *value).unwrap();
    }
    g.end_frame();

    let mut h = m.host();
    let ops: Vec<Opcode> = h.frames()[0].opcodes().collect();
    assert_eq!(ops.len(), cases.len());
    for (op, (vt, pid, _)) in ops.iter().zip(cases) {
        assert_eq!(op.category(), pathland_core::category::PARAMETER);
        assert_eq!(op.command(), pathland_core::parameter::SET_PROPERTY);
        assert_eq!(op.b(), (*vt as u32) << 16 | *pid as u32);
    }
}

#[test]
fn insert_child_with_explicit_index_round_trips() {
    let mut m = Memory::new(MemoryLayout::default());

    {
        let mut g = m.guest();
        g.begin_frame();
        g.create_node(1, component_type::VSTACK).unwrap();
        g.create_node(2, component_type::TEXT).unwrap();
        g.create_node(3, component_type::TEXT).unwrap();
        g.insert_child(1, 2, APPEND).unwrap();
        g.insert_child(1, 3, 0).unwrap();
        g.end_frame();
    }

    let mut h = m.host();
    let ops: Vec<Opcode> = h.frames()[0].opcodes().collect();
    assert_eq!(ops[3].command(), pathland_core::tree::INSERT_CHILD);
    assert_eq!(ops[3].c(), APPEND);
    assert_eq!(ops[4].command(), pathland_core::tree::INSERT_CHILD);
    assert_eq!(ops[4].c(), 0);
}
