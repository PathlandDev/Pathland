//! Integration tests for reactive signals in `pathland-core`.

use std::collections::BTreeMap;

use pathland_core::{
    category, init_memory, property_id, parameter, value_type, Guest, Host, MemoryLayout, Opcode,
};
use pathland_engine::{assign_ids, Component, Engine, Node, SignalId, SignalValue};

fn with_guest() -> (Vec<u8>, MemoryLayout) {
    let layout = MemoryLayout::default();
    let mut mem = vec![0u8; layout.total_bytes()];
    init_memory(&mut mem, &layout);
    (mem, layout)
}

fn text_node(text: &str) -> Node {
    Node {
        id: 0,
        component: Component::Text { text: text.into() },
        children: Vec::new(),
        properties: BTreeMap::new(),
        string_properties: BTreeMap::new(),
    list_properties: BTreeMap::new(),
        token_properties: BTreeMap::new(),
        text_binding: None,
        property_bindings: BTreeMap::new(),
        gestures: Vec::new(),
    }
}

fn vstack(children: Vec<Node>) -> Node {
    Node {
        id: 0,
        component: Component::VStack,
        children,
        properties: BTreeMap::new(),
        string_properties: BTreeMap::new(),
    list_properties: BTreeMap::new(),
        token_properties: BTreeMap::new(),
        text_binding: None,
        property_bindings: BTreeMap::new(),
        gestures: Vec::new(),
    }
}

fn emit_full(engine: &mut Engine, root: &Node, mem: &mut [u8], layout: &MemoryLayout) {
    let mut guest = Guest::new(mem, layout);
    guest.begin_frame();
    engine.emit(root, &mut guest).unwrap();
    guest.end_frame();
    // Drain so the next read only sees the signal delta.
    let mut host = Host::new(mem, layout);
    let _ = host.frames();
}

fn set_and_collect(
    engine: &mut Engine,
    sig: SignalId,
    value: SignalValue,
    mem: &mut [u8],
    layout: &MemoryLayout,
) -> (usize, Vec<Opcode>) {
    let mut guest = Guest::new(mem, layout);
    guest.begin_frame();
    let n = engine.set_signal(sig, value, &mut guest).unwrap();
    guest.end_frame();
    let mut host = Host::new(mem, layout);
    let frames = host.frames();
    let ops = frames
        .first()
        .map(|f| f.opcodes().collect::<Vec<Opcode>>())
        .unwrap_or_default();
    (n, ops)
}

#[test]
fn text_signal_change_emits_only_bound_node() {
    let mut engine = Engine::new();
    let sig = engine.create_signal(SignalValue::Str("hello".into()));

    let (mut mem, layout) = with_guest();
    let mut root = vstack(vec![text_node("initial"), text_node("static")]);
    root.children[0].text_binding = Some(sig);
    assign_ids(&mut root, &mut 1);

    emit_full(&mut engine, &root, &mut mem, &layout);

    let (n, ops) = set_and_collect(
        &mut engine,
        sig,
        SignalValue::Str("world".into()),
        &mut mem,
        &layout,
    );
    assert_eq!(n, 1, "exactly one opcode for the bound node");
    assert_eq!(ops.len(), 1);
    assert_eq!(ops[0].category(), category::PARAMETER);
    assert_eq!(ops[0].command(), parameter::SET_TEXT);
    assert_eq!(ops[0].a(), 2, "node 2 is the bound text");
}

#[test]
fn unrelated_node_emits_nothing_on_signal_change() {
    let mut engine = Engine::new();
    let sig = engine.create_signal(SignalValue::Str("hello".into()));

    let (mut mem, layout) = with_guest();
    let mut root = vstack(vec![text_node("bound"), text_node("static")]);
    root.children[0].text_binding = Some(sig);
    assign_ids(&mut root, &mut 1);

    emit_full(&mut engine, &root, &mut mem, &layout);

    // Set to the same value: no change, no opcodes.
    let (n, ops) = set_and_collect(
        &mut engine,
        sig,
        SignalValue::Str("hello".into()),
        &mut mem,
        &layout,
    );
    assert_eq!(n, 0);
    assert!(ops.is_empty());
}

#[test]
fn property_signal_change_emits_set_property() {
    let mut engine = Engine::new();
    let sig = engine.create_signal(SignalValue::F32(4.0));

    let (mut mem, layout) = with_guest();
    let mut root = vstack(vec![text_node("x")]);
    root.property_bindings.insert(property_id::SPACING, sig);
    assign_ids(&mut root, &mut 1);

    emit_full(&mut engine, &root, &mut mem, &layout);

    let (n, ops) = set_and_collect(
        &mut engine,
        sig,
        SignalValue::F32(12.0),
        &mut mem,
        &layout,
    );
    assert_eq!(n, 1);
    assert_eq!(ops.len(), 1);
    assert_eq!(ops[0].category(), category::PARAMETER);
    assert_eq!(ops[0].command(), parameter::SET_PROPERTY);
    assert_eq!(ops[0].a(), 1);
    assert_eq!(ops[0].b() as u16, property_id::SPACING);
    assert_eq!(ops[0].c_f32(), 12.0);
}

#[test]
fn color_signal_packs_as_color_value_type() {
    let mut engine = Engine::new();
    let sig = engine.create_signal(SignalValue::U32(0xFF_0000FF));

    let (mut mem, layout) = with_guest();
    let mut root = text_node("x");
    root.property_bindings.insert(property_id::COLOR, sig);
    assign_ids(&mut root, &mut 1);

    emit_full(&mut engine, &root, &mut mem, &layout);

    let (n, ops) = set_and_collect(
        &mut engine,
        sig,
        SignalValue::U32(0xFF_00FF00),
        &mut mem,
        &layout,
    );
    assert_eq!(n, 1);
    let op = ops.iter().find(|o| o.b() as u16 == property_id::COLOR).unwrap();
    assert_eq!(op.b() >> 16, value_type::COLOR as u32);
    assert_eq!(op.c(), 0xFF_00FF00);
}

