//! End-to-end round-trip tests: a `Guest` (producer) writes opcodes into a
//! frame and a `Host` (consumer) reads them back. These exercise the public
//! API exactly as a renderer would consume the engine's output.

use pathland_core::{
    category, component_type, init_memory, property_id, value_type, Guest, Host, MemoryLayout,
    Opcode, APPEND,
};

/// A linear-memory buffer plus its layout, letting a test create disjoint
/// `Guest` and `Host` views over the same memory over time.
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
fn tree_opcodes_round_trip_in_order() {
    let mut m = Memory::new(MemoryLayout::default());

    {
        let mut g = m.guest();
        g.begin_frame();
        g.create_node(1, component_type::VSTACK).unwrap();
        g.create_node(2, component_type::TEXT).unwrap();
        g.insert_child(1, 2, APPEND).unwrap();
        g.move_child(1, 2, 0).unwrap();
        g.remove_child(1, 2).unwrap();
        g.delete_node(2).unwrap();
        g.end_frame();
    }

    let mut h = m.host();
    let frames = h.frames();
    assert_eq!(frames.len(), 1);
    let ops: Vec<Opcode> = frames[0].opcodes().collect();
    assert_eq!(ops.len(), 6);

    // CREATE_NODE VSTACK id=1
    assert_eq!(ops[0].category(), category::TREE);
    assert_eq!(ops[0].command(), pathland_core::tree::CREATE_NODE);
    assert_eq!(ops[0].a(), 1);
    assert_eq!(ops[0].b(), component_type::VSTACK as u32);

    // CREATE_NODE TEXT id=2
    assert_eq!(ops[1].command(), pathland_core::tree::CREATE_NODE);
    assert_eq!(ops[1].a(), 2);
    assert_eq!(ops[1].b(), component_type::TEXT as u32);

    // INSERT_CHILD parent=1 child=2 APPEND
    assert_eq!(ops[2].command(), pathland_core::tree::INSERT_CHILD);
    assert_eq!(ops[2].a(), 1);
    assert_eq!(ops[2].b(), 2);
    assert_eq!(ops[2].c(), APPEND);

    // MOVE_CHILD parent=1 child=2 newIndex=0
    assert_eq!(ops[3].command(), pathland_core::tree::MOVE_CHILD);
    assert_eq!(ops[3].a(), 1);
    assert_eq!(ops[3].b(), 2);
    assert_eq!(ops[3].c(), 0);

    // REMOVE_CHILD parent=1 child=2
    assert_eq!(ops[4].command(), pathland_core::tree::REMOVE_CHILD);
    assert_eq!(ops[4].a(), 1);
    assert_eq!(ops[4].b(), 2);

    // DELETE_NODE id=2
    assert_eq!(ops[5].command(), pathland_core::tree::DELETE_NODE);
    assert_eq!(ops[5].a(), 2);

    // Second drain is empty.
    assert!(h.frames().is_empty());
}

#[test]
fn set_text_round_trips_through_arena() {
    let mut m = Memory::new(MemoryLayout::default());

    let (arena_ref, text) = {
        let mut g = m.guest();
        g.begin_frame();
        g.create_node(1, component_type::TEXT).unwrap();
        let ref_ = g.set_text(1, "Hello, Pathland!").unwrap();
        g.end_frame();
        (ref_, "Hello, Pathland!".to_string())
    };

    let mut h = m.host();
    let frames = h.frames();
    assert_eq!(frames.len(), 1);
    let frame = &frames[0];
    assert_eq!(frame.arena_str(arena_ref).unwrap(), text);

    let ops: Vec<Opcode> = frame.opcodes().collect();
    assert_eq!(ops[1].command(), pathland_core::parameter::SET_TEXT);
    assert_eq!(ops[1].a(), 1);
    assert_eq!(ops[1].b(), arena_ref);
}

#[test]
fn reset_opcode_round_trips() {
    let mut m = Memory::new(MemoryLayout::default());

    {
        let mut g = m.guest();
        g.begin_frame();
        g.create_node(1, component_type::VSTACK).unwrap();
        g.reset().unwrap();
        g.end_frame();
    }

    let mut h = m.host();
    let ops: Vec<Opcode> = h.frames()[0].opcodes().collect();
    assert_eq!(ops.len(), 2);
    assert_eq!(ops[1].category(), category::META);
    assert_eq!(ops[1].command(), pathland_core::meta::RESET);
}

#[test]
fn set_property_encodes_b_field_correctly() {
    let mut m = Memory::new(MemoryLayout::default());

    {
        let mut g = m.guest();
        g.begin_frame();
        g.create_node(1, component_type::TEXT).unwrap();
        g.set_property(1, property_id::COLOR, value_type::COLOR, 0xFF00_00FF)
            .unwrap();
        g.set_property(1, property_id::FONT_SIZE, value_type::F32, 16.0f32.to_bits())
            .unwrap();
        g.end_frame();
    }

    let mut h = m.host();
    let ops: Vec<Opcode> = h.frames()[0].opcodes().collect();
    assert_eq!(ops.len(), 3);

    // COLOR: B = (valueType<<16)|propertyId = (0x07<<16)|0x100A
    assert_eq!(ops[1].category(), category::PARAMETER);
    assert_eq!(ops[1].command(), pathland_core::parameter::SET_PROPERTY);
    assert_eq!(ops[1].a(), 1);
    assert_eq!(ops[1].b(), (value_type::COLOR as u32) << 16 | property_id::COLOR as u32);
    assert_eq!(ops[1].c(), 0xFF00_00FF);

    // FONT_SIZE: B = (0x04<<16)|0x1007
    assert_eq!(ops[2].b(), (value_type::F32 as u32) << 16 | property_id::FONT_SIZE as u32);
    assert_eq!(ops[2].c_f32(), 16.0);
}
