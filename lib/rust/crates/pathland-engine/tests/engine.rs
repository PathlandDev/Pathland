//! Integration tests for the diff-based reactive emitter in `pathland-core`.

use std::collections::BTreeMap;

use pathland_core::{
    category, init_memory, listener, property_id, size, parameter, tree, value_type, Guest, Host,
    MemoryLayout, Opcode,
};
use pathland_engine::{assign_ids, component_type_id, Component, Engine, Node};

fn with_guest() -> (Vec<u8>, MemoryLayout) {
    let layout = MemoryLayout::default();
    let mut mem = vec![0u8; layout.total_bytes()];
    init_memory(&mut mem, &layout);
    (mem, layout)
}

fn emit_and_collect(engine: &mut Engine, root: &Node) -> Vec<Opcode> {
    let (mut mem, layout) = with_guest();
    {
        let mut guest = Guest::new(&mut mem, &layout);
        guest.begin_frame();
        engine.emit(root, &mut guest).unwrap();
        guest.end_frame();
    }
    let mut host = Host::new(&mut mem, &layout);
    let frames = host.frames();
    frames
        .first()
        .map(|f| f.opcodes().collect::<Vec<Opcode>>())
        .unwrap_or_default()
}

fn built(mut root: Node) -> Node {
    assign_ids(&mut root, &mut 1);
    root
}

fn text_node(text: &str) -> Node {
    Node {
        id: 0,
        component: Component::Text { text: text.into() },
        children: Vec::new(),
        properties: BTreeMap::new(),
        string_properties: BTreeMap::new(),
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
        token_properties: BTreeMap::new(),
        text_binding: None,
        property_bindings: BTreeMap::new(),
        gestures: Vec::new(),
    }
}

fn hstack(children: Vec<Node>) -> Node {
    Node {
        id: 0,
        component: Component::HStack,
        children,
        properties: BTreeMap::new(),
        string_properties: BTreeMap::new(),
        token_properties: BTreeMap::new(),
        text_binding: None,
        property_bindings: BTreeMap::new(),
        gestures: Vec::new(),
    }
}

#[test]
fn first_emit_produces_full_structure() {
    let mut engine = Engine::new();
    let mut root = vstack(vec![text_node("ab"), text_node("cd")]);
    root.properties.insert(property_id::SPACING, 4.0f32.to_bits());
    root.properties.insert(property_id::PADDING, 8.0f32.to_bits());
    assign_ids(&mut root, &mut 1);

    let ops = emit_and_collect(&mut engine, &root);
    assert_eq!(ops.len(), 9);
    assert!(ops.iter().any(|o| {
        o.category() == category::TREE
            && o.command() == tree::CREATE_NODE
            && o.a() == 1
            && o.b() as u16 == component_type_id(&Component::VStack)
    }));
    assert!(ops
        .iter()
        .any(|o| o.category() == category::PARAMETER && o.command() == parameter::SET_TEXT));
    assert!(engine.tracked_nodes() == 3);
}

#[test]
fn property_change_emits_only_delta() {
    let mut engine = Engine::new();
    let mut a = built(vstack(vec![text_node("ab")]));
    a.properties.insert(property_id::SPACING, 4.0f32.to_bits());
    a.properties.insert(property_id::PADDING, 8.0f32.to_bits());
    emit_and_collect(&mut engine, &a);

    a.properties.insert(property_id::SPACING, 12.0f32.to_bits());
    let ops = emit_and_collect(&mut engine, &a);

    assert_eq!(ops.len(), 1, "only the spacing delta should emit");
    assert_eq!(ops[0].category(), category::PARAMETER);
    assert_eq!(ops[0].command(), parameter::SET_PROPERTY);
    assert_eq!(ops[0].a(), 1);
    assert_eq!(ops[0].b() as u16, property_id::SPACING);
    assert_eq!(ops[0].c_f32(), 12.0);
}

