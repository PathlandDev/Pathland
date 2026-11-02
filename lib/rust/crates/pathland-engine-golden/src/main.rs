//! Shared engine trace fixtures.
//!
//! Defines a small set of engine scenarios, runs the canonical Rust engine, and
//! writes each emitted frame as a self-contained PLPL batch in hex under
//! `spec/engine-traces/`. The Java engine test builds the same scenarios through
//! its DSL and compares its encoded frames byte-for-byte.
//!
//! This emits neutral wire data only (opcodes + strings in the existing batch
//! format). It does not contain Java source, packages, or formatting.

use pathland_core::{
    category, init_memory, parameter, value_type, Guest, Host, MemoryLayout, Opcode,
};
use pathland_core_transport::encode_frame;
use pathland_engine::{assign_ids, Component, Engine, Node, SignalId, SignalValue};
use std::fmt::Write as _;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

fn trace_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../../../../spec/engine-traces")
}

fn text_node(text: &str) -> Node {
    Node::from_component(&Component::Text { text: text.into() })
}

fn vstack(children: Vec<Node>) -> Node {
    let mut node = Node::from_component(&Component::VStack);
    node.children = children;
    node
}

fn with_memory() -> (Vec<u8>, MemoryLayout) {
    let layout = MemoryLayout::default();
    let mut mem = vec![0u8; layout.total_bytes()];
    init_memory(&mut mem, &layout);
    (mem, layout)
}

fn note_arena_ref(frame: &pathland_core::Frame<'_>, offset: u32, max: &mut usize) {
    if let Ok(bytes) = frame.arena_get(offset) {
        *max = (*max).max(offset as usize + 4 + bytes.len());
    }
}

/// Trim the full arena region to the entries this frame references, matching the
/// compact string section a self-contained network batch carries.
fn used_arena(frame: &pathland_core::Frame<'_>) -> Vec<u8> {
    let mut max = 0usize;
    for op in frame.opcodes() {
        match (op.category(), op.command()) {
            (category::PARAMETER, parameter::SET_TEXT) => {
                note_arena_ref(frame, op.b(), &mut max);
            }
            (category::PARAMETER, parameter::SET_PROPERTY) => {
                let vt = (op.b() >> 16) as u8;
                if vt == value_type::STRING
                    || vt == value_type::DESIGN_TOKEN
                    || vt == value_type::LIST
                {
                    note_arena_ref(frame, op.c(), &mut max);
                }
            }
            (category::PARAMETER, parameter::SET_DESIGN_TOKEN) => {
                note_arena_ref(frame, op.a(), &mut max);
                if op.b() as u8 == value_type::STRING {
                    note_arena_ref(frame, op.c(), &mut max);
                }
            }
            _ => {}
        }
    }
    frame.arena()[..max].to_vec()
}

fn read_frame(mem: &mut [u8], layout: &MemoryLayout) -> Vec<u8> {
    let mut host = Host::new(mem, layout);
    let frames = host.frames();
    let frame = frames.first().expect("expected one emitted frame");
    let opcodes: Vec<Opcode> = frame.opcodes().collect();
    let strings = used_arena(frame);
    encode_frame(&opcodes, &strings)
}

fn emit_frame(engine: &mut Engine, root: &Node) -> Vec<u8> {
    let (mut mem, layout) = with_memory();
    {
        let mut guest = Guest::new(&mut mem, &layout);
        guest.begin_frame();
        engine.emit(root, &mut guest).expect("emit");
        guest.end_frame();
    }
    read_frame(&mut mem, &layout)
}

fn signal_frame(engine: &mut Engine, signal: SignalId, value: SignalValue) -> Vec<u8> {
    let (mut mem, layout) = with_memory();
    {
        let mut guest = Guest::new(&mut mem, &layout);
        guest.begin_frame();
        engine
            .set_signal(signal, value, &mut guest)
            .expect("set signal");
        guest.end_frame();
    }
    read_frame(&mut mem, &layout)
}

