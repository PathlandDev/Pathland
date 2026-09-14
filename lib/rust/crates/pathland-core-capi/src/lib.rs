//! # pathland-core-capi
//!
//! The minimal C ABI for the Pathland SPSC ring buffer + 16-byte opcode engine,
//! built as `libpathland_core` (a `cdylib`). Foreign hosts (Java FFM, Swift,
//! C#, …) construct the 16-byte opcodes themselves, allocate variable-length
//! strings into the bump arena, and push them into the shared ring **in
//! place** — the engine stays in Rust, the retained tree / diff stays in the
//! host language.
//!
//! ```c
//! void* h = pathland_core_create();
//! pathland_core_begin_frame(h);
//! uint8_t op[16] = { 0x01, 0x01, 0, 0, 1,0,0,0, 2,0,0,0, 0,0,0,0 };
//! pathland_ring_buffer_push(h, op);   // TREE::CREATE_NODE, id 1, vstack
//! pathland_core_end_frame(h);
//! // read opcodes zero-copy from pathland_core_ring_ptr(h)
//! pathland_core_destroy(h);
//! ```
//!
//! Opcode layout (little-endian): `[Category u8][Command u8][Flags u16][A u32]
//! [B u32][C u32]` — see `spec/OPCODE.md`.

use plcore::{Opcode, OPCODE_SIZE};
use pathland_core_transport::RingTransport;

/// Opaque handle to a ring host.
pub type CoreHandle = *mut std::ffi::c_void;

/// The native host: owns the shared-memory ring + arena.
struct CoreHost {
    ring: RingTransport,
}

fn as_host<'a>(handle: CoreHandle) -> &'a mut CoreHost {
    assert!(!handle.is_null(), "pathland_core: null handle");
    // SAFETY: handles come only from `pathland_core_create`.
    unsafe { &mut *(handle as *mut CoreHost) }
}

/// Read a 16-byte opcode from a foreign pointer.
fn opcode_from_ptr(opcode: *const u8) -> Option<Opcode> {
    if opcode.is_null() {
        return None;
    }
    // SAFETY: the caller guarantees `opcode` points to ≥ 16 readable bytes.
    let slice = unsafe { std::slice::from_raw_parts(opcode, OPCODE_SIZE) };
    let arr: [u8; OPCODE_SIZE] = slice.try_into().ok()?;
    Some(Opcode::from_bytes(&arr))
}

// ---------------------------------------------------------------------------
// Lifetime
// ---------------------------------------------------------------------------

/// Create a ring host over a fresh zero-copy shared ring. Returns an opaque
/// handle (non-null on success).
#[no_mangle]
pub extern "C" fn pathland_core_create() -> CoreHandle {
    let host = Box::new(CoreHost {
        ring: RingTransport::new(),
    });
    Box::into_raw(host) as CoreHandle
}

/// Destroy a ring host previously created by [`pathland_core_create`].
#[no_mangle]
pub unsafe extern "C" fn pathland_core_destroy(handle: CoreHandle) {
    if !handle.is_null() {
        // SAFETY: `handle` is the pointer returned by `pathland_core_create`.
        drop(Box::from_raw(handle as *mut CoreHost));
    }
}

// ---------------------------------------------------------------------------
// Ring (guest → host)
// ---------------------------------------------------------------------------

/// Push one raw 16-byte opcode into the shared ring at `writeCursor`, advancing
/// it. Returns 1 on success, 0 when the ring is full or `opcode` is null.
#[no_mangle]
pub unsafe extern "C" fn pathland_ring_buffer_push(handle: CoreHandle, opcode: *const u8) -> u8 {
    let Some(op) = opcode_from_ptr(opcode) else {
        return 0;
    };
    let mut guest = as_host(handle).ring.producer();
    u8::from(guest.push_opcode(op).is_ok())
}