#[test]
fn color_property_emits_color_value_type() {
    let mut engine = Engine::new();
    let mut root = text_node("hi");
    root.properties.insert(property_id::COLOR, 0xFF_0000FF);
    root.properties
        .insert(property_id::BACKGROUND_COLOR, 0xFF_EEEEEE);
    let ops = emit_and_collect(&mut engine, &root);
    assert_eq!(ops.len(), 4);
    let color_op = ops
        .iter()
        .find(|o| o.b() as u16 == property_id::COLOR)
        .unwrap();
    assert_eq!(color_op.b() >> 16, value_type::COLOR as u32);
    assert_eq!(color_op.c(), 0xFF_0000FF);
}

/// Emit once and read the (property, valueType, token path) of the first
/// `SET_PROPERTY` opcode, resolving its arena string via the host frame.
fn emit_and_read_token(engine: &mut Engine, root: &Node) -> (u16, u8, String) {
    let (mut mem, layout) = with_guest();
    {
        let mut guest = Guest::new(&mut mem, &layout);
        guest.begin_frame();
        engine.emit(root, &mut guest).unwrap();
        guest.end_frame();
    }
    let mut host = Host::new(&mut mem, &layout);
    let frames = host.frames();
    let frame = frames.first().unwrap();
    for op in frame.opcodes() {
        if op.category() == category::PARAMETER && op.command() == parameter::SET_PROPERTY {
            let prop = op.b() as u16;
            let vt = (op.b() >> 16) as u8;
            let path = frame.arena_str(op.c()).map(str::to_owned).unwrap_or_default();
            return (prop, vt, path);
        }
    }
    (0, 0, String::new())
}

#[test]
fn token_ref_emits_design_token_value_type() {
    let mut engine = Engine::new();
    let mut root = text_node("hi");
    root.token_properties
        .insert(property_id::COLOR, "color.primary".into());
    let (prop, vt, path) = emit_and_read_token(&mut engine, &root);
    assert_eq!(prop, property_id::COLOR);
    assert_eq!(vt, value_type::DESIGN_TOKEN);
    assert_eq!(path, "color.primary");
}

#[test]
fn unchanged_token_ref_emits_zero_opcodes() {
    let mut engine = Engine::new();
    let mut root = built(text_node("hi"));
    root.token_properties
        .insert(property_id::COLOR, "color.primary".into());
    emit_and_collect(&mut engine, &root);
    let ops = emit_and_collect(&mut engine, &root);
    assert_eq!(ops.len(), 0, "no arena churn / no re-emission for an unchanged ref");
}

#[test]
fn token_ref_change_emits_single_design_token_delta() {
    let mut engine = Engine::new();
    let mut root = built(text_node("hi"));
    root.token_properties
        .insert(property_id::COLOR, "color.primary".into());
    emit_and_collect(&mut engine, &root);

    root.token_properties
        .insert(property_id::COLOR, "color.secondary".into());
    let ops = emit_and_collect(&mut engine, &root);
    assert_eq!(ops.len(), 1);
    assert_eq!(ops[0].command(), parameter::SET_PROPERTY);
    assert_eq!(ops[0].b() as u16, property_id::COLOR);
    assert_eq!(ops[0].b() >> 16, value_type::DESIGN_TOKEN as u32);
}

#[test]
fn text_change_emits_single_set_text() {
    let mut engine = Engine::new();
    let mut root = built(vstack(vec![text_node("ab")]));
    emit_and_collect(&mut engine, &root);

    root.children[0].component = Component::Text { text: "xy".into() };
    let ops = emit_and_collect(&mut engine, &root);
    assert_eq!(ops.len(), 1);
    assert_eq!(ops[0].command(), parameter::SET_TEXT);
    assert_eq!(ops[0].a(), 2);
}

#[test]
fn node_added_emits_create_and_insert() {
    let mut engine = Engine::new();
    let mut root = built(vstack(vec![text_node("ab")]));
    emit_and_collect(&mut engine, &root);

    root.children.push(text_node("cd"));
    assign_ids(&mut root, &mut 1);
    let ops = emit_and_collect(&mut engine, &root);
    assert_eq!(ops.len(), 3);
    assert!(ops.iter().any(|o| o.command() == tree::CREATE_NODE && o.a() == 3));
    assert!(ops.iter().any(|o| o.command() == tree::INSERT_CHILD && o.b() == 3));
}

