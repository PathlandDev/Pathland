//! C ABI for the Pathland native host.
//!
//! Foreign host languages (Swift, Java, C#, …) link this `cdylib` and call the
//! `pathland_native_*` functions. The surface is the **flat world** — no
//! per-component wrappers — and the ring is exposed as a raw pointer + length so
//! the foreign driver reads opcodes **in place** (zero-copy).
//!
//! ```c
//! void* h = pathland_native_create();
//! pathland_native_create_node(h, 1, 2);            // 2 = vstack
//! pathland_native_create_node(h, 2, 3);            // 3 = text
//! pathland_native_set_text(h, 2, "Hello");
//! pathland_native_insert_child(h, 1, 2, UINT32_MAX);
//! pathland_native_emit(h);
//! // read opcodes from pathland_native_ring_ptr(h) (header at offset 0)
//! pathland_native_destroy(h);
//! ```

use std::ffi::{c_char, c_void, CStr};

use pathland_engine::{SignalId, SignalValue};

use super::{component_from_id, MediaControllerSlot, NativeHost};

/// Opaque handle to a [`NativeHost`].
pub type HostHandle = *mut c_void;

// ---------------------------------------------------------------------------
// Lifetime
// ---------------------------------------------------------------------------

/// Create a native host over a fresh zero-copy shared ring. Returns an opaque
/// handle (non-null on success).
#[no_mangle]
pub extern "C" fn pathland_native_create() -> HostHandle {
    let host = Box::new(NativeHost::new());
    Box::into_raw(host) as HostHandle
}

/// Destroy a native host previously created by [`pathland_native_create`].
#[no_mangle]
pub unsafe extern "C" fn pathland_native_destroy(host: HostHandle) {
    if !host.is_null() {
        drop(Box::from_raw(host as *mut NativeHost));
    }
}

fn as_host<'a>(host: HostHandle) -> &'a mut NativeHost {
    unsafe { &mut *(host as *mut NativeHost) }
}

// ---------------------------------------------------------------------------
// Flat builder (variants as raw u32)
// ---------------------------------------------------------------------------

/// `component_id` here is the raw protocol component id (hstack=1, vstack=2,
/// text=3, button=4, spacer=8). Returns 1 on success, 0 on bad component.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_create_node(
    host: HostHandle,
    id: u32,
    component: u32,
) -> u8 {
    let Some(component) = component_from_id(component) else {
        return 0;
    };
    as_host(host).create_node(id, component);
    1
}

#[no_mangle]
pub unsafe extern "C" fn pathland_native_insert_child(
    host: HostHandle,
    parent: u32,
    child: u32,
    index: u32,
) -> u8 {
    as_host(host).insert_child(parent, child, index);
    1
}

#[no_mangle]
pub unsafe extern "C" fn pathland_native_remove_child(host: HostHandle, parent: u32, child: u32) -> u8 {
    as_host(host).remove_child(parent, child);
    1
}

/// Set a node's text content from a NUL-terminated UTF-8 C string.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_set_text(
    host: HostHandle,
    id: u32,
    text: *const c_char,
) -> u8 {
    if text.is_null() {
        return 0;
    }
    let text = CStr::from_ptr(text).to_string_lossy().into_owned();
    as_host(host).set_text(id, &text);
    1
}

/// Set a property with a raw u32 value (bit pattern — floats are passed via
/// `pathland_native_set_property_f32`).
#[no_mangle]
pub unsafe extern "C" fn pathland_native_set_property(
    host: HostHandle,
    id: u32,
    prop: u32,
    value: u32,
) -> u8 {
    as_host(host).set_property(id, prop as u16, value);
    1
}

/// Set a float-valued property.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_set_property_f32(
    host: HostHandle,
    id: u32,
    prop: u32,
    value: f32,
) -> u8 {
    as_host(host).set_property_f32(id, prop as u16, value);
    1
}

// ---------------------------------------------------------------------------
// Engine
// ---------------------------------------------------------------------------

/// Emit the retained tree as opcodes into the shared ring. Returns 1 if a frame
/// was written.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_emit(host: HostHandle) -> u8 {
    if as_host(host).emit() {
        1
    } else {
        0
    }
}

// ---------------------------------------------------------------------------
// Zero-copy ring access
// ---------------------------------------------------------------------------

