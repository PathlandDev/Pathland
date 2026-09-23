//! The Qt main-loop shell: owns the `Pump` (frames in / events out) and the
//! retained renderer, and hands the C++ shell a tick callback so the ring is
//! pumped on the Qt main thread between frames — the Qt analogue of the GTK
//! renderer's glib idle pump.
//!
//! Contract (mirrors `pathland-render-gtk`):
//! - The renderer **never drains events** — it writes `EVENT` opcodes via
//!   `Pump::send_input` and wakes the host; the host drains its own ring.
//! - All access is on the Qt main thread; the host re-enters the ring from the
//!   wake callback on the same thread (the ring is the decoupling buffer, so a
//!   control signal firing during frame apply needs no deferred flush).

use std::cell::RefCell;
use std::ffi::c_void;
use std::ffi::CString;
use std::rc::Rc;

use pathland_core::tokens::Scheme;
use pathland_core::{category, meta, Event};
use pathland_core_transport::{
    DriverTransport, FrameSource, OpcodeBatch, RingTransport, TransportError,
};

use crate::ffi::{ev, QtEvent};
use crate::renderer::QtRenderer;

/// The bidirectional transport surface the renderer pumps: frames in
/// (guest → host) and events out (host → guest).
pub trait Pump {
    /// Read the next guest → host frame.
    fn next_frame(&mut self) -> Result<Option<OpcodeBatch<'_>>, TransportError>;
    /// Write a host → guest raw input as an `EVENT` opcode.
    fn send_input(&mut self, event: &Event) -> Result<(), TransportError>;
}

impl Pump for RingTransport {
    fn next_frame(&mut self) -> Result<Option<OpcodeBatch<'_>>, TransportError> {
        FrameSource::next_frame(self)
    }
    fn send_input(&mut self, event: &Event) -> Result<(), TransportError> {
        DriverTransport::send_input(self, event)
    }
}

/// A host-supplied event callback, invoked (with no payload) when the renderer
/// has written a raw input into the host → guest event ring. The host drains
/// the ring itself.
pub type EventCallback = extern "C" fn();

/// What to do with a Qt raw input.
enum QtAction {
    /// Encode + write into the event ring, then wake the host.
    Send(Event),
    /// A platform color-scheme change: re-resolve tokens in the renderer
    /// (no host wake — the re-apply flows back as `SET_PROPERTY` commands).
    Scheme(Scheme),
    None,
}

/// Decode a `QtEvent` (C++ → Rust) into an action.
fn decode_event(raw: &QtEvent) -> QtAction {
    match raw.kind {
        ev::POINTER_DOWN => QtAction::Send(Event::PointerDown {
            target: raw.node_id,
            x: raw.x,
            y: raw.y,
            secondary: false,
        }),
        ev::POINTER_MOVE => QtAction::Send(Event::PointerMove {
            target: raw.node_id,
            x: raw.x,
            y: raw.y,
            hovering: false,
            leaving: false,
        }),
        ev::POINTER_UP => QtAction::Send(Event::PointerUp {
            target: raw.node_id,
            x: raw.x,
            y: raw.y,
            secondary: false,
        }),
        ev::VALUE_CHANGED => QtAction::Send(Event::ValueChanged {
            target: raw.node_id,
            value: raw.value,
        }),
        ev::TEXT_CHANGED => {
            // The string is length-prefixed, not guaranteed NUL-terminated.
            let slice =
                unsafe { std::slice::from_raw_parts(raw.str_ as *const u8, raw.str_len as usize) };
            QtAction::Send(Event::TextChanged {
                target: raw.node_id,
                value: String::from_utf8_lossy(slice).into_owned(),
            })
        }
        ev::NAVIGATE_BACK => QtAction::Send(Event::Navigate { url: None }),
        ev::SCHEME_CHANGED => {
            QtAction::Scheme(if raw.value >= 0.5 { Scheme::Dark } else { Scheme::Light })
        }
        _ => QtAction::None,
    }
}

/// Whether a frame carries `META::RESET` (clear all output).
fn frame_has_reset(frame: &pathland_core::Frame<'_>) -> bool {
    frame
        .opcodes()
        .any(|op| op.category() == category::META && op.command() == meta::RESET)
}

// ---------------------------------------------------------------------------
// C-ABI path: a boxed `Pump` over an opaque host/ring pointer (capi.rs).
// ---------------------------------------------------------------------------

