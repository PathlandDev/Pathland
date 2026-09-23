//! # Pathland Qt Quick desktop demo
//!
//! A native Qt Quick app demonstrating Pathland's **zero-copy shared-memory
//! ring** and the shared **Qt renderer** (`pathland-render-qt`).
//!
//! The developer authors the UI with the SwiftUI-style DSL; the diff emitter
//! emits declarative 16-byte opcodes into the shared ring, and the Qt renderer
//! maps them onto a Qt Quick scene (Row/Column/Text/Button/TextField/Slider).
//! The demo never touches Qt APIs directly — it only drives Pathland and the
//! renderer.
//!
//! Interaction is declared with `.on_tap_gesture(...)` on any view; raw pointer
//! events round-trip through the shared ring and are turned back into taps by
//! the app-side [`TapRecognizer`](pathland_view::TapRecognizer).

use std::cell::RefCell;
use std::collections::BTreeMap;
use std::rc::Rc;
use std::time::Instant;

use pathland_engine::Engine;
use pathland_core::listener;
use pathland_core_transport::RingTransport;
use pathland_view::{
    assign_ids, button, hstack, text, vstack, Color, Node, Slider, View, ViewExt,
};

const APP_ID: &str = "org.pathland.QtDemo";

/// node id → tap callback, collected from the built tree after `assign_ids`.
type TapHandlers = BTreeMap<u32, Rc<RefCell<dyn FnMut() + 'static>>>;

/// Mark a node as a value-reporting control (`BINDING_ID`), so the renderer
/// gates its `VALUE_CHANGED` events back to the app. The Rust DSL does not yet
/// expose signal property bindings, so the demo marks the slider directly on
/// the retained tree (the protocol's "this node reports" marker).
fn mark_value_bound(node: &mut Node) {
    if node.component == pathland_view::Component::Slider {
        node.properties
            .insert(pathland_core::property_id::BINDING_ID, 1);
    }
    for child in &mut node.children {
        mark_value_bound(child);
    }
}

/// Build the DSL tree for the current counter + slider value. This is the
/// developer's authoring surface.
fn build_tree(count: &Rc<RefCell<u32>>, slider_value: &Rc<RefCell<f32>>) -> Node {
    let tap_count = count.clone();
    let mut tree = vstack![
        hstack![
            text(format!("Count: {}", *count.borrow()).as_str()),
            button("Increment").on_tap_gesture(move || *tap_count.borrow_mut() += 1),
        ]
        .spacing(8.0)
        .padding(16.0),
        text("Pathland · Qt Quick · shared ring").foreground_style(Color::argb(0xFF_888888)),
        // A non-button view (Text) declaring raw pointer listeners — proving any
        // element can emit events via the `EVENT_LISTENERS` property.
        text("I am a Text with raw pointer listeners")
            .pointer_events(listener::POINTER_DOWN | listener::POINTER_UP)
            .foreground_style(Color::argb(0xFF_0000AA)),
        text(format!("Slider: {:.1}", *slider_value.borrow()).as_str()),
        Slider {
            value: *slider_value.borrow(),
            min: 0.0,
            max: 10.0,
        },
        vstack![
            text("short"),
            text("a much longer label"),
            text("x"),
        ]
        .spacing(4.0)
        .padding(16.0),
    ]
    .spacing(12.0)
    .padding(24.0)
    .build();
    mark_value_bound(&mut tree);
    tree
}

fn main() {
    let engine = Rc::new(RefCell::new(Engine::new()));
    let ring = Rc::new(RefCell::new(RingTransport::new()));
    let count = Rc::new(RefCell::new(0u32));
    let slider_value = Rc::new(RefCell::new(5.0f32));
    let handlers = Rc::new(RefCell::new(TapHandlers::new()));

    // Build + emit the current tree, refreshing the id → tap-callback map.
    let emit = {
        let engine = engine.clone();
        let ring = ring.clone();
        let count = count.clone();
        let slider_value = slider_value.clone();
        let handlers = handlers.clone();
        move || {
            let mut tree = build_tree(&count, &slider_value);
            assign_ids(&mut tree, &mut 1);
            let mut h = handlers.borrow_mut();
            h.clear();
            pathland_view::collect_tap_handlers(&tree, &mut *h);
            drop(h);

            let mut ring_ref = ring.borrow_mut();
            let mut guest = ring_ref.producer();
            guest.begin_frame();
            engine.borrow_mut().emit(&tree, &mut guest).ok();
            guest.end_frame();
        }
    };

    // Initial frame before the renderer starts pumping.
    emit();

    // Hand the renderer the shared ring and a wake handler. The renderer writes
    // native inputs as EVENT opcodes into the ring and wakes us; we drain the
    // ring, run the taps through the recognizer, invoke the matching callback,
    // and re-emit. The renderer's idle pump applies the delta.
    pathland_qt::run(APP_ID, "Pathland Qt Demo", ring.clone(), {
        let emit = emit;
        let handlers = handlers.clone();
        let slider_value = slider_value.clone();
        let start = Instant::now();
        let mut recognizer = pathland_view::TapRecognizer::new();
        move |ring| {
            let events = ring.borrow_mut().drain_events();
            let now_ms = start.elapsed().as_millis() as u64;
            let mut changed = false;
            for ev in &events {
                // Platform back (Escape/back key) arrives as a global NAVIGATE
                // request with no URL. There is no Rust router yet, so this is
                // surfaced to the console to prove the event reached the app.
                if matches!(ev, pathland_core::Event::Navigate { url: None }) {
                    eprintln!("[demo] NAVIGATE back requested");
                }
                // A bound control's value changed (the slider): update the app
                // state and re-emit — the echo-suppressed, VALUE-last renderer
                // path applies it back without a loop.
                if let pathland_core::Event::ValueChanged { value, .. } = ev {
                    *slider_value.borrow_mut() = *value;
                    changed = true;
                }
                if let Some(target) = recognizer.feed(ev, now_ms) {
                    if let Some(handler) = handlers.borrow().get(&target) {
                        let mut cb = handler.borrow_mut();
                        (&mut *cb)();
                        changed = true;
                    }
                }
            }
            if changed {
                emit();
            }
        }
    });
}