//! # Pathland TUI (Ratatui) desktop demo
//!
//! A terminal app demonstrating Pathland's **zero-copy shared-memory ring** and
//! the shared **Ratatui renderer** (`pathland-render-tui`).
//!
//! The developer authors the UI with the SwiftUI-style DSL; the diff emitter in
//! `pathland-engine` emits declarative 16-byte opcodes into the shared ring, and
//! the `pathland-render-tui` renderer maps them onto terminal widgets. The demo
//! never touches ratatui widgets directly — it only drives Pathland and the
//! renderer.
//!
//! Interaction is declared with `.on_tap_gesture(...)`; raw pointer events
//! round-trip through the shared ring and are turned back into taps by the
//! app-side [`TapRecognizer`](pathland_view::TapRecognizer). Quit with `q` or
//! `Ctrl+C`.

use std::cell::RefCell;
use std::collections::BTreeMap;
use std::rc::Rc;
use std::time::Instant;

use pathland_core::size;
use pathland_core_transport::RingTransport;
use pathland_engine::Engine;
use pathland_render_tui::run;
use pathland_view::{assign_ids, button, hstack, text, vstack, Color, Node, View, ViewExt};

/// node id → tap callback, collected from the built tree after `assign_ids`.
type TapHandlers = BTreeMap<u32, Rc<RefCell<dyn FnMut() + 'static>>>;

/// Build the DSL tree for the current counter value. This is the developer's
/// authoring surface.
fn build_tree(count: &Rc<RefCell<u32>>) -> Node {
    let tap_count = count.clone();
    vstack![
        hstack![
            text(format!("Count: {}", *count.borrow()).as_str()).frame(Some(size::FILL), None, None),
            button("Increment").on_tap_gesture(move || *tap_count.borrow_mut() += 1),
        ]
        .spacing(8.0)
        .padding(16.0),
        text("Pathland · TUI · shared ring").foreground_style(Color::argb(0xFF_888888)),
        vstack![text("short"), text("a much longer label"), text("x")]
            .spacing(4.0)
            .padding(16.0),
    ]
    .spacing(12.0)
    .padding(24.0)
    .build()
}

fn main() {
    let engine = Rc::new(RefCell::new(Engine::new()));
    let ring = Rc::new(RefCell::new(RingTransport::new()));
    let count = Rc::new(RefCell::new(0u32));
    let handlers = Rc::new(RefCell::new(TapHandlers::new()));

    // Build + emit the current tree, refreshing the id → tap-callback map.
    let emit = {
        let engine = engine.clone();
        let ring = ring.clone();
        let count = count.clone();
        let handlers = handlers.clone();
        move || {
            let mut tree = build_tree(&count);
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
    // raw inputs as EVENT opcodes into the ring and wakes us; we drain the
    // ring, run the taps through the recognizer, invoke the matching callback,
    // and re-emit. The renderer applies the delta on the next loop iteration.
    let start = Instant::now();
    let mut recognizer = pathland_view::TapRecognizer::new();
    run(ring.clone(), move || {
        let events = ring.borrow_mut().drain_events();
        let now_ms = start.elapsed().as_millis() as u64;
        let mut tapped = false;
        for ev in &events {
            // Esc (no text input focused) arrives as a global NAVIGATE request.
            // There is no Rust router yet, so this is surfaced to the console
            // to prove the event reached the app.
            if matches!(ev, pathland_core::Event::Navigate { url: None }) {
                eprintln!("[demo] NAVIGATE back requested");
            }
            if let Some(target) = recognizer.feed(ev, now_ms) {
                if let Some(handler) = handlers.borrow().get(&target) {
                    let mut cb = handler.borrow_mut();
                    (&mut *cb)();
                    tapped = true;
                }
            }
        }
        if tapped {
            emit();
        }
    });
}