/// The per-run state held on the stack of [`run_with_pump_sized`]; the C++
/// shell receives an opaque pointer to it.
struct Runner {
    pump: Box<dyn Pump>,
    renderer: QtRenderer,
    on_event: Option<EventCallback>,
}

/// Pump one tick: flush the next pending frame into the renderer.
fn pump_once(runner: &mut Runner) {
    match runner.pump.next_frame() {
        Ok(Some(batch)) => {
            let frame = batch.frame();
            if frame_has_reset(frame) {
                runner.renderer.reset();
            } else {
                runner.renderer.apply_frame(frame);
            }
        }
        Ok(None) => {}
        Err(_) => {}
    }
}

/// Qt timer tick → Rust pump (the C++ shell calls this with the `Runner` ptr).
pub extern "C" fn qt_tick(user: *mut c_void) {
    if user.is_null() {
        return;
    }
    // SAFETY: single-threaded (Qt main thread); `Runner` outlives `run`.
    let runner = unsafe { &mut *(user as *mut Runner) };
    pump_once(runner);
}

/// Qt wake (a control reported an input) → host callback. The renderer does
/// not decode the event; it only wakes the host, which drains its own ring.
pub extern "C" fn qt_wake(user: *mut c_void) {
    if user.is_null() {
        return;
    }
    // SAFETY: as above.
    let runner = unsafe { &*(user as *const Runner) };
    if let Some(cb) = runner.on_event {
        cb();
    }
}

/// Qt raw input (C++ → Rust): encode the `Event`, write it into the ring, then
/// wake the host. The host drains its own ring — the renderer never drains.
/// A scheme change instead re-resolves tokens in the renderer (no host wake).
pub extern "C" fn qt_event(ev: *const QtEvent, user: *mut c_void) {
    if ev.is_null() || user.is_null() {
        return;
    }
    // SAFETY: single-threaded (Qt main thread); `ev` is valid for the call.
    let raw = unsafe { &*ev };
    let runner = unsafe { &mut *(user as *mut Runner) };

    match decode_event(raw) {
        QtAction::Send(event) => {
            if let Err(e) = runner.pump.send_input(&event) {
                eprintln!("pathland-render-qt: send_input failed: {e:?}");
            }
            if let Some(cb) = runner.on_event {
                cb();
            }
        }
        QtAction::Scheme(scheme) => runner.renderer.set_scheme(scheme),
        QtAction::None => {}
    }
}

/// Run the Qt renderer over any `Pump`, blocking until the Qt event loop exits.
pub fn run_with_pump_sized(
    title: &str,
    pump: Box<dyn Pump>,
    on_event: Option<EventCallback>,
    width: i32,
    height: i32,
) {
    let mut runner = Runner {
        pump,
        renderer: QtRenderer::new(),
        on_event,
    };
    let user = &mut runner as *mut Runner as *mut c_void;
    let title = CString::new(title).unwrap_or_else(|_| CString::new("Pathland").unwrap());
    // Blocks until the window closes (Qt main loop exits).
    unsafe {
        crate::ffi::pathland_qt_layer_run(
            title.as_ptr(),
            width,
            height,
            qt_wake,
            qt_tick,
            qt_event,
            user,
        );
    }
}

// ---------------------------------------------------------------------------
// Rust-demo path: a shared `Rc<RefCell<RingTransport>>` + a host closure
// (mirrors `pathland_render_gtk::run`). The pump borrow is held across frame
// application, so the batch's zero-copy view of the shared ring stays valid.
// ---------------------------------------------------------------------------

/// Per-run state for the shared-ring convenience path.
struct RingRunner {
    ring: Rc<RefCell<RingTransport>>,
    renderer: QtRenderer,
    on_event: Option<EventCallback>,
}

// The host's drain-and-respond closure, stored for the wake trampoline
// (a regular comment: `thread_local!` doesn't carry doc comments).
thread_local! {
    static RING_HOST: RefCell<Option<Box<dyn FnMut()>>> = const { RefCell::new(None) };
}

fn pump_once_ring(runner: &mut RingRunner) {
    let guard = runner.ring.try_borrow_mut();
    if let Ok(mut p) = guard {
        if let Ok(Some(batch)) = FrameSource::next_frame(&mut *p) {
            let frame = batch.frame();
            if frame_has_reset(frame) {
                runner.renderer.reset();
            } else {
                runner.renderer.apply_frame(frame);
            }
        }
    }
}