/// Return a pointer to the shared linear memory (header | ring | arena) so a
/// foreign driver can read opcodes in place. Never null; the header layout is
/// the stable wire layout in `spec/OPCODE.md` (frameCount/readCursor/
/// writeCursor/ringOffset/arenaOffset at their documented offsets).
#[no_mangle]
pub unsafe extern "C" fn pathland_native_ring_ptr(host: HostHandle) -> *const u8 {
    let host = as_host(host);
    host.ring_buffer().as_ptr()
}

/// Total length in bytes of the shared linear memory buffer.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_ring_len(host: HostHandle) -> usize {
    let host = as_host(host);
    host.ring_buffer().len()
}

/// Convenience: return the total buffer length of the default layout.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_layout_bytes(host: HostHandle) -> usize {
    let host = as_host(host);
    host.layout().total_bytes()
}

/// Read the root node id (u32_MAX = none).
#[no_mangle]
pub unsafe extern "C" fn pathland_native_root_id(host: HostHandle) -> u32 {
    as_host(host).root_id().unwrap_or(u32::MAX)
}

// ---------------------------------------------------------------------------
// Raw-input events (host → guest)
// ---------------------------------------------------------------------------

/// Drain pending raw-input events written by a renderer into the host → guest
/// event ring, writing each as its raw 16-byte `EVENT` opcode
/// (`[category][command][flags:u16][A:u32][B:u32][C:u32]`, little-endian) into
/// `out`. Returns the number of events written (≤ `max`).
///
/// `out` must point to at least `max * 16` writable bytes. This is the generic
/// event path: the renderer only writes `EVENT` opcodes; the host drains them
/// here, symmetric with how a driver reads frame opcodes via
/// [`pathland_native_ring_ptr`].
#[no_mangle]
pub unsafe extern "C" fn pathland_native_drain_events(
    host: HostHandle,
    out: *mut u8,
    max: u32,
) -> u32 {
    if out.is_null() || max == 0 {
        return 0;
    }
    let events = as_host(host).drain_events();
    let n = events.len().min(max as usize);
    for (i, ev) in events.into_iter().take(n).enumerate() {
        let bytes = ev.encode().to_bytes();
        // SAFETY: `out` points to ≥ max*16 writable bytes; we write ≤ max slots.
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), out.add(i * 16), 16);
    }
    n as u32
}

// ---------------------------------------------------------------------------
// Signals (reactive state owned by the engine)
// ---------------------------------------------------------------------------

fn as_signal_id(id: u32) -> SignalId {
    SignalId(id)
}

/// Create a float signal. Returns its handle (an opaque `SignalId`).
#[no_mangle]
pub unsafe extern "C" fn pathland_native_signal_create_f32(host: HostHandle, value: f32) -> u32 {
    as_host(host).create_signal(SignalValue::F32(value)).0
}

/// Create a raw u32 signal (color `0xAARRGGBB`, listener mask, …).
#[no_mangle]
pub unsafe extern "C" fn pathland_native_signal_create_u32(host: HostHandle, value: u32) -> u32 {
    as_host(host).create_signal(SignalValue::U32(value)).0
}

/// Create a boolean signal.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_signal_create_bool(host: HostHandle, value: u8) -> u32 {
    as_host(host).create_signal(SignalValue::Bool(value != 0)).0
}

/// Create an enum signal (wire value 0..n).
#[no_mangle]
pub unsafe extern "C" fn pathland_native_signal_create_enum(host: HostHandle, value: u8) -> u32 {
    as_host(host).create_signal(SignalValue::Enum(value)).0
}

/// Create a string signal from a NUL-terminated UTF-8 C string.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_signal_create_string(
    host: HostHandle,
    text: *const c_char,
) -> u32 {
    if text.is_null() {
        return u32::MAX;
    }
    let s = CStr::from_ptr(text).to_string_lossy().into_owned();
    as_host(host).create_signal(SignalValue::Str(s)).0
}

/// Set a float signal's value; re-emits only the nodes bound to it. Returns 1
/// if any opcode was written.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_signal_set_f32(
    host: HostHandle,
    id: u32,
    value: f32,
) -> u8 {
    u8::from(as_host(host).set_signal(as_signal_id(id), SignalValue::F32(value)))
}

/// Set a raw u32 signal's value.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_signal_set_u32(
    host: HostHandle,
    id: u32,
    value: u32,
) -> u8 {
    u8::from(as_host(host).set_signal(as_signal_id(id), SignalValue::U32(value)))
}