#[test]
fn node_removed_emits_delete() {
    let mut engine = Engine::new();
    let mut root = built(vstack(vec![text_node("ab"), text_node("cd")]));
    emit_and_collect(&mut engine, &root);

    root.children.pop();
    let ops = emit_and_collect(&mut engine, &root);
    assert_eq!(ops.len(), 1);
    assert_eq!(ops[0].command(), tree::DELETE_NODE);
    assert_eq!(ops[0].a(), 3);
}

#[test]
fn child_reordered_emits_move() {
    let mut engine = Engine::new();
    let mut root = built(hstack(vec![text_node("a"), text_node("b")]));
    emit_and_collect(&mut engine, &root);

    root.children.swap(0, 1);
    let ops = emit_and_collect(&mut engine, &root);
    assert_eq!(ops.len(), 2);
    assert!(ops.iter().all(|o| o.command() == tree::MOVE_CHILD));
    assert!(ops.iter().any(|o| o.b() == 3 && o.c() == 0));
    assert!(ops.iter().any(|o| o.b() == 2 && o.c() == 1));
}

#[test]
fn frame_emits_width_height_alignment_as_ordinary_properties() {
    let mut engine = Engine::new();
    let mut root = text_node("x");
    root.properties.insert(property_id::WIDTH, size::FILL.to_bits());
    root.properties.insert(property_id::HEIGHT, 24.0f32.to_bits());
    root.properties.insert(property_id::ALIGNMENT, 1.0f32.to_bits());
    let ops = emit_and_collect(&mut engine, &root);
    assert_eq!(ops.len(), 5);
    let width = ops
        .iter()
        .find(|o| o.b() as u16 == property_id::WIDTH)
        .unwrap();
    assert_eq!(width.c_f32(), size::FILL);
    let height = ops
        .iter()
        .find(|o| o.b() as u16 == property_id::HEIGHT)
        .unwrap();
    assert_eq!(height.c_f32(), 24.0);
    let align = ops
        .iter()
        .find(|o| o.b() as u16 == property_id::ALIGNMENT)
        .unwrap();
    assert_eq!(align.c_f32(), 1.0);
}

#[test]
fn frame_change_emits_only_size_delta() {
    let mut engine = Engine::new();
    let mut root = text_node("x");
    root.properties.insert(property_id::WIDTH, 100.0f32.to_bits());
    root.properties.insert(property_id::HEIGHT, 24.0f32.to_bits());
    root.properties.insert(property_id::ALIGNMENT, 0.0f32.to_bits());
    emit_and_collect(&mut engine, &root);

    root.properties.insert(property_id::WIDTH, 120.0f32.to_bits());
    let ops = emit_and_collect(&mut engine, &root);
    assert_eq!(ops.len(), 1, "only the width delta should emit");
    assert_eq!(ops[0].command(), parameter::SET_PROPERTY);
    assert_eq!(ops[0].b() as u16, property_id::WIDTH);
    assert_eq!(ops[0].c_f32(), 120.0);
}

#[test]
fn event_listeners_emits_u32_value_type() {
    let mut engine = Engine::new();
    let mut root = text_node("x");
    root.properties.insert(
        property_id::EVENT_LISTENERS,
        listener::POINTER_DOWN | listener::POINTER_UP,
    );
    let ops = emit_and_collect(&mut engine, &root);
    let listeners = ops
        .iter()
        .find(|o| o.b() as u16 == property_id::EVENT_LISTENERS)
        .unwrap();
    assert_eq!(listeners.b() >> 16, value_type::U32 as u32);
    assert_eq!(
        listeners.c(),
        listener::POINTER_DOWN | listener::POINTER_UP
    );
}

#[test]
fn steady_state_emits_zero() {
    let mut engine = Engine::new();
    let root = built(vstack(vec![text_node("ab"), text_node("cd")]));
    emit_and_collect(&mut engine, &root);

    let ops = emit_and_collect(&mut engine, &root);
    assert!(ops.is_empty(), "unchanged tree must emit zero opcodes");
}

