//! WebAssembly wrapper for the Rust HTML renderer.
//!
//! The TypeScript SSR server copies a self-contained PLPL batch into wasm
//! memory, calls [`pathland_wasm_render`], and reads the returned UTF-8 HTML.
//! The returned pointer refers to a reusable wasm-side output buffer: the caller
//! MUST copy the bytes before the next render call (which overwrites it).
//!
//! No state from the application lives here — the wrapper decodes one frame and
//! streams the HTML document/fragment, exactly like the native C ABI.

#![allow(clippy::missing_safety_doc)]

use std::cell::RefCell;
use std::slice;

use pathland_core_transport::decode_frame;
use pathland_render_html::HtmlRenderer;

thread_local! {
    static OUTPUT: RefCell<Vec<u8>> = const { RefCell::new(Vec::new()) };
}

/// Allocate `len` bytes in wasm linear memory for the caller to fill.
#[no_mangle]
pub extern "C" fn pathland_wasm_alloc(len: u32) -> *mut u8 {
    let mut buffer = Vec::<u8>::with_capacity(len as usize);
    let ptr = buffer.as_mut_ptr();
    std::mem::forget(buffer);
    ptr
}

/// Release memory returned by [`pathland_wasm_alloc`].
#[no_mangle]
pub unsafe extern "C" fn pathland_wasm_free(ptr: *mut u8, len: u32) {
    if !ptr.is_null() {
        drop(unsafe { Vec::from_raw_parts(ptr, 0, len as usize) });
    }
}

/// Render a self-contained PLPL batch. `fragment != 0` renders a fragment.
///
/// Returns a pointer into the reusable output buffer (copy it immediately) and
/// writes the byte length to `out_len`. Returns NULL for a malformed batch.
#[no_mangle]
pub unsafe extern "C" fn pathland_wasm_render(
    batch: *const u8,
    len: u32,
    root: u32,
    fragment: u8,
    out_len: *mut u32,
) -> *const u8 {
    if batch.is_null() || out_len.is_null() {
        return std::ptr::null();
    }
    let bytes = unsafe { slice::from_raw_parts(batch, len as usize) };
    let (opcodes, strings) = match decode_frame(bytes) {
        Ok(decoded) => decoded,
        Err(_) => return std::ptr::null(),
    };
    let renderer = HtmlRenderer::new();
    let html = if fragment != 0 {
        renderer.render_fragment(&opcodes, &strings, root)
    } else {
        renderer.render_document(&opcodes, &strings, root)
    };
    OUTPUT.with(|output| {
        let mut output = output.borrow_mut();
        output.clear();
        output.extend_from_slice(html.as_bytes());
        unsafe { *out_len = output.len() as u32 };
        output.as_ptr()
    })
}

#[cfg(test)]
mod tests {
    use std::ffi::CStr;

    use pathland_core::{category, component_type, parameter, tree, Opcode};
    use pathland_core_transport::encode_frame;

    use super::*;

    fn frame() -> Vec<u8> {
        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(
            category::TREE,
            tree::CREATE_NODE,
            0,
            1,
            component_type::TEXT as u32,
            0,
        ));
        strings.extend_from_slice(&(2u32).to_le_bytes());
        strings.extend_from_slice(b"Hi");
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_TEXT,
            0,
            1,
            0,
            0,
        ));
        encode_frame(&opcodes, &strings)
    }

    #[test]
    fn renders_a_document_and_fragment() {
        let batch = frame();
        let mut len = 0u32;
        let ptr =
            unsafe { pathland_wasm_render(batch.as_ptr(), batch.len() as u32, 1, 0, &mut len) };
        assert!(!ptr.is_null());
        let html = unsafe { slice::from_raw_parts(ptr, len as usize) };
        let html = std::str::from_utf8(html).unwrap();
        assert!(html.contains("<!DOCTYPE html>"));
        assert!(html.contains("data-pathland-id=\"1\""));
        assert!(html.contains("Hi"));

        let mut len = 0u32;
        let ptr =
            unsafe { pathland_wasm_render(batch.as_ptr(), batch.len() as u32, 1, 1, &mut len) };
        let html = unsafe { slice::from_raw_parts(ptr, len as usize) };
        assert!(!std::str::from_utf8(html)
            .unwrap()
            .contains("<!DOCTYPE html>"));
    }

    #[test]
    fn alloc_free_round_trips() {
        let ptr = pathland_wasm_alloc(8);
        assert!(!ptr.is_null());
        unsafe { pathland_wasm_free(ptr, 8) };
        let _ = CStr::from_bytes_with_nul(b"\0").unwrap();
    }
}