/// Set a boolean signal's value.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_signal_set_bool(
    host: HostHandle,
    id: u32,
    value: u8,
) -> u8 {
    u8::from(as_host(host).set_signal(as_signal_id(id), SignalValue::Bool(value != 0)))
}

/// Set an enum signal's value.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_signal_set_enum(
    host: HostHandle,
    id: u32,
    value: u8,
) -> u8 {
    u8::from(as_host(host).set_signal(as_signal_id(id), SignalValue::Enum(value)))
}

/// Set a string signal's value from a NUL-terminated UTF-8 C string.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_signal_set_string(
    host: HostHandle,
    id: u32,
    text: *const c_char,
) -> u8 {
    if text.is_null() {
        return 0;
    }
    let s = CStr::from_ptr(text).to_string_lossy().into_owned();
    u8::from(as_host(host).set_signal(as_signal_id(id), SignalValue::Str(s)))
}

/// Bind a node's text to a signal (the signal's string value wins over static
/// text). Returns 1 if the node exists.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_node_bind_text(
    host: HostHandle,
    node: u32,
    signal: u32,
) -> u8 {
    u8::from(as_host(host).bind_text(node, as_signal_id(signal)))
}

/// Bind a node's property to a signal. Returns 1 if the node exists.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_node_bind_property(
    host: HostHandle,
    node: u32,
    prop: u32,
    signal: u32,
) -> u8 {
    u8::from(as_host(host).bind_property(node, prop as u16, as_signal_id(signal)))
}

// ---------------------------------------------------------------------------
// Media controllers (spec/MEDIA.md)
// ---------------------------------------------------------------------------

/// Create a media controller; returns its handle (0 on failure). The controller
/// owns its transport signals — reach them with
/// [`pathland_native_media_controller_signal`] and drive them with
/// `pathland_native_signal_set_*`.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_media_controller_create(host: HostHandle) -> u32 {
    as_host(host).media_controller_create()
}

/// Set a controller's resource from a NUL-terminated UTF-8 C string. Returns 1
/// on success.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_media_controller_source(
    host: HostHandle,
    handle: u32,
    source: *const c_char,
) -> u8 {
    if source.is_null() {
        return 0;
    }
    let source = CStr::from_ptr(source).to_string_lossy().into_owned();
    u8::from(as_host(host).media_controller_source(handle, &source))
}

/// A controller's transport signal for `slot` (0 = invalid). The slot codes are
/// the [`MediaControllerSlot`] order.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_media_controller_signal(
    host: HostHandle,
    handle: u32,
    slot: u32,
) -> u32 {
    let slot = match slot {
        0 => MediaControllerSlot::Playing,
        1 => MediaControllerSlot::Position,
        2 => MediaControllerSlot::Volume,
        3 => MediaControllerSlot::Muted,
        4 => MediaControllerSlot::Loop,
        5 => MediaControllerSlot::Rate,
        6 => MediaControllerSlot::Time,
        7 => MediaControllerSlot::Duration,
        8 => MediaControllerSlot::VolumeReport,
        _ => return 0,
    };
    match as_host(host).media_controller_signal(handle, slot) {
        Some(id) => id.0,
        None => 0,
    }
}

/// Mark a node as referencing a media controller: a marker node (created as
/// `COMMENT`) becomes the controller's non-visual `MEDIA_CONTROLLER` node in
/// place; a `VIDEO` node becomes one of its surfaces. Returns 1 if the node and
/// handle exist.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_node_media_controller(
    host: HostHandle,
    node: u32,
    handle: u32,
) -> u8 {
    u8::from(as_host(host).node_media_controller(node, handle))
}

/// Bind a node's **`STRING`**-valued property to a signal (e.g. a controller's
/// `MEDIA_SOURCE`). A change re-emits only that property. Returns 1 if the node
/// exists.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_node_bind_string_property(
    host: HostHandle,
    node: u32,
    prop: u32,
    signal: u32,
) -> u8 {
    u8::from(as_host(host).bind_string_property(node, prop as u16, as_signal_id(signal)))
}

/// Bind a window/surface metadata field (`META::SET_WINDOW_METADATA`: 1 title,
/// 2 description, 3 locale) to a string signal. A change re-emits only that
/// field; renderers map it onto their own window chrome. Returns 1 for a
/// defined field.
#[no_mangle]
pub unsafe extern "C" fn pathland_native_window_metadata(
    host: HostHandle,
    field: u32,
    signal: u32,
) -> u8 {
    u8::from(as_host(host).bind_window_metadata(field as u16, as_signal_id(signal)))
}
