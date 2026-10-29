//! Window metadata (`META::SET_WINDOW_METADATA`): initial emission, per-field
//! reactivity, and clear.

use std::collections::BTreeMap;

use pathland_core::{
    category, init_memory, meta, window_field, Guest, Host, MemoryLayout, Opcode,
};
use pathland_engine::{
    assign_ids, Component, Engine, Node, SignalValue, WindowMetadata,
};

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
        string_bindings: BTreeMap::new(),
        media_controller: None,
        gestures: Vec::new(),
    }
}

fn ops_after_emit(
    engine: &mut Engine,
    root: &Node,
    mem: &mut [u8],
    layout: &MemoryLayout,
) -> Vec<(u32, String)> {
    {
        let mut guest = Guest::new(mem, layout);
        guest.begin_frame();
        engine.emit(root, &mut guest).unwrap();
        guest.end_frame();
    }
    let mut host = Host::new(mem, layout);
    let frames = host.frames();
    let Some(frame) = frames.first() else {
        return Vec::new();
    };
    frame
        .opcodes()
        .filter(|op| op.category() == category::META && op.command() == meta::SET_WINDOW_METADATA)
        .map(|op| {
            (
                op.a(),
                frame.arena_str(op.b()).unwrap_or("").to_string(),
            )
        })
        .collect()
}

fn ops_after_set(
    engine: &mut Engine,
    signal: pathland_engine::SignalId,
    value: SignalValue,
    mem: &mut [u8],
    layout: &MemoryLayout,
    root: &Node,
) -> (usize, Vec<(u32, String)>) {
    let n = {
        let mut guest = Guest::new(mem, layout);
        guest.begin_frame();
        let n = engine.set_signal(signal, value, &mut guest).unwrap();
        guest.end_frame();
        n
    };
    // A Guest borrows the buffer; the frame is inspected through a fresh Host.
    let _ = root;
    let mut host = Host::new(mem, layout);
    let frames = host.frames();
    let Some(frame) = frames.last() else {
        return (n, Vec::new());
    };
    let ops = frame
        .opcodes()
        .filter(|op| op.category() == category::META && op.command() == meta::SET_WINDOW_METADATA)
        .map(|op| {
            (
                op.a(),
                frame.arena_str(op.b()).unwrap_or("").to_string(),
            )
        })
        .collect();
    (n, ops)
}

#[test]
fn mount_emits_all_fields_and_signal_change_emits_one() {
    let mut engine = Engine::new();
    let title = engine.create_signal(SignalValue::Str("Library".into()));
    let description = engine.create_signal(SignalValue::Str("8 songs".into()));
    let locale = engine.create_signal(SignalValue::Str("en".into()));
    engine.bind_window_metadata(
        WindowMetadata::new()
            .title(title)
            .description(description)
            .locale(locale),
    );

    let mut root = text_node("body");
    assign_ids(&mut root, &mut 1);
    let (mut mem, layout) = with_guest();

    let initial = ops_after_emit(&mut engine, &root, &mut mem, &layout);
    assert_eq!(
        initial,
        vec![
            (window_field::TITLE as u32, "Library".to_string()),
            (window_field::DESCRIPTION as u32, "8 songs".to_string()),
            (window_field::LOCALE as u32, "en".to_string()),
        ]
    );

    // Unchanged tree + unchanged metadata → zero metadata opcodes.
    let again = ops_after_emit(&mut engine, &root, &mut mem, &layout);
    assert!(again.is_empty(), "unchanged metadata emits nothing: {again:?}");

    // One changed field → one opcode.
    let (n, changed) = ops_after_set(
        &mut engine,
        title,
        SignalValue::Str("Now Playing".into()),
        &mut mem,
        &layout,
        &root,
    );
    assert_eq!(n, 1);
    assert_eq!(
        changed,
        vec![(window_field::TITLE as u32, "Now Playing".to_string())]
    );

    // Same value → zero.
    let (n, unchanged) = ops_after_set(
        &mut engine,
        title,
        SignalValue::Str("Now Playing".into()),
        &mut mem,
        &layout,
        &root,
    );
    assert_eq!(n, 0);
    assert!(unchanged.is_empty());

    // Empty string clears the field (one clear opcode).
    let (n, cleared) = ops_after_set(
        &mut engine,
        locale,
        SignalValue::Str(String::new()),
        &mut mem,
        &layout,
        &root,
    );
    assert_eq!(n, 1);
    assert_eq!(cleared, vec![(window_field::LOCALE as u32, String::new())]);
}

#[test]
fn wire_opcode_is_field_plus_string_offset() {
    let op = Opcode::new(
        category::META,
        meta::SET_WINDOW_METADATA,
        0,
        window_field::TITLE as u32,
        12,
        0,
    );
    let bytes = op.to_bytes();
    assert_eq!(bytes[0], category::META);
    assert_eq!(bytes[1], meta::SET_WINDOW_METADATA);
    assert_eq!(u32::from_le_bytes(bytes[4..8].try_into().unwrap()), 1);
    assert_eq!(u32::from_le_bytes(bytes[8..12].try_into().unwrap()), 12);
}
