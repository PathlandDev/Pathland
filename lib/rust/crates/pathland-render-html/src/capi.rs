//! Flat C ABI (`pathland_html_*`) — the cross-language host surface for the
//! HTML renderer. Every function is a pure function of a self-contained PLPL
//! batch: it parses the raw bytes, renders a full document or fragment in one
//! streaming pass (no retained state), and returns a NUL-terminated C string
//! the caller owns (release with `pathland_html_free`).
//!
//! Java binds this via JNA (`com.pathland.render.html` shim); Swift/.NET bind
//! the same ABI.

use std::ffi::{CString, c_char};
use std::os::raw::c_uchar;

use pathland_core_transport::decode_frame;

use crate::HtmlRenderer;

fn render_bytes(
    batch: *const c_uchar,
    len: u32,
    root: u32,
    fragment: bool,
    debug: bool,
) -> *const c_char {
    if batch.is_null() {
        return std::ptr::null();
    }
    let bytes = unsafe { std::slice::from_raw_parts(batch, len as usize) };
    let (opcodes, strings) = match decode_frame(bytes) {
        Ok(decoded) => decoded,
        Err(_) => return std::ptr::null(),
    };
    let renderer = HtmlRenderer::new().with_debug_comments(debug);
    let html = if fragment {
        renderer.render_fragment(&opcodes, &strings, root)
    } else {
        renderer.render_document(&opcodes, &strings, root)
    };
    match CString::new(html) {
        Ok(cs) => cs.into_raw(),
        Err(_) => std::ptr::null(),
    }
}

/// Render a self-contained PLPL batch as a **full HTML document**. Returns a
/// NUL-terminated C string owned by the caller (release with `pathland_html_free`);
/// NULL when the batch is malformed.
///
/// # Safety
/// `batch` must point to `len` readable bytes; the batch is decoded
/// bounds-checked before any rendering.
#[no_mangle]
pub unsafe extern "C" fn pathland_html_render(batch: *const c_uchar, len: u32, root: u32) -> *const c_char {
    render_bytes(batch, len, root, false, false)
}

/// Render a self-contained PLPL batch as an **HTML fragment** (no `<html>`).
/// Ownership and safety as [`pathland_html_render`].
#[no_mangle]
pub unsafe extern "C" fn pathland_html_render_fragment(
    batch: *const c_uchar,
    len: u32,
    root: u32,
) -> *const c_char {
    render_bytes(batch, len, root, true, false)
}

/// Render a self-contained PLPL batch as a **full HTML document** with
/// **debug comments** on every node (a non-zero `debug` enables them — see
/// [`crate::debug`]). Ownership and safety as [`pathland_html_render`].
#[no_mangle]
pub unsafe extern "C" fn pathland_html_render_debug(
    batch: *const c_uchar,
    len: u32,
    root: u32,
    debug: u8,
) -> *const c_char {
    render_bytes(batch, len, root, false, debug != 0)
}

/// Render a self-contained PLPL batch as an **HTML fragment** with **debug
/// comments** on every node (a non-zero `debug` enables them). Ownership and
/// safety as [`pathland_html_render`].
#[no_mangle]
pub unsafe extern "C" fn pathland_html_render_fragment_debug(
    batch: *const c_uchar,
    len: u32,
    root: u32,
    debug: u8,
) -> *const c_char {
    render_bytes(batch, len, root, true, debug != 0)
}

/// Release a string returned by [`pathland_html_render`] /
/// [`pathland_html_render_fragment`]. No-op on NULL.
///
/// # Safety
/// `ptr` must be a pointer previously returned by this module (or NULL).
#[no_mangle]
pub unsafe extern "C" fn pathland_html_free(ptr: *const c_char) {
    if !ptr.is_null() {
        drop(CString::from_raw(ptr as *mut c_char));
    }
}

#[cfg(test)]
mod tests {
    use std::ffi::CStr;

    use pathland_core::{Opcode, category, component_type, property_id, style, tree};
    use pathland_core_transport::encode_frame;

