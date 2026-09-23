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

use std::ffi::c_void;
use std::ffi::CString;

use pathland_core::Event;
use pathland_core_transport::{
    DriverTransport, FrameSource, OpcodeBatch, RingTransport, TransportError,
};

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
        Ok(Some(batch)) => runner.renderer.apply_frame(batch.frame()),
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
            user,
        );
    }
}