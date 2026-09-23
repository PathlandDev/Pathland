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
pub type EventFn = extern "C" fn(*const QtEvent, *mut c_void);

/// One raw input reported by the C++ Qt layer (mirrors `PathlandQtEvent`).
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct QtEvent {
    pub kind: u8,
    pub node_id: u32,
    pub x: f32,
    pub y: f32,
    pub value: f32,
    pub str_len: u32,
    pub str_: *const c_char,
}

pub mod ev {
    pub const POINTER_UP: u8 = 1;
    pub const VALUE_CHANGED: u8 = 2;
    pub const TEXT_CHANGED: u8 = 3;
    pub const POINTER_DOWN: u8 = 4;
    pub const POINTER_MOVE: u8 = 5;
    pub const NAVIGATE_BACK: u8 = 6;
    pub const SCHEME_CHANGED: u8 = 7;
}

extern "C" {
    /// Own the Qt application shell; blocks until the window closes.
    pub fn pathland_qt_layer_run(
        title: *const c_char,
        width: c_int,
        height: c_int,
        wake: Wake,
        tick: Tick,
        event_fn: EventFn,
        user: *mut c_void,
    ) -> c_int;
    /// Apply a batch of delta commands to the Qt Quick scene.
    pub fn pathland_qt_layer_apply(cmds: *const Command, count: u32);
    /// Reset the whole scene (META::RESET).
    pub fn pathland_qt_layer_reset();
    /// Quit the event loop (host-initiated shutdown).
    #[allow(dead_code)] // part of the C ABI surface for foreign hosts
    pub fn pathland_qt_layer_quit();

    // Test hooks (init without an event loop; query the live scene).
    #[allow(dead_code)]
    pub fn pathland_qt_layer_init(
        wake: Wake,
        tick: Tick,
        event_fn: EventFn,
        user: *mut c_void,
    ) -> c_int;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_apply_count() -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_last_text(out: *mut c_char, cap: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_root_child_count() -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_widget_child_count(id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_widget_text(id: u32, out: *mut c_char, cap: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_widget_prop_text(
        id: u32,
        prop: *const c_char,
        out: *mut c_char,
        cap: u32,
    ) -> u32;
    #[allow(dead_code)] // test-only hook
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
    use crate::{renderer::QtRenderer, CONTENT_ITEM};
    use pathland_core::{init_memory, Frame, Guest, Host, MemoryLayout};
    use pathland_engine::Engine;
    use pathland_view::{
    assign_ids, button, text, vstack, Node, Slider, View, ViewExt,
};
    use pathland_core::listener;
    use pathland_view::{Component, Gauge, Menu, ProgressView, Stepper};
    use std::ffi::CStr;
    use std::sync::Mutex;

    extern "C" fn noop_wake(_user: *mut c_void) {}
    extern "C" fn noop_tick(_user: *mut c_void) {}
    extern "C" fn noop_event(_ev: *const QtEvent, _user: *mut c_void) {}

    /// QGuiApplication is process-global: serialize every Qt-touching test.
    static QT_LOCK: Mutex<()> = Mutex::new(());

    fn with_qt(f: impl FnOnce()) {
        let _guard = QT_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        std::env::set_var("QT_QPA_PLATFORM", "offscreen");
        std::env::set_var("QT_QUICK_BACKEND", "software");
        unsafe {
            assert_eq!(
                pathland_qt_layer_init(noop_wake, noop_tick, noop_event, std::ptr::null_mut()),
                0
            );
            pathland_qt_layer_reset(); // isolate test state (counters + widgets)
            f();
            pathland_qt_layer_shutdown();
        }
    }

    /// One persistent engine + ring memory, so consecutive emits are deltas.
    struct Harness {
        layout: MemoryLayout,
        mem: Vec<u8>,
        engine: Engine,
    }

    impl Harness {
        fn new() -> Self {
            let layout = MemoryLayout::default();
            let mut mem = vec![0u8; layout.total_bytes()];
            init_memory(&mut mem, &layout);
            Self {
                layout,
                mem,
                engine: Engine::new(),
            }
        }

        fn emit(&mut self, root: &Node) -> (Vec<u8>, Vec<u8>) {
            let layout = self.layout;
            {
                let mut guest = Guest::new(&mut self.mem, &layout);
                guest.begin_frame();
                self.engine.emit(root, &mut guest).unwrap();
                guest.end_frame();
            }
            let mut host = Host::new(&mut self.mem, &layout);
            let frames = host.frames();
            let Some(f) = frames.first() else {
                return (Vec::new(), Vec::new());
            };
            let mut slots = Vec::with_capacity(f.len() * 16);
            for op in f.opcodes() {
                slots.extend_from_slice(&op.to_bytes());
            }
            let arena = f.arena().to_vec();
            (slots, arena)
        }
    }

    fn read_text(id: u32) -> String {
        let mut buf = [0i8; 256];
        let n = unsafe { pathland_qt_layer_widget_text(id, buf.as_mut_ptr(), buf.len() as u32) };
        if n == 0 {
            return String::new();
        }
        unsafe { CStr::from_ptr(buf.as_ptr()) }.to_string_lossy().into_owned()
    }

    /// End-to-end FFI: a Rust command batch (create / insert / set_text with a
    /// string) crosses into the C++ Qt layer headless (offscreen) and comes
    /// back verified.
    #[test]
    fn command_batch_crosses_into_cpp_offscreen() {
        with_qt(|| unsafe {
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

            // The VSTACK widget exists as a child of the content item.
            assert_eq!(pathland_qt_layer_root_child_count(), 1);
            assert_eq!(pathland_qt_layer_widget_child_count(1), 0);
        });
    }

    /// Full pipeline: DSL -> engine -> frame -> shared decode -> delta diff ->
    /// FFI -> live QML scene. Asserts the real widgets in the offscreen scene.
    #[test]
    fn renderer_drives_live_qml_scene() {
        with_qt(|| {
            let mut view = vstack![
                text("hello"),
                button("Press"),
                Slider {
                    value: 5.0,
                    min: 0.0,
                    max: 10.0,
                }
            ]
            .spacing(8.0)
            .build();
            assign_ids(&mut view, &mut 1);

            let mut h = Harness::new();
            let mut r = QtRenderer::new();
            {
                let (slots, arena) = h.emit(&view);
                let frame = Frame::from_parts(&slots, &arena, 0, slots.len());
                r.apply_frame(&frame); // tree.apply_frame + diff + FFI forward
            }

            // Root stack is the single content child, with three children.
            assert_eq!(unsafe { pathland_qt_layer_root_child_count() }, 1);
            assert_eq!(unsafe { pathland_qt_layer_widget_child_count(1) }, 3);

            // Node 2 is the Text with the DSL content.
            assert_eq!(read_text(2), "hello");
            // Node 3 is the Button (label rides SET_TEXT).
            assert_eq!(read_text(3), "Press");
            // Node 4 is the Slider with value/min/max applied.
            assert_eq!(read_text(4), ""); // Slider has no text
            let mut buf = [0i8; 64];
            let n = unsafe {
                pathland_qt_layer_widget_prop_text(4, c"value".as_ptr(), buf.as_mut_ptr(), buf.len() as u32)
            };
            assert_eq!(unsafe { CStr::from_ptr(buf.as_ptr()) }.to_string_lossy(), "5");
            assert_eq!(n, 1);

            // Root spacing applied.
            let mut buf2 = [0i8; 64];
            unsafe {
                pathland_qt_layer_widget_prop_text(1, c"spacing".as_ptr(), buf2.as_mut_ptr(), buf2.len() as u32)
            };
            assert_eq!(unsafe { CStr::from_ptr(buf2.as_ptr()) }.to_string_lossy(), "8");

            // Delta: spacing 8 -> 16 only re-sends the root's prop.
            let mut view2 = vstack![text("hello"), button("Press")]
                .spacing(16.0)
                .build();
            assign_ids(&mut view2, &mut 1);
            let (slots, arena) = h.emit(&view2);
            let frame = Frame::from_parts(&slots, &arena, 0, slots.len());
            r.apply_frame(&frame);

            assert_eq!(unsafe { pathland_qt_layer_widget_child_count(1) }, 2);
            let mut buf3 = [0i8; 64];
            unsafe {
                pathland_qt_layer_widget_prop_text(1, c"spacing".as_ptr(), buf3.as_mut_ptr(), buf3.len() as u32)
            };
            assert_eq!(unsafe { CStr::from_ptr(buf3.as_ptr()) }.to_string_lossy(), "16");
        });
    }

    /// EVENT_LISTENERS pointer bits attach a MouseArea overlay to the node's
    /// QML item (spec/EVENTS.md: any element can emit raw inputs).
    #[test]
    fn pointer_listeners_attach_mousearea() {
        with_qt(|| {
            let mut view = vstack![
                text("listener").pointer_events(listener::POINTER_DOWN | listener::POINTER_UP)
            ]
            .build();
            assign_ids(&mut view, &mut 1);

            let mut h = Harness::new();
            let mut r = QtRenderer::new();
            {
                let (slots, arena) = h.emit(&view);
                let frame = Frame::from_parts(&slots, &arena, 0, slots.len());
                r.apply_frame(&frame);
            }

            // Root stack has one child (the Text, node 2); the Text gained a
            // MouseArea overlay child for its pointer listeners.
            assert_eq!(unsafe { pathland_qt_layer_root_child_count() }, 1);
            assert_eq!(unsafe { pathland_qt_layer_widget_child_count(1) }, 1);
            assert_eq!(unsafe { pathland_qt_layer_widget_child_count(2) }, 1);
        });
    }

    /// The Phase-B controls construct into their QML counterparts and apply
    /// their value/progress properties.
    #[test]
    fn control_widgets_construct_and_apply_props() {
        with_qt(|| {
            let mut view = vstack![
                ProgressView(Some(0.5)),
                Stepper {
                    value: 3.0,
                    min: 0.0,
                    max: 10.0,
                },
                Gauge {
                    value: 4.0,
                    min: 0.0,
                    max: 10.0,
                },
                Menu,
            ]
            .build();
            assign_ids(&mut view, &mut 1);

            let mut h = Harness::new();
            let mut r = QtRenderer::new();
            {
                let (slots, arena) = h.emit(&view);
                let frame = Frame::from_parts(&slots, &arena, 0, slots.len());
                r.apply_frame(&frame);
            }

            assert_eq!(unsafe { pathland_qt_layer_widget_child_count(1) }, 4);
            // node 2 = ProgressBar with the fraction applied.
            let mut buf = [0i8; 64];
            unsafe {
                pathland_qt_layer_widget_prop_text(2, c"value".as_ptr(), buf.as_mut_ptr(), buf.len() as u32)
            };
            assert_eq!(unsafe { CStr::from_ptr(buf.as_ptr()) }.to_string_lossy(), "0.5");
            // node 3 = SpinBox with the (int-cast) value applied.
            unsafe {
                pathland_qt_layer_widget_prop_text(3, c"value".as_ptr(), buf.as_mut_ptr(), buf.len() as u32)
            };
            assert_eq!(unsafe { CStr::from_ptr(buf.as_ptr()) }.to_string_lossy(), "3");
            // node 5 = MenuButton (has no text by default).
            assert_eq!(read_text(5), "");
        });
    }

    /// A PICKER's option children populate its ComboBox model (ordered by
    /// insertion), so `SELECTION`/activation drive the model.
    #[test]
    fn picker_builds_model_from_option_children() {
        with_qt(|| {
            let mut root = Node::from_component(&Component::VStack);
            let mut picker = Node::from_component(&Component::Picker);
            picker.children.push(text("One").build());
            picker.children.push(text("Two").build());
            root.children.push(picker);
            assign_ids(&mut root, &mut 1);

            let mut h = Harness::new();
            let mut r = QtRenderer::new();
            {
                let (slots, arena) = h.emit(&root);
                let frame = Frame::from_parts(&slots, &arena, 0, slots.len());
                r.apply_frame(&frame);
            }

            // node 2 = Picker; its ComboBox model has both options.
            let mut buf = [0i8; 64];
            unsafe {
                pathland_qt_layer_widget_prop_text(2, c"count".as_ptr(), buf.as_mut_ptr(), buf.len() as u32)
            };
            assert_eq!(unsafe { CStr::from_ptr(buf.as_ptr()) }.to_string_lossy(), "2");
        });
    }
}