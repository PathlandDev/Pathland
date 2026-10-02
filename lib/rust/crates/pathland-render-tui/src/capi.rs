//! The flat C ABI for foreign hosts (Java via JNA).

use std::cell::RefCell;
use std::ffi::c_void;
use std::rc::Rc;

use pathland_core::Event;
use pathland_core_transport::{DriverTransport, FrameSource, OpcodeBatch, RingTransport, TransportError};

use crate::run;

/// A `Pump` over a `libpathland_core` `RingTransport` borrowed from the foreign
/// host. Same contract as the GTK renderer's `CoreRingHandle`: single-threaded
/// (the TUI event loop runs on the caller's thread), and the host must not
/// destroy the core handle until `pathland_tui_run_ring` returns.
struct CoreRingHandle(*mut RingTransport);

impl FrameSource for CoreRingHandle {
    fn next_frame(&mut self) -> Result<Option<OpcodeBatch<'_>>, TransportError> {
        // SAFETY: single-threaded (the caller's terminal thread); the ring
        // outlives the call.
        unsafe { FrameSource::next_frame(&mut *self.0) }
    }
}

impl DriverTransport for CoreRingHandle {
    fn send_input(&mut self, event: &Event) -> Result<(), TransportError> {
        // SAFETY: as above.
        unsafe { DriverTransport::send_input(&mut *self.0, event) }
    }
}

/// Run the TUI renderer over a `libpathland_core` ring borrowed in-process.
///
/// `ring` is the pointer from `pathland_core_ring_mut` (the underlying
/// `RingTransport` of a `pathland_core_create` handle). This is the route for
/// hosts that emit opcodes themselves (e.g. the Java DSL writes into the ring
/// and the renderer pumps the same ring in place). `on_event` wakes the host
/// (no payload) whenever a raw input was written; the host drains via
/// `pathland_core_drain_events` and may re-enter the ring to emit deltas.
///
/// Blocks until the user quits (`q` or Ctrl+C).
#[no_mangle]
pub unsafe extern "C" fn pathland_tui_run_ring(
    ring: *mut c_void,
    on_event: Option<extern "C" fn()>,
) {
    if ring.is_null() {
        return;
    }

    let handle = CoreRingHandle(ring as *mut RingTransport);
    let pump = Rc::new(RefCell::new(handle));
    run(pump, move || {
        if let Some(cb) = on_event {
            cb();
        }
    });
}