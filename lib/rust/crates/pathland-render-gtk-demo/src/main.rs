//! # Pathland GTK4 desktop demo
//!
//! A native GTK4 app demonstrating Pathland's **zero-copy shared-memory ring**
//! and the shared **GTK renderer** (`pathland-render-gtk`).
//!
//! The developer authors the UI with the SwiftUI-style DSL; the diff emitter in
//! `pathland-core` emits declarative 16-byte opcodes into the shared ring, and
//! the `pathland-render-gtk` renderer maps them onto native GTK widgets. The demo
//! never touches GTK/glib/pango directly — it only drives Pathland and the
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
use pathland_core::{listener, size};
use pathland_core_transport::RingTransport;
use pathland_view::{
    assign_ids, button, hstack, text, vstack, Align, Color, Configurable, ForegroundStyle, Frame,
    Node, Padding, PointerEvents, TapGesture, View, ViewExt,
};

const APP_ID: &str = "org.pathland.GtkDemo";

/// node id → tap callback, collected from the built tree after `assign_ids`.
type TapHandlers = BTreeMap<u32, Rc<RefCell<dyn FnMut() + 'static>>>;

/// Build the DSL tree for the current counter value. This is the developer's
/// authoring surface.
fn build_tree(count: &Rc<RefCell<u32>>) -> Node {
    let tap_count = count.clone();
    vstack![
        hstack![
            text(format!("Count: {}", *count.borrow()).as_str())
                .modifiers(Frame::new(Some(size::FILL), None, None)),
            button("Increment").modifiers(TapGesture::new(move || *tap_count.borrow_mut() += 1)),
        ]
        .with(|v| {
            v.spacing(8.0);
        })
        .modifiers(Padding(16.0)),
        text("Pathland · GTK4 · shared ring").modifiers(ForegroundStyle(Color::argb(0xFF_888888))),
        // A non-button view (Text) declaring raw pointer listeners — proving any
        // element can emit events via the `EVENT_LISTENERS` property.
        text("I am a Text with raw pointer listeners").modifiers((
            PointerEvents(listener::POINTER_DOWN | listener::POINTER_UP),
            ForegroundStyle(Color::argb(0xFF_0000AA)),
        )),
        vstack![
            text("short"),
            text("a much longer label"),
            text("x"),
        ]
        .with(|v| {
            v.spacing(4.0);
        })
        .modifiers((Padding(16.0), Frame::new(None, None, Some(Align::Center)))),
    ]
    .with(|v| {
        v.spacing(12.0);
    })
    .modifiers(Padding(24.0))
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
    // native inputs as EVENT opcodes into the ring and wakes us; we drain the
    // ring, run the taps through the recognizer, invoke the matching callback,
    // and re-emit. The renderer's idle pump applies the delta.
    pathland_gtk::run(APP_ID, "Pathland GTK4 Demo", ring.clone(), {
        let emit = emit;
        let handlers = handlers.clone();
        let start = Instant::now();
        let mut recognizer = pathland_view::TapRecognizer::new();
        move |ring| {
            let events = ring.borrow_mut().drain_events();
            let now_ms = start.elapsed().as_millis() as u64;
            let mut tapped = false;
            for ev in &events {
                // Platform back (Escape/back key) arrives as a global NAVIGATE
                // request with no URL. There is no Rust router yet, so this is
                // surfaced to the console to prove the event reached the app.
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
        }
    });
}