extern "C" fn ring_tick(user: *mut c_void) {
    if user.is_null() {
        return;
    }
    // SAFETY: single-threaded (Qt main thread); `RingRunner` outlives `run`.
    let runner = unsafe { &mut *(user as *mut RingRunner) };
    pump_once_ring(runner);
}

extern "C" fn ring_host_wake(user: *mut c_void) {
    if user.is_null() {
        return;
    }
    // SAFETY: as above.
    let runner = unsafe { &*(user as *const RingRunner) };
    if let Some(cb) = runner.on_event {
        cb();
    }
}

extern "C" fn ring_event(ev: *const QtEvent, user: *mut c_void) {
    if ev.is_null() || user.is_null() {
        return;
    }
    // SAFETY: single-threaded (Qt main thread).
    let raw = unsafe { &*ev };
    let runner = unsafe { &mut *(user as *mut RingRunner) };

    match decode_event(raw) {
        QtAction::Send(event) => {
            let guard = runner.ring.try_borrow_mut();
            if let Ok(mut p) = guard {
                let _ = DriverTransport::send_input(&mut *p, &event);
            }
            if let Some(cb) = runner.on_event {
                cb();
            }
        }
        QtAction::Scheme(scheme) => runner.renderer.set_scheme(scheme),
        QtAction::None => {}
    }
}