#[test]
fn emit_is_zero_alloc_in_steady_state() {
    use std::alloc::{GlobalAlloc, Layout as AllocLayout};
    use std::cell::Cell;
    use std::thread_local;

    thread_local! {
        static COUNT: Cell<usize> = const { Cell::new(0) };
    }
    struct Counting;
    unsafe impl GlobalAlloc for Counting {
        unsafe fn alloc(&self, l: AllocLayout) -> *mut u8 {
            COUNT.with(|c| c.set(c.get() + 1));
            unsafe { std::alloc::System.alloc(l) }
        }
        unsafe fn dealloc(&self, p: *mut u8, l: AllocLayout) {
            unsafe { std::alloc::System.dealloc(p, l) }
        }
    }
    #[global_allocator]
    static A: Counting = Counting;

    let (mut mem, layout) = with_guest();
    let mut root = vstack(vec![text_node("one"), text_node("two")]);
    root.properties.insert(property_id::SPACING, 4.0f32.to_bits());
    root.properties.insert(property_id::PADDING, 8.0f32.to_bits());
    // Token-ref properties must also be steady-state zero-alloc (no fresh map,
    // no arena re-alloc on an unchanged ref).
    root.children[0]
        .token_properties
        .insert(property_id::COLOR, "color.primary".into());
    // String-valued properties must also be steady-state zero-alloc.
    root.children[1]
        .string_properties
        .insert(property_id::FONT_FAMILY, "Inter".into());
    assign_ids(&mut root, &mut 1);

    let mut engine = Engine::new();
    let mut guest = Guest::new(&mut mem, &layout);

    guest.begin_frame();
    engine.emit(&root, &mut guest).unwrap();
    guest.end_frame();

    COUNT.with(|c| c.set(0));
    guest.begin_frame();
    engine.emit(&root, &mut guest).unwrap();
    guest.end_frame();
    COUNT.with(|c| {
        assert_eq!(c.get(), 0, "steady-state emit must not allocate");
    });
}

fn emit_and_collect_with_arena(engine: &mut Engine, root: &Node) -> (Vec<Opcode>, Vec<String>) {
    let (mut mem, layout) = with_guest();
    {
        let mut guest = Guest::new(&mut mem, &layout);
        guest.begin_frame();
        engine.emit(root, &mut guest).unwrap();
        guest.end_frame();
    }
    let mut host = Host::new(&mut mem, &layout);
    let frames = host.frames();
    let frame = match frames.first() {
        Some(f) => f,
        None => return (Vec::new(), Vec::new()),
    };
    let ops: Vec<Opcode> = frame.opcodes().collect();
    let strings: Vec<String> = ops
        .iter()
        .map(|o| {
            frame
                .arena_str(o.c())
                .map(|s| s.to_string())
                .unwrap_or_default()
        })
        .collect();
    (ops, strings)
}

#[test]
fn string_property_emits_with_string_value_type_and_diffs() {
    let mut engine = Engine::new();
    let mut root = text_node("x");
    root.string_properties
        .insert(property_id::FONT_FAMILY, "Georgia".into());
    assign_ids(&mut root, &mut 1);

    let (ops, strings) = emit_and_collect_with_arena(&mut engine, &root);
    let prop = ops
        .iter()
        .position(|o| {
            o.category() == category::PARAMETER
                && o.command() == parameter::SET_PROPERTY
                && o.b() as u16 == property_id::FONT_FAMILY
        })
        .expect("FONT_FAMILY SET_PROPERTY emitted");
    assert_eq!((ops[prop].b() >> 16) & 0xff, value_type::STRING as u32);
    assert_eq!(strings[prop], "Georgia");

    // An unchanged string property must not re-emit.
    let (ops2, _) = emit_and_collect_with_arena(&mut engine, &root);
    assert_eq!(ops2.len(), 0, "unchanged string property must not re-emit");

    // A changed family re-emits one SET_PROPERTY with the new arena string.
    root.string_properties
        .insert(property_id::FONT_FAMILY, "Inter".into());
    let (ops3, strings3) = emit_and_collect_with_arena(&mut engine, &root);
    assert_eq!(ops3.len(), 1);
    assert_eq!(strings3[0], "Inter");
}
