//! C ABI for the Pathland Qt renderer (foreign hosts, e.g. Java via JNA).
//!
//! Mirrors `pathland-render-gtk`'s `capi.rs`:
//! - `pathland_qt_run(host, on_event)` — pump a `pathland-view-native`
//!   `NativeHost`'s ring in-process.
//! - `pathland_qt_run_ring(ring, on_event, width, height)` — pump a
//!   `libpathland_core` ring borrowed in-process (the route for hosts that
//!   emit opcodes themselves, e.g. the Java DSL's `RingOpcodeSink`).
//!
//! Both block until the Qt event loop exits and pass a bare program name to
//! Qt (the process argv belongs to the embedding host — e.g. a JVM's `-cp` /
//! `-D` flags — and must not be parsed as Qt options).

use std::ffi::c_void;

use pathland_core::Event;
use pathland_core_transport::{
    DriverTransport, FrameSource, OpcodeBatch, RingTransport, TransportError,
};
use pathland_native::NativeHost;

use crate::run::{run_with_pump_sized, EventCallback, Pump};

/// A `Pump` over a `pathland-view-native` `NativeHost` owned by the foreign host.
struct NativeHostHandle(*mut NativeHost);

impl Pump for NativeHostHandle {
    fn next_frame(&mut self) -> Result<Option<OpcodeBatch<'_>>, TransportError> {
        // SAFETY: single-threaded (Qt main thread); the host must not destroy
        // the host until `pathland_qt_run` returns.
        unsafe { FrameSource::next_frame(&mut *self.0) }
    }

    fn send_input(&mut self, event: &Event) -> Result<(), TransportError> {
        // SAFETY: as above.
        unsafe { DriverTransport::send_input(&mut *self.0, event) }
    }
}

/// A `Pump` over a `libpathland_core` `RingTransport` borrowed from the foreign
/// host. Same contract as [`NativeHostHandle`].
struct CoreRingHandle(*mut RingTransport);

impl Pump for CoreRingHandle {
    fn next_frame(&mut self) -> Result<Option<OpcodeBatch<'_>>, TransportError> {
        // SAFETY: single-threaded (Qt main thread); the ring outlives the call.
        unsafe { FrameSource::next_frame(&mut *self.0) }
    }

    fn send_input(&mut self, event: &Event) -> Result<(), TransportError> {
        // SAFETY: as above.
        unsafe { DriverTransport::send_input(&mut *self.0, event) }
    }
}

/// Run the Qt renderer over a `pathland-view-native` host's shared ring.
///
/// `host` is the opaque handle from `pathland_native_create`; the renderer
/// pumps its ring in-process (frames in, events out). `on_event` is called
/// (on the Qt main thread, with no payload) whenever a raw input was written;
/// the host then drains the event ring via `pathland_native_drain_events` and
/// may re-enter `pathland_native_*` to update the tree and re-emit.
///
/// Blocks until the Qt event loop exits (window closed).
#[no_mangle]
pub unsafe extern "C" fn pathland_qt_run(host: *mut c_void, on_event: Option<EventCallback>) {
    if host.is_null() {
        return;
    }
    let pump: Box<dyn Pump> = Box::new(NativeHostHandle(host as *mut NativeHost));
    run_with_pump_sized(
        "Pathland Qt Demo",
        pump,
        on_event,
        420,
        220,
    );
}

/// Run the Qt renderer over a `libpathland_core` ring borrowed in-process.
///
/// `ring` is the pointer from `pathland_core_ring_mut` (the underlying
/// `RingTransport` of a `pathland_core_create` handle). This is the route for
/// hosts that emit opcodes themselves — no `pathland-view-native` host is
/// involved. `width`/`height` size the default window (0 = renderer default).
/// Blocks until the Qt event loop exits.
#[no_mangle]
pub unsafe extern "C" fn pathland_qt_run_ring(
    ring: *mut c_void,
    on_event: Option<EventCallback>,
    width: u32,
    height: u32,
) {
    if ring.is_null() {
        return;
    }
    let pump: Box<dyn Pump> = Box::new(CoreRingHandle(ring as *mut RingTransport));
    run_with_pump_sized(
        "Pathland Qt Demo",
        pump,
        on_event,
        width.max(1) as i32,
        height.max(1) as i32,
    );
}