/// Run the Qt renderer over a shared `RingTransport`, calling `on_event` (with
/// the ring) whenever the renderer wrote raw inputs. Mirrors
/// `pathland_render_gtk::run`. Blocks until the window closes.
pub fn run<F>(_app_id: &str, title: &str, ring: Rc<RefCell<RingTransport>>, on_event: F)
where
    F: FnMut(Rc<RefCell<RingTransport>>) + 'static,
{
    // A Rust closure can't be an `extern "C" fn`; store it for the trampoline.
    let ring_for_host = ring.clone();
    RING_HOST.with(|h| {
        let mut cb = on_event;
        *h.borrow_mut() = Some(Box::new(move || cb(ring_for_host.clone())));
    });
    extern "C" fn host_trampoline() {
        RING_HOST.with(|h| {
            if let Some(cb) = h.borrow_mut().as_mut() {
                cb();
            }
        });
    }

    let mut runner = RingRunner {
        ring,
        renderer: QtRenderer::new(),
        on_event: Some(host_trampoline),
    };
    let user = &mut runner as *mut RingRunner as *mut c_void;
    let title = CString::new(title).unwrap_or_else(|_| CString::new("Pathland").unwrap());
    unsafe {
        crate::ffi::pathland_qt_layer_run(
            title.as_ptr(),
            420,
            220,
            ring_host_wake,
            ring_tick,
            ring_event,
            user,
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::cell::Cell;
    use std::ffi::c_char;

    // Thread-local so parallel tests don't share wake counts.
    thread_local! {
        static WAKES: Cell<usize> = const { Cell::new(0) };
    }

    extern "C" fn count_wake() {
        WAKES.with(|w| w.set(w.get() + 1));
    }

    fn wakes() -> usize {
        WAKES.with(|w| w.get())
    }

    fn qt_event(kind: u8, node_id: u32, value: f32) -> QtEvent {
        QtEvent {
            kind,
            node_id,
            x: 3.0,
            y: 4.0,
            value,
            str_len: 0,
            str_: std::ptr::null(),
        }
    }

    #[test]
    fn decode_maps_pointer_and_global_events() {
        // Pointer down/move/up -> raw pointer events.
        match decode_event(&qt_event(ev::POINTER_DOWN, 7, 0.0)) {
            QtAction::Send(Event::PointerDown { target, x, y, secondary }) => {
                assert_eq!(target, 7);
                assert_eq!((x, y), (3.0, 4.0));
                assert!(!secondary);
            }
            _ => panic!("POINTER_DOWN must map to Event::PointerDown"),
        }
        match decode_event(&qt_event(ev::POINTER_MOVE, 7, 0.0)) {
            QtAction::Send(Event::PointerMove { target, .. }) => assert_eq!(target, 7),
            _ => panic!("POINTER_MOVE must map to Event::PointerMove"),
        }
        match decode_event(&qt_event(ev::POINTER_UP, 7, 0.0)) {
            QtAction::Send(Event::PointerUp { target, .. }) => assert_eq!(target, 7),
            _ => panic!("POINTER_UP must map to Event::PointerUp"),
        }
        // Global back request.
        match decode_event(&qt_event(ev::NAVIGATE_BACK, 0, 0.0)) {
            QtAction::Send(Event::Navigate { url: None }) => {}
            _ => panic!("NAVIGATE_BACK must map to Event::Navigate {{ url: None }}"),
        }
        // Scheme change -> renderer-side action, not a ring write.
        assert!(matches!(
            decode_event(&qt_event(ev::SCHEME_CHANGED, 0, 1.0)),
            QtAction::Scheme(Scheme::Dark)
        ));
        assert!(matches!(
            decode_event(&qt_event(ev::SCHEME_CHANGED, 0, 0.0)),
            QtAction::Scheme(Scheme::Light)
        ));
        assert!(matches!(decode_event(&qt_event(99, 0, 0.0)), QtAction::None));
    }

    #[test]
    fn ring_event_writes_pointer_into_ring_and_wakes() {
        let ring = Rc::new(RefCell::new(RingTransport::new()));
        WAKES.with(|w| w.set(0));
        let mut runner = RingRunner {
            ring: ring.clone(),
            renderer: QtRenderer::new(),
            on_event: Some(count_wake),
        };
        let user = &mut runner as *mut RingRunner as *mut c_void;

        let raw = qt_event(ev::POINTER_DOWN, 7, 0.0);
        ring_event(&raw, user);

        // The event landed in the ring and the host was woken.
        assert_eq!(wakes(), 1);
        let events = ring.borrow_mut().drain_events();
        assert_eq!(events.len(), 1);
        assert!(matches!(
            &events[0],
            Event::PointerDown { target: 7, .. }
        ));
    }

    #[test]
    fn scheme_event_reapplies_without_waking() {
        let ring = Rc::new(RefCell::new(RingTransport::new()));
        WAKES.with(|w| w.set(0));
        let mut runner = RingRunner {
            ring: ring.clone(),
            renderer: QtRenderer::new(),
            on_event: Some(count_wake),
        };
        let user = &mut runner as *mut RingRunner as *mut c_void;

        let raw = qt_event(ev::SCHEME_CHANGED, 0, 1.0);
        ring_event(&raw, user);

        // No ring write, no host wake — only a renderer-internal re-apply.
        assert_eq!(wakes(), 0);
        assert!(ring.borrow_mut().drain_events().is_empty());
    }

    #[test]
    fn text_event_string_is_length_prefixed() {
        let ring = Rc::new(RefCell::new(RingTransport::new()));
        let mut runner = RingRunner {
            ring: ring.clone(),
            renderer: QtRenderer::new(),
            on_event: None,
        };
        let user = &mut runner as *mut RingRunner as *mut c_void;

        let text = b"hello"; // deliberately not NUL-terminated
        let raw = QtEvent {
            kind: ev::TEXT_CHANGED,
            node_id: 9,
            x: 0.0,
            y: 0.0,
            value: 0.0,
            str_len: text.len() as u32,
            str_: text.as_ptr() as *const c_char,
        };
        ring_event(&raw, user);
        let events = ring.borrow_mut().drain_events();
        assert_eq!(events.len(), 1);
        match &events[0] {
            Event::TextChanged { target, value } => {
                assert_eq!(*target, 9);
                assert_eq!(value, "hello");
            }
            other => panic!("expected TextChanged, got {other:?}"),
        }
    }

    #[test]
    fn frame_has_reset_detects_meta_reset() {
        use pathland_core::Opcode;

        let reset = Opcode::from_bytes(&[
            category::META, meta::RESET, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        ]);
        let mut slots = Vec::new();
        slots.extend_from_slice(&reset.to_bytes());
        let frame = pathland_core::Frame::from_parts(&slots, &[], 0, slots.len());
        assert!(frame_has_reset(&frame), "META::RESET must be detected");

        let create = Opcode::from_bytes(&[
            category::TREE, 0x01, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        ]);
        let mut slots2 = Vec::new();
        slots2.extend_from_slice(&create.to_bytes());
        let frame2 = pathland_core::Frame::from_parts(&slots2, &[], 0, slots2.len());
        assert!(!frame_has_reset(&frame2), "non-reset frames must not reset");
    }
}