    use super::*;

    #[test]
    fn capi_renders_a_full_document_and_frees() {
        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::TEXT as u32, 0));
        strings.extend_from_slice(&(2u32).to_le_bytes());
        strings.extend_from_slice(b"Hi");
        opcodes.push(Opcode::new(category::STYLE, style::SET_TEXT, 0, 1, 0, 0));
        let bytes = encode_frame(&opcodes, &strings);

        let ptr = unsafe { pathland_html_render(bytes.as_ptr(), bytes.len() as u32, 1) };
        assert!(!ptr.is_null());
        let html = unsafe { CStr::from_ptr(ptr) }.to_string_lossy().into_owned();
        assert!(html.contains("<!DOCTYPE html>"));
        assert!(html.contains("<span data-pathland-id=\"1\">Hi</span>"));
        unsafe { pathland_html_free(ptr) };

        let frag = unsafe { pathland_html_render_fragment(bytes.as_ptr(), bytes.len() as u32, 1) };
        let html = unsafe { CStr::from_ptr(frag) }.to_string_lossy().into_owned();
        assert!(!html.contains("<!DOCTYPE html>"));
        assert!(html.contains("data-pathland-id=\"1\""));
        unsafe { pathland_html_free(frag) };
    }

    #[test]
    fn capi_renders_slot_route_and_transition_attrs() {
        // A VSTACK slot carrying ROUTE (STRING) + TRANSITION (F32 enum 3 = Slide): the
        // DOM client reads data-pathland-route to mirror the URL and may animate a swap.
        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        strings.extend_from_slice(&(9u32).to_le_bytes());
        strings.extend_from_slice(b"/users/42");
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            (0x05u32 << 16) | property_id::ROUTE as u32,
            0,
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            (0x04u32 << 16) | property_id::TRANSITION as u32,
            3.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            (0x04u32 << 16) | property_id::NAV_CHROME as u32,
            1.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            (0x02u32 << 16) | property_id::NAV_DEPTH as u32,
            2,
        ));
        let bytes = encode_frame(&opcodes, &strings);

        let frag = unsafe { pathland_html_render_fragment(bytes.as_ptr(), bytes.len() as u32, 1) };
        let html = unsafe { CStr::from_ptr(frag) }.to_string_lossy().into_owned();
        assert!(html.contains("data-pathland-route=\"/users/42\""), "{html}");
        assert!(html.contains("data-pathland-transition=\"slide\""), "{html}");
        assert!(html.contains("data-pathland-nav-chrome=\"custom\""), "{html}");
        assert!(html.contains("data-pathland-depth=\"2\""), "{html}");
        unsafe { pathland_html_free(frag) };
    }

    #[test]
    fn capi_debug_render_emits_node_comments() {
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            (0x04u32 << 16) | property_id::SPACING as u32,
            4.0f32.to_bits(),
        ));
        let bytes = encode_frame(&opcodes, &[]);

        // Debug on: a per-node comment naming component + modifiers.
        let ptr = unsafe { pathland_html_render_debug(bytes.as_ptr(), bytes.len() as u32, 1, 1) };
        let html = unsafe { CStr::from_ptr(ptr) }.to_string_lossy().into_owned();
        assert!(html.contains("<!-- #1 VStack: spacing=4 -->"), "{html}");
        unsafe { pathland_html_free(ptr) };

        // Debug off is byte-identical to the plain export (no comments).
        let ptr = unsafe { pathland_html_render_debug(bytes.as_ptr(), bytes.len() as u32, 1, 0) };
        let html = unsafe { CStr::from_ptr(ptr) }.to_string_lossy().into_owned();
        assert!(!html.contains("<!--"), "{html}");
        unsafe { pathland_html_free(ptr) };
    }

    #[test]
    fn capi_rejects_a_truncated_batch() {
        let ptr = unsafe { pathland_html_render([0u8, 1, 2].as_ptr(), 3, 1) };
        assert!(ptr.is_null());
    }
}
