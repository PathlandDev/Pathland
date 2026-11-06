//! C ABI for foreign hosts (Java/JNA, Swift, …).
//!
//! `pathland_markdown_parse` returns an owned buffer containing the encoded
//! [`pathland_core::AttributedString`] arena payload (the bytes the caller
//! allocates into an arena/string section). The caller must release it with
//! `pathland_markdown_free(ptr, len)`.

use std::alloc::{alloc, dealloc, Layout};
use std::ptr;

/// Parse inline Markdown and return an owned payload buffer.
///
/// # Safety
///
/// `input` must point to `input_len` readable bytes (or be null when
/// `input_len == 0`). `out_len` must be a valid, writable `u32` pointer.
/// Returns null on invalid UTF-8 or an encoding failure; `out_len` is set to 0.
#[no_mangle]
pub unsafe extern "C" fn pathland_markdown_parse(
    input: *const u8,
    input_len: usize,
    out_len: *mut u32,
) -> *mut u8 {
    if out_len.is_null() {
        return ptr::null_mut();
    }
    unsafe { *out_len = 0 };
    if input.is_null() && input_len != 0 {
        return ptr::null_mut();
    }

    let bytes = if input_len == 0 {
        &[][..]
    } else {
        unsafe { std::slice::from_raw_parts(input, input_len) }
    };
    let Ok(text) = std::str::from_utf8(bytes) else {
        return ptr::null_mut();
    };
    let Some(value) = pathland_markdown_parser::parse(text) else {
        return ptr::null_mut();
    };
    let Ok(payload) = value.encode() else {
        return ptr::null_mut();
    };
    if payload.is_empty() {
        return ptr::null_mut();
    }

    let Ok(layout) = Layout::array::<u8>(payload.len()) else {
        return ptr::null_mut();
    };
    let buffer = unsafe { alloc(layout) };
    if buffer.is_null() {
        return ptr::null_mut();
    }
    unsafe {
        ptr::copy_nonoverlapping(payload.as_ptr(), buffer, payload.len());
        *out_len = payload.len() as u32;
    }
    buffer
}

/// Release a buffer returned by [`pathland_markdown_parse`].
///
/// # Safety
///
/// `ptr`/`len` must come from one successful `pathland_markdown_parse` call.
#[no_mangle]
pub unsafe extern "C" fn pathland_markdown_free(ptr: *mut u8, len: u32) {
    if ptr.is_null() || len == 0 {
        return;
    }
    if let Ok(layout) = Layout::array::<u8>(len as usize) {
        unsafe { dealloc(ptr, layout) };
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use pathland_core::AttributedString;

    #[test]
    fn round_trips() {
        let input = "**hi** [x](y)";
        let mut len = 0u32;
        let ptr = unsafe { pathland_markdown_parse(input.as_ptr(), input.len(), &mut len) };
        assert!(!ptr.is_null());
        let bytes = unsafe { std::slice::from_raw_parts(ptr, len as usize) };
        let value = AttributedString::decode(bytes).unwrap();
        assert_eq!(value.text(), "hi x");
        assert_eq!(value.links().len(), 1);
        unsafe { pathland_markdown_free(ptr, len) };
    }

    #[test]
    fn rejects_invalid_utf8() {
        let bytes = [0xFFu8, 0xFE];
        let mut len = 0u32;
        let ptr = unsafe { pathland_markdown_parse(bytes.as_ptr(), bytes.len(), &mut len) };
        assert!(ptr.is_null());
        assert_eq!(len, 0);
    }
}
