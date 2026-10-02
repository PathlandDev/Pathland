//! End-to-end test of the native host: flat builder → emit → zero-copy ring →
//! render-tree decode.

use pathland_gtk::RenderTree;
use pathland_native::{component_from_id, NativeHost};
use pathland_core::{property_id, SharedHeader, parameter};
use pathland_engine::SignalValue;
use pathland_core_transport::FrameSource;

#[test]
fn flat_builder_emit_then_render_tree() {
    let mut host = NativeHost::new();

    // Build: vstack(1) + [ text(2, "Hello"), button(3) ]
    host.create_node(1, pathland_view::Component::VStack);
    host.create_node(2, pathland_view::Component::Text { text: "".into() });
    host.create_node(3, pathland_view::Component::Button { label: "".into() });
    host.set_text(2, "Hello");
    host.set_property_f32(1, property_id::SPACING, 8.0);
    host.insert_child(1, 2, u32::MAX);
    host.insert_child(1, 3, u32::MAX);

    assert!(host.emit(), "a frame must be written");

    // The header shows a completed frame (frameCount incremented).
    {
        let mem = host.ring_buffer();
        let header = SharedHeader::new(mem);
        assert!(header.frame_count() >= 1);
    }

    // Drain the ring and decode the emitted frame into a RenderTree.
    let mut render = RenderTree::default();
    let mut ring = host.take_ring();
    if let Some(batch) = ring.next_frame().unwrap() {
        render.apply_frame(batch.frame());
    }
    assert_eq!(render.nodes.len(), 3);
    assert_eq!(render.node(2).unwrap().text.as_deref(), Some("Hello"));
    let root = render.node(1).unwrap();
    assert_eq!(root.component_type, pathland_core::component_type::VSTACK);
    assert_eq!(root.children, vec![2, 3]);
}

#[test]
fn component_from_id_maps_flat_surface() {
    assert_eq!(
        component_from_id(0x10),
        Some(pathland_view::Component::VStack)
    );
    assert_eq!(component_from_id(0x01), Some(pathland_view::Component::Text { text: "".into() }));
    assert!(component_from_id(999).is_none());
}

#[test]
fn signal_set_writes_a_set_text_delta_for_the_bound_node() {
    let mut host = NativeHost::new();

    host.create_node(1, pathland_view::Component::VStack);
    host.create_node(2, pathland_view::Component::Text { text: "".into() });
    host.create_node(3, pathland_view::Component::Text { text: "static".into() });
    host.insert_child(1, 2, u32::MAX);
    host.insert_child(1, 3, u32::MAX);

    let sig = host.create_signal(SignalValue::Str("hello".into()));
    assert!(host.bind_text(2, sig));

    assert!(host.emit(), "full emit");

    // Drain the full frame (the initial tree).
    assert!(host.next_frame().unwrap().is_some());

    // Set the signal: writes a SET_TEXT delta into the ring in place.
    assert!(host.set_signal(sig, SignalValue::Str("world".into())));

    // The next frame is exactly the bound node's delta.
    let batch = host.next_frame().unwrap().expect("a delta frame");
    let ops: Vec<pathland_core::Opcode> = batch.frame().opcodes().collect();
    assert_eq!(ops.len(), 1, "only the bound node emits");
    assert_eq!(ops[0].command(), parameter::SET_TEXT);
    assert_eq!(ops[0].a(), 2);
}

#[test]
fn signal_property_change_emits_set_property() {
    let mut host = NativeHost::new();

    host.create_node(1, pathland_view::Component::VStack);
    host.create_node(2, pathland_view::Component::Text { text: "x".into() });
    host.insert_child(1, 2, u32::MAX);

    let sig = host.create_signal(SignalValue::F32(4.0));
    assert!(host.bind_property(1, property_id::SPACING, sig));

    assert!(host.emit());
    assert!(host.next_frame().unwrap().is_some());

    assert!(host.set_signal(sig, SignalValue::F32(12.0)));

    let batch = host.next_frame().unwrap().expect("a delta frame");
    let ops: Vec<pathland_core::Opcode> = batch.frame().opcodes().collect();
    assert_eq!(ops.len(), 1);
    assert_eq!(ops[0].command(), parameter::SET_PROPERTY);
    assert_eq!(ops[0].a(), 1);
    assert_eq!(ops[0].b() as u16, property_id::SPACING);
    assert_eq!(ops[0].c_f32(), 12.0);
}

#[test]
fn signal_noop_set_writes_nothing() {
    let mut host = NativeHost::new();
    host.create_node(1, pathland_view::Component::Text { text: "".into() });
    let sig = host.create_signal(SignalValue::Str("hello".into()));
    assert!(host.bind_text(1, sig));

    assert!(host.emit());
    assert!(host.next_frame().unwrap().is_some());

    // Setting to the same value is a no-op.
    assert!(!host.set_signal(sig, SignalValue::Str("hello".into())));
    assert!(host.next_frame().unwrap().is_none());
}
