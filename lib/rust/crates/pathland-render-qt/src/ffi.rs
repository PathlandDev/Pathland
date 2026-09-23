//! The Rust ↔ C++ FFI boundary: command batch (Rust → C++), the Qt shell
//! entry points, and the test hooks. Mirrors `src/qt/pathland_qt.h`.
//!
//! A [`Command`] is one delta — Rust produces a batch (the engine's "only
//! changes" discipline) and the C++ layer applies it to the Qt Quick scene.

use std::ffi::{c_char, c_void};
use std::os::raw::c_int;

/// One delta command handed to the C++ Qt layer. `str_` points into the
/// renderer's `RenderTree` strings and is only valid for the duration of the
/// enclosing [`crate::renderer::QtRenderer::apply_frame`] call.
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct Command {
    pub kind: u8,
    pub a: u32,
    pub b: u32,
    pub c: u32,
    pub str_len: u32,
    pub str_: *const c_char,
}

impl Command {
    pub const CREATE_NODE: u8 = 0;
    pub const DELETE_NODE: u8 = 1;
    pub const INSERT_CHILD: u8 = 2;
    pub const REMOVE_CHILD: u8 = 3;
    pub const MOVE_CHILD: u8 = 4;
    pub const SET_TEXT: u8 = 5;
    pub const SET_PROPERTY: u8 = 6;
    pub const SET_STRING_PROPERTY: u8 = 7;
    pub const RESET_NODE: u8 = 8;
    pub const SET_SCHEME: u8 = 9;

    fn new(kind: u8, a: u32, b: u32, c: u32) -> Self {
        Self {
            kind,
            a,
            b,
            c,
            str_len: 0,
            str_: std::ptr::null(),
        }
    }

    fn with_str(kind: u8, a: u32, s: &str) -> Self {
        Self {
            kind,
            a,
            b: 0,
            c: 0,
            str_len: s.len() as u32,
            str_: s.as_ptr() as *const c_char,
        }
    }

    pub fn create_node(id: u32, component: u16) -> Self {
        Self::new(Self::CREATE_NODE, id, component as u32, 0)
    }
    pub fn delete_node(id: u32) -> Self {
        Self::new(Self::DELETE_NODE, id, 0, 0)
    }
    pub fn insert_child(parent: u32, child: u32, index: u32) -> Self {
        Self::new(Self::INSERT_CHILD, parent, child, index)
    }
    pub fn remove_child(parent: u32, child: u32) -> Self {
        Self::new(Self::REMOVE_CHILD, parent, child, 0)
    }
    pub fn move_child(parent: u32, child: u32, new_index: u32) -> Self {
        Self::new(Self::MOVE_CHILD, parent, child, new_index)
    }
    pub fn set_text(id: u32, text: &str) -> Self {
        Self::with_str(Self::SET_TEXT, id, text)
    }
    /// `b = (valueType << 16) | propertyId`, `c = raw value` — the same
    /// encoding the opcode wire uses.
    pub fn set_property(id: u32, prop: u16, value_type: u8, value: u32) -> Self {
        Self::new(Self::SET_PROPERTY, id, ((value_type as u32) << 16) | prop as u32, value)
    }
    pub fn set_string_property(id: u32, prop: u16, value: &str) -> Self {
        Self {
            kind: Self::SET_STRING_PROPERTY,
            a: id,
            b: prop as u32,
            c: 0,
            str_len: value.len() as u32,
            str_: value.as_ptr() as *const c_char,
        }
    }
    pub fn reset_node(id: u32) -> Self {
        Self::new(Self::RESET_NODE, id, 0, 0)
    }
    pub fn set_scheme(scheme: u8) -> Self {
        Self::new(Self::SET_SCHEME, scheme as u32, 0, 0)
    }
}

pub type Wake = extern "C" fn(*mut c_void);
pub type Tick = extern "C" fn(*mut c_void);

extern "C" {
    /// Own the Qt application shell; blocks until the window closes.
    pub fn pathland_qt_layer_run(
        title: *const c_char,
        width: c_int,
        height: c_int,
        wake: Wake,
        tick: Tick,
        user: *mut c_void,
    ) -> c_int;
    /// Apply a batch of delta commands to the Qt Quick scene.
    pub fn pathland_qt_layer_apply(cmds: *const Command, count: u32);
    /// Reset the whole scene (META::RESET).
    pub fn pathland_qt_layer_reset();
    /// Quit the event loop (host-initiated shutdown).
    pub fn pathland_qt_layer_quit();

    // Test hooks (init without an event loop; record what `apply` received).
    pub fn pathland_qt_layer_init(wake: Wake, tick: Tick, user: *mut c_void) -> c_int;
    pub fn pathland_qt_layer_apply_count() -> u32;
    pub fn pathland_qt_layer_last_text(out: *mut c_char, cap: u32) -> u32;
    pub fn pathland_qt_layer_shutdown();
}

/// Forward a command batch to the C++ layer.
pub fn apply(cmds: &[Command]) {
    if cmds.is_empty() {
        return;
    }
    // SAFETY: `cmds` strings point into the renderer's `RenderTree`, which is
    // not mutated during the call (same `apply_frame` borrow).
    unsafe { pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32) };
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::CONTENT_ITEM;
    use std::ffi::CStr;

    extern "C" fn noop_wake(_user: *mut c_void) {}
    extern "C" fn noop_tick(_user: *mut c_void) {}

    /// End-to-end FFI: a Rust command batch (create / insert / set_text with a
    /// string) crosses into the C++ Qt layer headless (offscreen) and comes
    /// back verified. Proves the boundary the whole renderer is built on.
    #[test]
    fn command_batch_crosses_into_cpp_offscreen() {
        std::env::set_var("QT_QPA_PLATFORM", "offscreen");
        std::env::set_var("QT_QUICK_BACKEND", "software");

        unsafe {
            assert_eq!(
                pathland_qt_layer_init(noop_wake, noop_tick, std::ptr::null_mut()),
                0,
                "Qt shell initializes offscreen"
            );

            let text = "hello";
            let cmds = [
                Command::create_node(1, 0x10), // VSTACK
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::set_text(1, text),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            assert_eq!(pathland_qt_layer_apply_count(), 3);
            let mut buf = [0i8; 64];
            let n = pathland_qt_layer_last_text(buf.as_mut_ptr(), buf.len() as u32);
            assert_eq!(n, text.len() as u32);
            assert_eq!(CStr::from_ptr(buf.as_ptr()).to_string_lossy(), text);

            pathland_qt_layer_shutdown();
        }
    }
}