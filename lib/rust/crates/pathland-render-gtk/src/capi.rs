//! C ABI for the Pathland GTK renderer (Java host).
//!
//! A foreign host (Java via JNA) drives the `pathland-view-native` flat world to
//! build/emit the retained tree, then calls `pathland_gtk_run` to hand the
//! renderer its shared ring and run GTK in-process. Raw-input events are
//! reported back through a C callback (`void (*)(u32 targetId)`).
//!
//! A second route — `pathland_gtk_run_ring` — lets a host that emits opcodes
//! itself (e.g. the Java DSL's `libpathland_core` ring) hand the renderer the
//! borrowed ring directly, with no `pathland-view-native` host involved.

use std::cell::RefCell;
use std::ffi::c_void;
use std::rc::Rc;

use pathland_native::NativeHost;
use pathland_core::Event;
use pathland_core_transport::{DriverTransport, FrameSource, OpcodeBatch, RingTransport, TransportError};

use crate::{run_with_pump_sized, Pump};

/// A host-supplied event callback, invoked (with no payload) when the renderer
/// has written a raw input into the host → guest event ring. The host drains the
/// ring itself (via `pathland_native_drain_events`) to receive the events.
pub type EventCallback = extern "C" fn();

/// A `Pump` over a `pathland-view-native` `NativeHost` owned by the foreign host.
///
/// The renderer borrows the host through a raw pointer for the duration of
/// `pathland_gtk_run`; the foreign host keeps driving the same host (via its
/// own handle) from the event callback. All access is on the GTK main thread.
struct NativeHostHandle(*mut NativeHost);

impl Pump for NativeHostHandle {
    fn next_frame(&mut self) -> Result<Option<OpcodeBatch<'_>>, TransportError> {
        // SAFETY: single-threaded (GTK main thread); the foreign host must not
        // destroy the host until `pathland_gtk_run` returns.
        unsafe { FrameSource::next_frame(&mut *self.0) }
    }

    fn send_input(&mut self, event: &Event) -> Result<(), TransportError> {
        // SAFETY: as above.
        unsafe { DriverTransport::send_input(&mut *self.0, event) }
    }
}

/// A `Pump` over a `libpathland_core` `RingTransport` borrowed from the foreign
/// host. Same contract as [`NativeHostHandle`]: single-threaded (GTK main
/// thread), and the host must not destroy the core handle until
/// `pathland_gtk_run_ring` returns.
struct CoreRingHandle(*mut RingTransport);

impl Pump for CoreRingHandle {
    fn next_frame(&mut self) -> Result<Option<OpcodeBatch<'_>>, TransportError> {
        // SAFETY: single-threaded (GTK main thread); the ring outlives the call.
        unsafe { FrameSource::next_frame(&mut *self.0) }
    }

    fn send_input(&mut self, event: &Event) -> Result<(), TransportError> {
        // SAFETY: as above.
        unsafe { DriverTransport::send_input(&mut *self.0, event) }
    }
}

/// Run the GTK renderer over a `pathland-view-native` host's shared ring.
///
/// `host` is the opaque handle from `pathland_native_create`; the renderer
/// pumps its ring in-process (frames in, events out). `on_event` is called
/// (on the GTK main thread, with no payload) whenever a raw input was written;
/// the host then drains the event ring via `pathland_native_drain_events` and
/// may re-enter `pathland_native_*` to update the tree and re-emit.
///
/// This function blocks until the GTK main loop exits (window closed).
#[no_mangle]
pub unsafe extern "C" fn pathland_gtk_run(
    host: *mut c_void,
    on_event: Option<EventCallback>,
) {
    if host.is_null() {
        return;
    }

    let handle = NativeHostHandle(host as *mut NativeHost);
    let pump = Rc::new(RefCell::new(handle));

    // Pass a bare program name to GTK: the real process argv is the JVM's
    // (`-cp`/`-D` flags + main class), which GTK must not parse as its own
    // options. A single non-option argument avoids both the "unknown option"
    // error and the empty-argv activation quirk on macOS.
    run_with_pump_sized(
        "org.pathland.GtkDemo",
        "Pathland GTK Demo",
        pump,
        &["GtkDemo"],
        move |_pump| {
            if let Some(cb) = on_event {
                cb();
            }
        },
        420,
        220,
    );
}

/// Run the GTK renderer over a `libpathland_core` ring borrowed in-process.
///
/// `ring` is the pointer from `pathland_core_ring_mut` (the underlying
/// `RingTransport` of a `pathland_core_create` handle). This is the route for
/// hosts that emit opcodes themselves (e.g. the Java DSL writes into the ring
/// and the renderer pumps the same ring in place) — no `pathland-view-native`
/// host is involved. `on_event` wakes the host (no payload) whenever a raw
/// input was written; the host drains via `pathland_core_drain_events` and may
/// re-enter the ring to emit deltas.
///
/// `width`/`height` size the default window (0 = renderer default 420×220).
/// This function blocks until the GTK main loop exits (window closed).
#[no_mangle]
pub unsafe extern "C" fn pathland_gtk_run_ring(
    ring: *mut c_void,
    on_event: Option<EventCallback>,
    width: u32,
    height: u32,
) {
    if ring.is_null() {
        return;
    }

    let handle = CoreRingHandle(ring as *mut RingTransport);
    let pump = Rc::new(RefCell::new(handle));

    // Bare program name for GTK (see `pathland_gtk_run`).
    run_with_pump_sized(
        "org.pathland.GtkDemo",
        "Pathland GTK Demo",
        pump,
        &["GtkDemo"],
        move |_pump| {
            if let Some(cb) = on_event {
                cb();
            }
        },
        width.max(1) as i32,
        height.max(1) as i32,
    );
}