struct Scenario {
    name: &'static str,
    steps: Vec<(&'static str, Vec<u8>)>,
}

fn mount_text_stack() -> Scenario {
    let mut engine = Engine::new();
    let mut root = vstack(vec![text_node("Hello")]);
    root.properties
        .insert(pathland_core::property_id::SPACING, 8.0f32.to_bits());
    assign_ids(&mut root, &mut 1);
    Scenario {
        name: "mount_text_stack",
        steps: vec![("000-mount", emit_frame(&mut engine, &root))],
    }
}

fn signal_text_delta() -> Scenario {
    let mut engine = Engine::new();
    let signal = engine.create_signal(SignalValue::Str("Hello".into()));
    let mut root = vstack(vec![text_node("Hello")]);
    root.children[0].text_binding = Some(signal);
    assign_ids(&mut root, &mut 1);

    let mount = emit_frame(&mut engine, &root);
    let delta = signal_frame(&mut engine, signal, SignalValue::Str("World".into()));
    Scenario {
        name: "signal_text_delta",
        steps: vec![("000-mount", mount), ("001-set-text", delta)],
    }
}

fn property_signal_delta() -> Scenario {
    let mut engine = Engine::new();
    let signal = engine.create_signal(SignalValue::F32(8.0));
    let mut root = vstack(vec![text_node("Hello")]);
    root.properties
        .insert(pathland_core::property_id::SPACING, 8.0f32.to_bits());
    root.property_bindings
        .insert(pathland_core::property_id::SPACING, signal);
    assign_ids(&mut root, &mut 1);

    let mount = emit_frame(&mut engine, &root);
    let delta = signal_frame(&mut engine, signal, SignalValue::F32(12.0));
    Scenario {
        name: "property_signal_delta",
        steps: vec![("000-mount", mount), ("001-set-spacing", delta)],
    }
}

fn styled_text_mount() -> Scenario {
    let mut engine = Engine::new();
    let mut root = text_node("Hello");
    root.properties
        .insert(pathland_core::property_id::FONT_SIZE, 17.0f32.to_bits());
    assign_ids(&mut root, &mut 1);
    Scenario {
        name: "styled_text_mount",
        steps: vec![("000-mount", emit_frame(&mut engine, &root))],
    }
}

fn structural_add_remove() -> Scenario {
    let mut engine = Engine::new();
    let mut root = vstack(vec![text_node("a")]);
    assign_ids(&mut root, &mut 1);
    let mount = emit_frame(&mut engine, &root);

    root.children.push(text_node("b"));
    assign_ids(&mut root, &mut 1);
    let added = emit_frame(&mut engine, &root);

    root.children.pop();
    let removed = emit_frame(&mut engine, &root);

    Scenario {
        name: "structural_add_remove",
        steps: vec![
            ("000-mount", mount),
            ("001-add-child", added),
            ("002-remove-child", removed),
        ],
    }
}

fn scenarios() -> Vec<Scenario> {
    vec![
        mount_text_stack(),
        signal_text_delta(),
        property_signal_delta(),
        styled_text_mount(),
        structural_add_remove(),
    ]
}

fn hex_file(scenario: &str, step: &str, bytes: &[u8]) -> String {
    let mut out = String::new();
    writeln!(
        out,
        "# Generated by `cargo run -p pathland-engine-golden -- --emit` (lib/rust). Do not edit."
    )
    .unwrap();
    writeln!(out, "# scenario: {scenario}").unwrap();
    writeln!(out, "# step: {step}").unwrap();
    writeln!(
        out,
        "# format: self-contained PLPL batch bytes (lowercase hex)"
    )
    .unwrap();
    for chunk in bytes.chunks(32) {
        for byte in chunk {
            write!(out, "{byte:02x}").unwrap();
        }
        out.push('\n');
    }
    out
}

fn emit() -> io::Result<Vec<PathBuf>> {
    let root = trace_root();
    let mut written = Vec::new();
    for scenario in scenarios() {
        let dir = root.join(scenario.name);
        fs::create_dir_all(&dir)?;
        for (step, bytes) in scenario.steps {
            let path = dir.join(format!("{step}.plpl.hex"));
            fs::write(&path, hex_file(scenario.name, step, &bytes))?;
            written.push(path);
        }
    }
    Ok(written)
}

fn check() -> Result<(), Vec<String>> {
    let root = trace_root();
    let mut failures = Vec::new();
    for scenario in scenarios() {
        for (step, bytes) in scenario.steps {
            let path = root.join(scenario.name).join(format!("{step}.plpl.hex"));
            let expected = hex_file(scenario.name, step, &bytes);
            match fs::read_to_string(&path) {
                Ok(actual) if actual == expected => {}
                Ok(_) => failures.push(format!("drift: {}", path.display())),
                Err(_) => failures.push(format!("missing: {}", path.display())),
            }
        }
    }
    if failures.is_empty() {
        Ok(())
    } else {
        Err(failures)
    }
}

fn main() -> io::Result<()> {
    match std::env::args().nth(1).as_deref() {
        Some("--emit") => {
            for path in emit()? {
                println!("wrote {}", path.display());
            }
        }
        Some("--check") => match check() {
            Ok(()) => println!("engine trace fixtures are current"),
            Err(failures) => {
                for failure in &failures {
                    eprintln!("{failure}");
                }
                eprintln!(
                    "{} mismatch(es) — run `cargo run -p pathland-engine-golden -- --emit`",
                    failures.len()
                );
                std::process::exit(1);
            }
        },
        other => {
            eprintln!("usage: pathland-engine-golden --emit | --check (got {other:?})");
            std::process::exit(2);
        }
    }
    Ok(())
}