#[test]
fn multiple_nodes_on_one_signal_emit_together() {
    let mut engine = Engine::new();
    let sig = engine.create_signal(SignalValue::Str("a".into()));

    let (mut mem, layout) = with_guest();
    let mut root = vstack(vec![text_node("1"), text_node("2"), text_node("3")]);
    root.children[0].text_binding = Some(sig);
    root.children[2].text_binding = Some(sig);
    assign_ids(&mut root, &mut 1);

    emit_full(&mut engine, &root, &mut mem, &layout);

    let (n, ops) = set_and_collect(
        &mut engine,
        sig,
        SignalValue::Str("b".into()),
        &mut mem,
        &layout,
    );
    assert_eq!(n, 2, "both bound nodes re-emit");
    assert_eq!(ops.len(), 2);
    assert!(ops.iter().all(|o| o.command() == parameter::SET_TEXT));
    let ids: Vec<u32> = ops.iter().map(|o| o.a()).collect();
    assert_eq!(ids, vec![2, 4]);
}

#[test]
fn rebinding_drops_stale_dependency() {
    let mut engine = Engine::new();
    let sig = engine.create_signal(SignalValue::Str("a".into()));

    let (mut mem, layout) = with_guest();
    let mut root = vstack(vec![text_node("1")]);
    root.children[0].text_binding = Some(sig);
    assign_ids(&mut root, &mut 1);

    emit_full(&mut engine, &root, &mut mem, &layout);

    // Rebuild without the binding; a full emit re-records deps (none now).
    let mut root2 = vstack(vec![text_node("1")]);
    assign_ids(&mut root2, &mut 1);
    emit_full(&mut engine, &root2, &mut mem, &layout);

    // Setting the now-unbound signal emits nothing.
    let (n, ops) = set_and_collect(
        &mut engine,
        sig,
        SignalValue::Str("b".into()),
        &mut mem,
        &layout,
    );
    assert_eq!(n, 0);
    assert!(ops.is_empty());
}

#[test]
fn typed_signal_read_write_and_binding() {
    let mut engine = Engine::new();
    let label = engine.signal(String::from("hello"));
    // Typed read returns the current value.
    assert_eq!(engine.read(label.as_readonly()), Some(String::from("hello")));

    let (mut mem, layout) = with_guest();
    let mut root = vstack(vec![text_node("")]);
    root.children[0].text_binding = Some(label.id());
    assign_ids(&mut root, &mut 1);
    emit_full(&mut engine, &root, &mut mem, &layout);

    // Typed write emits only the bound node and updates the typed read.
    let mut guest = Guest::new(&mut mem, &layout);
    guest.begin_frame();
    let n = engine
        .set(label.clone(), String::from("world"), &mut guest)
        .unwrap();
    guest.end_frame();
    assert_eq!(n, 1);
    assert_eq!(engine.read(label.as_readonly()), Some(String::from("world")));
}

#[test]
fn typed_numeric_signal_packs_by_kind() {
    let mut engine = Engine::new();
    let spacing = engine.signal(4.0f32);
    assert_eq!(engine.read(spacing.as_readonly()), Some(4.0));

    let (mut mem, layout) = with_guest();
    let mut root = vstack(vec![text_node("x")]);
    root.property_bindings.insert(property_id::SPACING, spacing.id());
    assign_ids(&mut root, &mut 1);
    emit_full(&mut engine, &root, &mut mem, &layout);

    let mut guest = Guest::new(&mut mem, &layout);
    guest.begin_frame();
    let n = engine
        .set(spacing.clone(), 12.0f32, &mut guest)
        .unwrap();
    guest.end_frame();
    assert_eq!(n, 1);

    let mut host = Host::new(&mut mem, &layout);
    let frames = host.frames();
    let ops: Vec<Opcode> = frames[0].opcodes().collect();
    let op = ops
        .iter()
        .find(|o| o.b() as u16 == property_id::SPACING)
        .unwrap();
    assert_eq!(op.c_f32(), 12.0);
}

#[test]
fn computed_signal_reemits_its_bound_node() {
    let mut engine = Engine::new();
    let n = engine.signal(2.0f32);
    let nc = n.clone();
    let label = engine.computed(move || {
        String::from(if nc.get().unwrap() > 1.0 { "big" } else { "small" })
    });

    let (mut mem, layout) = with_guest();
    let mut root = vstack(vec![text_node("")]);
    root.children[0].text_binding = Some(label.id());
    assign_ids(&mut root, &mut 1);
    emit_full(&mut engine, &root, &mut mem, &layout);

    // Setting the source recomputes the computed and re-emits the bound node.
    let (count, ops) = set_and_collect(&mut engine, n.id(), SignalValue::F32(0.0), &mut mem, &layout);
    assert_eq!(count, 1);
    assert_eq!(ops[0].command(), parameter::SET_TEXT);
    assert_eq!(ops[0].a(), 2);

    // A second set that leaves the computed value unchanged emits nothing.
    let (count, ops) = set_and_collect(&mut engine, n.id(), SignalValue::F32(-5.0), &mut mem, &layout);
    assert_eq!(count, 0);
    assert!(ops.is_empty());
}