/// Allocate `len` bytes into the bump arena, returning the absolute arena
/// offset (a `u32` reference for `STYLE::SET_TEXT` / `STRING` property values).
/// Returns `u32::MAX` when the arena is full or `bytes` is null.
#[no_mangle]
pub unsafe extern "C" fn pathland_core_arena_alloc(
    handle: CoreHandle,
    bytes: *const u8,
    len: u32,
) -> u32 {
    if bytes.is_null() {
        return u32::MAX;
    }
    // SAFETY: the caller guarantees `bytes` points to ≥ `len` readable bytes.
    let slice = unsafe { std::slice::from_raw_parts(bytes, len as usize) };
    let mut guest = as_host(handle).ring.producer();
    guest.alloc(slice).unwrap_or(u32::MAX)
}

/// Record the start of a frame (frame boundaries delimit a batch of opcodes).
#[no_mangle]
pub unsafe extern "C" fn pathland_core_begin_frame(handle: CoreHandle) {
    let mut guest = as_host(handle).ring.producer();
    guest.begin_frame();
}

/// End the current frame, publishing `frameCount`.
#[no_mangle]
pub unsafe extern "C" fn pathland_core_end_frame(handle: CoreHandle) {
    let mut guest = as_host(handle).ring.producer();
    guest.end_frame();
}

/// The monotonic frame counter published so far.
#[no_mangle]
pub unsafe extern "C" fn pathland_core_frame_count(handle: CoreHandle) -> u32 {
    let guest = as_host(handle).ring.producer();
    guest.frame_count()
}

/// Pointer to the shared linear memory (header | ring | arena) for zero-copy
/// reads. Never null.
#[no_mangle]
pub unsafe extern "C" fn pathland_core_ring_ptr(handle: CoreHandle) -> *const u8 {
    as_host(handle).ring.buffer().as_ptr()
}

/// Total byte length of the shared linear memory buffer.
#[no_mangle]
pub unsafe extern "C" fn pathland_core_ring_len(handle: CoreHandle) -> u64 {
    as_host(handle).ring.buffer().len() as u64
}

/// Borrow the underlying shared ring for a same-process consumer (e.g. the GTK
/// renderer, which pumps it in place). Returns NULL for a null handle.
///
/// The caller must not destroy the core handle until the consumer is done with
/// the ring. The pointer is only valid inside this process.
#[no_mangle]
pub unsafe extern "C" fn pathland_core_ring_mut(handle: CoreHandle) -> *mut RingTransport {
    if handle.is_null() {
        return std::ptr::null_mut();
    }
    &mut as_host(handle).ring
}

// ---------------------------------------------------------------------------
// Events (host → guest)
// ---------------------------------------------------------------------------

/// Drain pending raw `EVENT`-category opcodes from the host → guest event ring,
/// copying each 16-byte opcode into `out`. Returns the number of events
/// written (≤ `max`). `out` must point to ≥ `max * 16` writable bytes.
#[no_mangle]
pub unsafe extern "C" fn pathland_core_drain_events(
    handle: CoreHandle,
    out: *mut u8,
    max: u32,
) -> u32 {
    if out.is_null() || max == 0 {
        return 0;
    }
    let mut guest = as_host(handle).ring.producer();
    let events = guest.drain_event_opcodes();
    let n = events.len().min(max as usize);
    for (i, op) in events.into_iter().take(n).enumerate() {
        let bytes = op.to_bytes();
        // SAFETY: `out` points to ≥ max*16 writable bytes; we write ≤ max slots.
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), out.add(i * 16), 16);
    }
    n as u32
}

/// Push one raw 16-byte `EVENT`-category opcode into the host → guest event
/// ring (the renderer reporting an input). Returns 1 on success.
#[no_mangle]
pub unsafe extern "C" fn pathland_core_send_event(handle: CoreHandle, opcode: *const u8) -> u8 {
    let Some(op) = opcode_from_ptr(opcode) else {
        return 0;
    };
    let mut guest = as_host(handle).ring.producer();
    u8::from(guest.push_event_opcode(op).is_ok())
}

#[cfg(test)]
mod tests {
    use super::*;
    use plcore::{category, component_type, tree};

    #[test]
    fn create_destroy_round_trips() {
        let handle = pathland_core_create();
        assert!(!handle.is_null());
        // SAFETY: handle from create.
        unsafe { pathland_core_destroy(handle) };
    }

    #[test]
    fn push_then_read_back_via_ring_ptr() {
        // SAFETY: handle from create; all pointers valid.
        unsafe {
            let handle = pathland_core_create();

            pathland_core_begin_frame(handle);
            let op = Opcode::new(
                category::TREE,
                tree::CREATE_NODE,
                0,
                1,
                component_type::VSTACK as u32,
                0,
            );
            let op_bytes = op.to_bytes();
            assert_eq!(pathland_ring_buffer_push(handle, op_bytes.as_ptr()), 1);
            pathland_core_end_frame(handle);

            assert_eq!(pathland_core_frame_count(handle), 1);

            // Read the shared memory zero-copy and find the opcode.
            let ptr = pathland_core_ring_ptr(handle);
            let len = pathland_core_ring_len(handle);
            let buf = std::slice::from_raw_parts(ptr, len as usize);
            // header: magic at 0, ringOffset at 6, slotCount at 0x0E.
            let ring_off = u32::from_le_bytes(buf[0x06..0x0A].try_into().unwrap()) as usize;
            let slot_count = u32::from_le_bytes(buf[0x0E..0x12].try_into().unwrap()) as usize;
            let write_cur = u32::from_le_bytes(buf[0x22..0x26].try_into().unwrap()) as usize;
            let slot = (write_cur - 1) % slot_count;
            let base = ring_off + slot * 16;
            let read_back = Opcode::from_bytes(buf[base..base + 16].try_into().unwrap());
            assert_eq!(read_back, op);

            pathland_core_destroy(handle);
        }
    }

    #[test]
    fn arena_alloc_and_ring_share_memory() {
        // SAFETY: handle from create.
        unsafe {
            let handle = pathland_core_create();
            let text = b"Count: 0";
            let ref_ = pathland_core_arena_alloc(handle, text.as_ptr(), text.len() as u32);
            assert_ne!(ref_, u32::MAX);

            // Push a SET_TEXT referencing the arena ref.
            let op = Opcode::new(category::STYLE, 0x03, 0, 7, ref_, 0);
            let bytes = op.to_bytes();
            assert_eq!(pathland_ring_buffer_push(handle, bytes.as_ptr()), 1);

            let ptr = pathland_core_ring_ptr(handle);
            let len = pathland_core_ring_len(handle);
            let buf = std::slice::from_raw_parts(ptr, len as usize);
            let arena_off = u32::from_le_bytes(buf[0x12..0x16].try_into().unwrap()) as usize;
            let arena = &buf[arena_off..];
            let entry = &arena[ref_ as usize..];
            let str_len = u32::from_le_bytes(entry[0..4].try_into().unwrap()) as usize;
            assert_eq!(&entry[4..4 + str_len], text);

            pathland_core_destroy(handle);
        }
    }

    #[test]
    fn events_round_trip_through_the_ring() {
        // SAFETY: handle from create.
        unsafe {
            let handle = pathland_core_create();
            let ev = Opcode::new(category::EVENT, 0x03, 0, 4, 0, 0); // POINTER_UP
            let bytes = ev.to_bytes();
            assert_eq!(pathland_core_send_event(handle, bytes.as_ptr()), 1);

            let mut out = [0u8; 32];
            let n = pathland_core_drain_events(handle, out.as_mut_ptr(), 2);
            assert_eq!(n, 1);
            assert_eq!(Opcode::from_bytes(&out[0..16].try_into().unwrap()), ev);

            // Second drain is empty.
            assert_eq!(pathland_core_drain_events(handle, out.as_mut_ptr(), 2), 0);

            pathland_core_destroy(handle);
        }
    }
}