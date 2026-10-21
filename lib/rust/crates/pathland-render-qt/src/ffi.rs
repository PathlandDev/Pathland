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
    pub const SET_DATE: u8 = 10;

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
        Self::new(
            Self::SET_PROPERTY,
            id,
            ((value_type as u32) << 16) | prop as u32,
            value,
        )
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
    /// A `DATE_PICKER` value: `b = days since epoch (I32)`, `c = millis of day`.
    pub fn set_date(id: u32, days: i32, millis: u32) -> Self {
        Self::new(Self::SET_DATE, id, days as u32, millis)
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
    /// KEY_DOWN/KEY_UP payload: platform key code + modifier bitmask + repeat.
    pub key_code: u16,
    pub modifiers: u8,
    pub repeat: u8,
}

pub mod ev {
    pub const POINTER_UP: u8 = 1;
    pub const VALUE_CHANGED: u8 = 2;
    pub const TEXT_CHANGED: u8 = 3;
    pub const POINTER_DOWN: u8 = 4;
    pub const POINTER_MOVE: u8 = 5;
    pub const NAVIGATE_BACK: u8 = 6;
    pub const SCHEME_CHANGED: u8 = 7;
    pub const MEDIA_PLAY_STATE: u8 = 8;
    pub const MEDIA_TIME: u8 = 9;
    pub const MEDIA_ENDED: u8 = 10;
    pub const MEDIA_VOLUME: u8 = 11;
    pub const MEDIA_DURATION: u8 = 12;
    pub const FIT_CHANGED: u8 = 13;
    pub const POINTER_ENTER: u8 = 14;
    pub const POINTER_LEAVE: u8 = 15;
    pub const KEY_DOWN: u8 = 16;
    pub const KEY_UP: u8 = 17;
    pub const FOCUS: u8 = 18;
    pub const EDITING: u8 = 19;
    pub const SUBMIT: u8 = 20;
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
    /// Set the local asset root for web-style `/_pathland/...` sources.
    #[allow(dead_code)]
    pub fn pathland_qt_layer_set_asset_root(root: *const c_char);
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
    pub fn pathland_qt_layer_scroll_content_has_child(id: u32, child_id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_nav_depth(slot: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_nav_page_fills(slot: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_widget_text(id: u32, out: *mut c_char, cap: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_widget_prop_text(
        id: u32,
        prop: *const c_char,
        out: *mut c_char,
        cap: u32,
    ) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_widget_prop_int(id: u32, prop: *const c_char) -> i32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_widget_fill(id: u32, which: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_widget_preferred(id: u32, which: u32) -> f64;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_widget_implicit_size(id: u32, which: u32) -> f64;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_widget_size(id: u32, which: u32) -> f64;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_widget_position(id: u32, which: u32) -> f64;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_top_child(parent: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_anchors_fill(id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_video_source(id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_media_source(id: u32, out: *mut c_char, cap: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_media_volume(id: u32) -> f64;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_media_muted(id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_media_loops(id: u32) -> i32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_media_rate(id: u32) -> f64;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_media_source_sets(id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_media_seeks(id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_fit_index(id: u32) -> i32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_report_fit(id: u32, width: f64);
    #[allow(dead_code)]
    pub fn pathland_qt_layer_background_color(id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_background_radius(id: u32) -> f64;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_background_border_width(id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_text_color(id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_text_pixel_size(id: u32) -> i32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_font_italic(id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_allows_hit_testing(id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_padding(id: u32, edge: *const c_char) -> f64;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_offset(id: u32, which: u32) -> f64;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_shadow_color(id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_shadow_x(id: u32) -> f64;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_render_once() -> i32;
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
    use pathland_core::{component_type, listener, property_id, value_type};
    use pathland_core::{init_memory, Frame, Guest, Host, MemoryLayout};
    use pathland_engine::Engine;
    use pathland_view::{
        assign_ids, button, hstack, spacer, text, vstack, Configurable, Fit, Node,
        PointerEvents, SizeThatFits, Slider, View, ViewExt,
    };
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

    /// Raw events reported by the C++ layer (kind, node id, value). `QtEvent`
    /// itself isn't `Send` (it holds a borrowed string pointer), so the capture
    /// keeps only the scalar payload.
    static EVENTS: Mutex<Vec<(u8, u32, f32)>> = Mutex::new(Vec::new());

    extern "C" fn capture_event(ev: *const QtEvent, _user: *mut c_void) {
        if ev.is_null() {
            return;
        }
        // SAFETY: the C++ layer passes a pointer valid for the call duration.
        let ev = unsafe { &*ev };
        EVENTS
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .push((ev.kind, ev.node_id, ev.value));
    }

    /// Like `with_qt`, but captures the raw events the C++ layer reports (e.g.
    /// `FIT_CHANGED`). The buffer starts empty for each test.
    fn with_qt_events(f: impl FnOnce()) {
        let _guard = QT_LOCK.lock().unwrap_or_else(|e| e.into_inner());
        std::env::set_var("QT_QPA_PLATFORM", "offscreen");
        std::env::set_var("QT_QUICK_BACKEND", "software");
        EVENTS.lock().unwrap_or_else(|e| e.into_inner()).clear();
        unsafe {
            assert_eq!(
                pathland_qt_layer_init(noop_wake, noop_tick, capture_event, std::ptr::null_mut()),
                0
            );
            pathland_qt_layer_reset();
            f();
            pathland_qt_layer_shutdown();
        }
    }

    /// The captured `FIT_CHANGED` events as `(node id, index)`.
    fn captured_fit_changes() -> Vec<(u32, i32)> {
        EVENTS
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .iter()
            .filter(|(kind, _, _)| *kind == ev::FIT_CHANGED)
            .map(|(_, id, value)| (*id, *value as i32))
            .collect()
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
        unsafe { CStr::from_ptr(buf.as_ptr()) }
            .to_string_lossy()
            .into_owned()
    }

    /// Read an integer-valued QML property (e.g. `lineCount`) as an `i32`.
    fn int_prop(id: u32, prop: &str) -> i32 {
        let cprop = std::ffi::CString::new(prop).unwrap();
        let mut buf = [0i8; 64];
        unsafe {
            pathland_qt_layer_widget_prop_text(
                id,
                cprop.as_ptr(),
                buf.as_mut_ptr(),
                buf.len() as u32,
            );
            CStr::from_ptr(buf.as_ptr())
                .to_string_lossy()
                .parse()
                .unwrap_or(-1)
        }
    }

    /// Read an enum/int-valued QML property (e.g. a TextField's `echoMode`).
    fn enum_prop(id: u32, prop: &str) -> i32 {
        let cprop = std::ffi::CString::new(prop).unwrap();
        unsafe { pathland_qt_layer_widget_prop_int(id, cprop.as_ptr()) }
    }

    /// Read a float-valued QML property (e.g. a shape's `radius`).
    fn float_prop(id: u32, prop: &str) -> f64 {
        str_prop(id, prop).parse().unwrap_or(f64::NAN)
    }

    /// Read a string- or URL-valued QML property (e.g. an image `source`).
    fn str_prop(id: u32, prop: &str) -> String {
        let cprop = std::ffi::CString::new(prop).unwrap();
        let mut buf = [0i8; 512];
        unsafe {
            pathland_qt_layer_widget_prop_text(
                id,
                cprop.as_ptr(),
                buf.as_mut_ptr(),
                buf.len() as u32,
            );
            CStr::from_ptr(buf.as_ptr()).to_string_lossy().into_owned()
        }
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
                Slider::with(|s| {
                    s.value(5.0).min(0.0).max(10.0);
                })
            ]
            .with(|v| {
                v.spacing(8.0);
            })
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
                pathland_qt_layer_widget_prop_text(
                    4,
                    c"value".as_ptr(),
                    buf.as_mut_ptr(),
                    buf.len() as u32,
                )
            };
            assert_eq!(
                unsafe { CStr::from_ptr(buf.as_ptr()) }.to_string_lossy(),
                "5"
            );
            assert_eq!(n, 1);

            // Root spacing applied.
            let mut buf2 = [0i8; 64];
            unsafe {
                pathland_qt_layer_widget_prop_text(
                    1,
                    c"spacing".as_ptr(),
                    buf2.as_mut_ptr(),
                    buf2.len() as u32,
                )
            };
            assert_eq!(
                unsafe { CStr::from_ptr(buf2.as_ptr()) }.to_string_lossy(),
                "8"
            );

            // Delta: spacing 8 -> 16 only re-sends the root's prop.
            let mut view2 = vstack![text("hello"), button("Press")]
                .with(|v| {
                    v.spacing(16.0);
                })
                .build();
            assign_ids(&mut view2, &mut 1);
            let (slots, arena) = h.emit(&view2);
            let frame = Frame::from_parts(&slots, &arena, 0, slots.len());
            r.apply_frame(&frame);

            assert_eq!(unsafe { pathland_qt_layer_widget_child_count(1) }, 2);
            let mut buf3 = [0i8; 64];
            unsafe {
                pathland_qt_layer_widget_prop_text(
                    1,
                    c"spacing".as_ptr(),
                    buf3.as_mut_ptr(),
                    buf3.len() as u32,
                )
            };
            assert_eq!(
                unsafe { CStr::from_ptr(buf3.as_ptr()) }.to_string_lossy(),
                "16"
            );
        });
    }

    /// Explicit sizing inside a stack: a `WIDTH=FILL` child stretches via the
    /// Layout attached property and a fixed width becomes `Layout.preferredWidth`.
    /// The cross axis HUGS by default — an `ALIGNMENT=Fill(3)` is the default hug,
    /// never a CSS stretch (spec/LAYOUT.md).
    #[test]
    fn explicit_width_inside_stack_and_cross_axis_hugs() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::HSTACK),
                Command::create_node(2, component_type::TEXT),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(1, 2, u32::MAX),
                Command::insert_child(1, 3, u32::MAX),
                Command::insert_child(0, 1, u32::MAX),
                Command::set_property(1, property_id::ALIGNMENT, value_type::F32, 3.0f32.to_bits()),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(3, property_id::WIDTH, value_type::F32, (-1.0f32).to_bits()),
                Command::set_text(2, "a"),
                Command::set_text(3, "b"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            assert_eq!(
                pathland_qt_layer_root_child_count(),
                1,
                "root fills the window"
            );
            assert_eq!(
                pathland_qt_layer_widget_child_count(1),
                2,
                "both children in the stack"
            );
            // The root HSTACK wrapper is anchored to the window content (the
            // "sliver" fix) instead of hugging its content.
            assert_eq!(
                pathland_qt_layer_anchors_fill(1),
                1,
                "root anchored to the window"
            );
            // Child 2 (fixed width 100) -> Layout.preferredWidth 100.
            assert_eq!(pathland_qt_layer_widget_preferred(2, 0), 100.0);
            // Child 3 (WIDTH FILL) -> Layout.fillWidth set.
            assert_eq!(pathland_qt_layer_widget_fill(3, 0), 1);
            // Cross axis (height) HUGS: `ALIGNMENT=Fill(3)` is the default hug,
            // never a stretch, and neither child has a HEIGHT.
            assert_eq!(pathland_qt_layer_widget_fill(2, 1), 0);
            assert_eq!(pathland_qt_layer_widget_fill(3, 1), 0);
        });
    }

    /// VStack layout: children stack top-to-bottom on the main (vertical) axis,
    /// with the configured spacing between them, and default cross-axis stretch
    /// aligns them on the left. Drives the real QML `ColumnLayout` and reads the
    /// laid-out geometry (`render_once` forces a layout pass offscreen).
    ///
    /// The stack is sized to its exact content (2 × 50 + 10) inside a `ZStack`
    /// root, so no leftover space is redistributed across the layout's rows and
    /// the spacing gap is observable directly.
    #[test]
    fn vstack_lays_out_children_vertically() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(0, 1, u32::MAX),
                Command::create_node(2, component_type::VSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::create_node(3, component_type::TEXT),
                Command::create_node(4, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::insert_child(2, 4, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 200.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 110.0f32.to_bits()),
                Command::set_property(2, property_id::SPACING, value_type::F32, 10.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 50.0f32.to_bits()),
                Command::set_property(4, property_id::HEIGHT, value_type::F32, 50.0f32.to_bits()),
                Command::set_text(3, "A"),
                Command::set_text(4, "B"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            let x3 = pathland_qt_layer_widget_position(3, 0);
            let y3 = pathland_qt_layer_widget_position(3, 1);
            let x4 = pathland_qt_layer_widget_position(4, 0);
            let y4 = pathland_qt_layer_widget_position(4, 1);
            let h3 = pathland_qt_layer_widget_size(3, 1);

            assert!(
                (h3 - 50.0).abs() < 0.5,
                "child height is the fixed main-axis size (got {h3})"
            );
            assert!(
                y4 > y3,
                "second child stacks below the first (got y3={y3}, y4={y4})"
            );
            assert!(
                (x4 - x3).abs() < 0.5,
                "default stretch aligns children on the left (x3={x3}, x4={x4})"
            );
            assert!(
                (y4 - (y3 + h3) - 10.0).abs() < 0.5,
                "vertical spacing gap is applied (got {})",
                y4 - (y3 + h3)
            );
        });
    }

    /// HStack layout: children flow left-to-right on the main (horizontal) axis,
    /// with the configured spacing between them, and default cross-axis stretch
    /// aligns them on the top. Sized to exact content (2 × 100 + 10) as above.
    #[test]
    fn hstack_lays_out_children_horizontally() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(0, 1, u32::MAX),
                Command::create_node(2, component_type::HSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::create_node(3, component_type::TEXT),
                Command::create_node(4, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::insert_child(2, 4, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 210.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(2, property_id::SPACING, value_type::F32, 10.0f32.to_bits()),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(4, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_text(3, "A"),
                Command::set_text(4, "B"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            let x3 = pathland_qt_layer_widget_position(3, 0);
            let y3 = pathland_qt_layer_widget_position(3, 1);
            let x4 = pathland_qt_layer_widget_position(4, 0);
            let y4 = pathland_qt_layer_widget_position(4, 1);
            let w3 = pathland_qt_layer_widget_size(3, 0);

            assert!(
                (w3 - 100.0).abs() < 0.5,
                "child width is the fixed main-axis size (got {w3})"
            );
            assert!(
                x4 > x3,
                "second child flows right of the first (got x3={x3}, x4={x4})"
            );
            assert!(
                (y4 - y3).abs() < 0.5,
                "default stretch aligns children on the top (y3={y3}, y4={y4})"
            );
            assert!(
                (x4 - (x3 + w3) - 10.0).abs() < 0.5,
                "horizontal spacing gap is applied (got {})",
                x4 - (x3 + w3)
            );
        });
    }

    /// A VStack hugs its content: main axis = sum of child sizes + spacing +
    /// padding, cross axis = max child + padding (spec/LAYOUT.md).
    #[test]
    fn vstack_hugs_its_content() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::VSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::PADDING, value_type::F32, 8.0f32.to_bits()),
                Command::set_property(2, property_id::SPACING, value_type::F32, 10.0f32.to_bits()),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 40.0f32.to_bits()),
                Command::create_node(4, component_type::TEXT),
                Command::insert_child(2, 4, u32::MAX),
                Command::set_property(4, property_id::WIDTH, value_type::F32, 60.0f32.to_bits()),
                Command::set_property(4, property_id::HEIGHT, value_type::F32, 30.0f32.to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            // width = max(100, 60) + 2*8; height = 40 + 30 + 10 + 2*8.
            assert!(
                (pathland_qt_layer_widget_implicit_size(2, 0) - 116.0).abs() < 0.5,
                "cross-axis hug = max child + padding (got {})",
                pathland_qt_layer_widget_implicit_size(2, 0)
            );
            assert!(
                (pathland_qt_layer_widget_implicit_size(2, 1) - 96.0).abs() < 0.5,
                "main-axis hug = sum + spacing + padding (got {})",
                pathland_qt_layer_widget_implicit_size(2, 1)
            );
        });
    }

    /// An HStack hugs its content: main axis = sum + spacing + padding, cross
    /// axis = max + padding (spec/LAYOUT.md).
    #[test]
    fn hstack_hugs_its_content() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::HSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::PADDING, value_type::F32, 8.0f32.to_bits()),
                Command::set_property(2, property_id::SPACING, value_type::F32, 10.0f32.to_bits()),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 40.0f32.to_bits()),
                Command::create_node(4, component_type::TEXT),
                Command::insert_child(2, 4, u32::MAX),
                Command::set_property(4, property_id::WIDTH, value_type::F32, 60.0f32.to_bits()),
                Command::set_property(4, property_id::HEIGHT, value_type::F32, 30.0f32.to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            // width = 100 + 60 + 10 + 2*8; height = max(40, 30) + 2*8.
            assert!(
                (pathland_qt_layer_widget_implicit_size(2, 0) - 186.0).abs() < 0.5,
                "main-axis hug = sum + spacing + padding (got {})",
                pathland_qt_layer_widget_implicit_size(2, 0)
            );
            assert!(
                (pathland_qt_layer_widget_implicit_size(2, 1) - 56.0).abs() < 0.5,
                "cross-axis hug = max child + padding (got {})",
                pathland_qt_layer_widget_implicit_size(2, 1)
            );
        });
    }

    /// A VStack's cross-axis ALIGNMENT (horizontal) positions each child in the
    /// leftover space — leading/center/trailing — and never resizes it.
    #[test]
    fn vstack_cross_axis_alignment_positions_children() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::VSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 300.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 200.0f32.to_bits()),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 50.0f32.to_bits()),
                Command::set_text(3, "chip"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            for (code, want_x) in [(0u32, 0.0), (1, 100.0), (2, 200.0)] {
                let set = [Command::set_property(
                    2,
                    property_id::ALIGNMENT,
                    value_type::F32,
                    (code as f32).to_bits(),
                )];
                pathland_qt_layer_apply(set.as_ptr(), set.len() as u32);
                assert_eq!(pathland_qt_layer_render_once(), 1);
                let x = pathland_qt_layer_widget_position(3, 0);
                assert!(
                    (x - want_x).abs() < 0.5,
                    "ALIGNMENT {code} places the child at x={want_x}, got x={x}"
                );
                // Position-only: the child keeps its own cross size.
                assert!((pathland_qt_layer_widget_size(3, 0) - 100.0).abs() < 0.5);
            }
        });
    }

    /// An HStack's cross-axis ALIGNMENT (vertical) positions each child —
    /// top/center/bottom — and never resizes it.
    #[test]
    fn hstack_cross_axis_alignment_positions_children() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::HSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 300.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 200.0f32.to_bits()),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 50.0f32.to_bits()),
                Command::set_text(3, "chip"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            for (code, want_y) in [(0u32, 0.0), (1, 75.0), (2, 150.0)] {
                let set = [Command::set_property(
                    2,
                    property_id::ALIGNMENT,
                    value_type::F32,
                    (code as f32).to_bits(),
                )];
                pathland_qt_layer_apply(set.as_ptr(), set.len() as u32);
                assert_eq!(pathland_qt_layer_render_once(), 1);
                let y = pathland_qt_layer_widget_position(3, 1);
                assert!(
                    (y - want_y).abs() < 0.5,
                    "ALIGNMENT {code} places the child at y={want_y}, got y={y}"
                );
                assert!((pathland_qt_layer_widget_size(3, 1) - 50.0).abs() < 0.5);
            }
        });
    }

    /// Two main-axis FILL children share the leftover space equally
    /// (spec/LAYOUT.md: FILL expands to available space).
    #[test]
    fn stack_main_axis_fill_shares_leftover_space() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::HSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 300.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 100.0f32.to_bits()),
                // Two greedy (fill-both) Children share the width.
                Command::create_node(3, component_type::COLOR),
                Command::insert_child(2, 3, u32::MAX),
                Command::create_node(4, component_type::COLOR),
                Command::insert_child(2, 4, u32::MAX),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            let w3 = pathland_qt_layer_widget_size(3, 0);
            let w4 = pathland_qt_layer_widget_size(4, 0);
            assert!((w3 - 150.0).abs() < 0.5, "first fill child gets half (got {w3})");
            assert!((w4 - 150.0).abs() < 0.5, "second fill child gets half (got {w4})");
        });
    }

    /// A fixed main-axis child keeps its size; a FILL sibling consumes the rest.
    #[test]
    fn stack_main_axis_fill_after_fixed_child() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::HSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 300.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 100.0f32.to_bits()),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 50.0f32.to_bits()),
                Command::set_text(3, "fixed"),
                Command::create_node(4, component_type::COLOR),
                Command::insert_child(2, 4, u32::MAX),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert!((pathland_qt_layer_widget_size(3, 0) - 100.0).abs() < 0.5, "fixed child keeps its width");
            assert!(
                (pathland_qt_layer_widget_size(4, 0) - 200.0).abs() < 0.5,
                "the fill sibling consumes the remaining width (got {})",
                pathland_qt_layer_widget_size(4, 0)
            );
        });
    }

    /// The mirror ordering (fill child first, fixed sibling second) — the layout
    /// used by the music player's wide row (filling main area + fixed sidebar).
    #[test]
    fn stack_main_axis_fill_before_fixed_child() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::HSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 300.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 100.0f32.to_bits()),
                Command::create_node(4, component_type::COLOR),
                Command::insert_child(2, 4, u32::MAX),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 50.0f32.to_bits()),
                Command::set_text(3, "fixed"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert!((pathland_qt_layer_widget_size(3, 0) - 100.0).abs() < 0.5, "fixed child keeps its width");
            assert!(
                (pathland_qt_layer_widget_size(4, 0) - 200.0).abs() < 0.5,
                "the fill sibling consumes the remaining width (got {})",
                pathland_qt_layer_widget_size(4, 0)
            );
        });
    }

    /// ZStack layout: children share one box (depth stacking) rather than being
    /// distributed along an axis. Unlike VStack/HStack, the second child does
    /// not shift — both sit at the top-leading origin with their own size
    /// (spec/PRIMITIVES.md §ZStack: default alignment is top-leading, no SPACING).
    #[test]
    fn zstack_overlaps_children_in_depth() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(0, 1, u32::MAX),
                Command::create_node(2, component_type::TEXT),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(1, 2, u32::MAX),
                Command::insert_child(1, 3, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 50.0f32.to_bits()),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 50.0f32.to_bits()),
                Command::set_text(2, "A"),
                Command::set_text(3, "B"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            // Both children are direct children of the ZStack (no inner layout).
            assert_eq!(pathland_qt_layer_widget_child_count(1), 2);

            let x2 = pathland_qt_layer_widget_position(2, 0);
            let y2 = pathland_qt_layer_widget_position(2, 1);
            let x3 = pathland_qt_layer_widget_position(3, 0);
            let y3 = pathland_qt_layer_widget_position(3, 1);

            assert!(
                (x2 - 0.0).abs() < 0.5 && (y2 - 0.0).abs() < 0.5,
                "first child at the top-leading origin (got {x2},{y2})"
            );
            assert!(
                (x3 - 0.0).abs() < 0.5 && (y3 - 0.0).abs() < 0.5,
                "second child overlaps at the origin, not shifted by an axis (got {x3},{y3})"
            );
            assert!(
                (x3 - x2).abs() < 0.5 && (y3 - y2).abs() < 0.5,
                "children share one box"
            );

            // Each child keeps its own size (not stretched or summed).
            assert!((pathland_qt_layer_widget_size(2, 0) - 100.0).abs() < 0.5);
            assert!((pathland_qt_layer_widget_size(2, 1) - 50.0).abs() < 0.5);
            assert!((pathland_qt_layer_widget_size(3, 0) - 100.0).abs() < 0.5);
            assert!((pathland_qt_layer_widget_size(3, 1) - 50.0).abs() < 0.5);
        });
    }

    /// ZStack draw order (spec/PRIMITIVES.md §ZStack): child index is the paint
    /// order (later = on top), and an explicit Z_INDEX overrides it (higher =
    /// on top; ties break by child index).
    #[test]
    fn zstack_draw_order_is_child_index_with_z_index_override() {
        with_qt(|| unsafe {
            let base = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(0, 1, u32::MAX),
                Command::create_node(2, component_type::TEXT),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(1, 2, u32::MAX),
                Command::insert_child(1, 3, u32::MAX),
                Command::set_text(2, "A"),
                Command::set_text(3, "B"),
            ];
            pathland_qt_layer_apply(base.as_ptr(), base.len() as u32);
            assert_eq!(
                pathland_qt_layer_top_child(1),
                3,
                "later child paints on top"
            );

            // A higher Z_INDEX on the first child lifts it above the second.
            let raise = [Command::set_property(
                2,
                property_id::Z_INDEX,
                value_type::F32,
                5.0f32.to_bits(),
            )];
            pathland_qt_layer_apply(raise.as_ptr(), raise.len() as u32);
            assert_eq!(
                pathland_qt_layer_top_child(1),
                2,
                "explicit Z_INDEX overrides child order"
            );
        });
    }

    /// Phase-1 component parity: ICON resolves its canonical name to a glyph,
    /// a MEDIA_CONTROLLER is layout-transparent (no visual slot), and a VIDEO
    /// surface binds to the controller's MediaPlayer (spec/ICONS.md, MEDIA.md).
    #[test]
    fn new_components_and_media_binding() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, 0x10), // VSTACK
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, 0x0B), // ICON
                Command::insert_child(1, 2, u32::MAX),
                Command::set_string_property(2, 0x1038, "play"), // ICON_NAME
                Command::create_node(4, 0x30),                   // MEDIA_CONTROLLER (non-visual)
                Command::insert_child(1, 4, u32::MAX),
                Command::create_node(3, 0x0A), // VIDEO surface
                Command::insert_child(1, 3, u32::MAX),
                Command::set_property(3, 0x103A, value_type::U32, 4), // MEDIA_CONTROLLER_ID
                Command::set_string_property(4, 0x103B, "file:///tmp/clip.mp4"), // MEDIA_SOURCE
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            // The root stack hosts the ICON + VIDEO; the controller takes no slot.
            assert_eq!(
                pathland_qt_layer_widget_child_count(1),
                2,
                "controller is layout-transparent"
            );
            assert_eq!(
                read_text(2),
                "\u{25B6}",
                "canonical 'play' name resolved to a glyph"
            );
            assert_eq!(
                pathland_qt_layer_video_source(3),
                1,
                "video surface bound to its controller"
            );
        });
    }

    /// ICON resolves the canonical `ICON_NAME` names the music demo's transport
    /// controls use to their glyphs (renderer-owned mapping, spec/ICONS.md).
    #[test]
    fn icon_glyphs_resolve_for_the_music_controls() {
        with_qt(|| unsafe {
            let names = ["volume", "skip-back", "play", "pause", "skip-forward"];
            let glyphs = ["\u{1F50A}", "\u{23EE}", "\u{25B6}", "\u{2016}", "\u{23ED}"];
            let mut cmds = vec![
                Command::create_node(1, component_type::VSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
            ];
            for (i, name) in names.iter().enumerate() {
                let id = (i as u32) + 2;
                cmds.push(Command::create_node(id, component_type::ICON));
                cmds.push(Command::insert_child(1, id, u32::MAX));
                cmds.push(Command::set_string_property(id, property_id::ICON_NAME, name));
            }
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            for (i, glyph) in glyphs.iter().enumerate() {
                let id = (i as u32) + 2;
                assert_eq!(&read_text(id), glyph, "glyph for '{}'", names[i]);
            }
        });
    }

    /// DIVIDER is an axis-aligned line: it fills the parent stack's cross axis,
    /// takes its thickness from `BORDER_WIDTH`, and its color from `COLOR`
    /// (spec/PRIMITIVES.md §Divider).
    #[test]
    fn divider_is_an_axis_line_with_color_and_thickness() {
        with_qt(|| unsafe {
            let cmds = [
                // Horizontal divider in a VStack: fills width, thickness = height.
                Command::create_node(1, component_type::VSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::DIVIDER),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::COLOR, value_type::COLOR, 0xFF11_2233),
                Command::set_property(2, property_id::BORDER_WIDTH, value_type::F32, 2.0f32.to_bits()),
                // Vertical divider in an HStack: fills height, thickness = width.
                Command::create_node(3, component_type::HSTACK),
                Command::insert_child(CONTENT_ITEM, 3, u32::MAX),
                Command::create_node(4, component_type::DIVIDER),
                Command::insert_child(3, 4, u32::MAX),
                Command::set_property(4, property_id::COLOR, value_type::COLOR, 0xFF44_5566),
                Command::set_property(4, property_id::BORDER_WIDTH, value_type::F32, 3.0f32.to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert!(
                (pathland_qt_layer_widget_size(2, 0) - pathland_qt_layer_widget_size(1, 0)).abs() < 0.5,
                "a horizontal divider fills the cross (width) axis"
            );
            assert!(
                (pathland_qt_layer_widget_size(2, 1) - 2.0).abs() < 0.5,
                "BORDER_WIDTH is the line thickness (height)"
            );
            assert_eq!(str_prop(2, "color").to_lowercase(), "#112233", "COLOR is the line color");

            assert!(
                (pathland_qt_layer_widget_size(4, 1) - pathland_qt_layer_widget_size(3, 1)).abs() < 0.5,
                "a vertical divider fills the cross (height) axis"
            );
            assert!(
                (pathland_qt_layer_widget_size(4, 0) - 3.0).abs() < 0.5,
                "BORDER_WIDTH is the line thickness (width)"
            );
            assert_eq!(str_prop(4, "color").to_lowercase(), "#445566", "COLOR is the line color");
        });
    }

    /// ZStack ALIGNMENT positions each child on both axes with the spec's 2D
    /// code (0..8), never resizing it (spec/PRIMITIVES.md §ZStack).
    #[test]
    fn zstack_alignment_positions_children_on_both_axes() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::VSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::ZSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, (-1.0f32).to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, (-1.0f32).to_bits()),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 20.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 20.0f32.to_bits()),
                Command::set_text(3, "x"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            let zw = pathland_qt_layer_widget_size(2, 0);
            let zh = pathland_qt_layer_widget_size(2, 1);
            let cw = pathland_qt_layer_widget_size(3, 0);
            let ch = pathland_qt_layer_widget_size(3, 1);
            assert!(zw > 100.0 && zh > 100.0, "the ZStack fills the window (got {zw}x{zh})");

            // (code, horizontal 0/1/2, vertical 0/1/2)
            let cases = [
                (0u32, 0u32, 0u32),
                (1, 1, 1),
                (2, 2, 2),
                (3, 1, 0),
                (4, 1, 2),
                (5, 0, 1),
                (6, 2, 1),
                (7, 2, 0),
                (8, 0, 2),
            ];
            for (code, h, v) in cases {
                let set = [Command::set_property(
                    2,
                    property_id::ALIGNMENT,
                    value_type::F32,
                    (code as f32).to_bits(),
                )];
                pathland_qt_layer_apply(set.as_ptr(), set.len() as u32);
                assert_eq!(pathland_qt_layer_render_once(), 1);

                let want_x = match h {
                    0 => 0.0,
                    1 => (zw - cw) / 2.0,
                    _ => zw - cw,
                };
                let want_y = match v {
                    0 => 0.0,
                    1 => (zh - ch) / 2.0,
                    _ => zh - ch,
                };
                let x = pathland_qt_layer_widget_position(3, 0);
                let y = pathland_qt_layer_widget_position(3, 1);
                assert!(
                    (x - want_x).abs() < 0.5 && (y - want_y).abs() < 0.5,
                    "code {code} places the child at ({want_x},{want_y}), got ({x},{y})"
                );
                // Position-only: the child keeps its own size.
                assert!((pathland_qt_layer_widget_size(3, 0) - cw).abs() < 0.5);
                assert!((pathland_qt_layer_widget_size(3, 1) - ch).abs() < 0.5);
            }
        });
    }

    /// Fill propagation inside a ZStack (the MusicPlayer `mainArea` shape): an
    /// `HStack` of `Spacer`s + content stretches to the stack width (its child
    /// content is centered), and an explicit-FILL child fills both axes
    /// (spec/PRIMITIVES.md §ZStack, spec/LAYOUT.md).
    #[test]
    fn zstack_fill_propagation_stretches_the_child() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::VSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::ZSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, (-1.0f32).to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, (-1.0f32).to_bits()),
                Command::set_property(2, property_id::ALIGNMENT, value_type::F32, 4.0f32.to_bits()), // bottom-center
                Command::create_node(3, component_type::HSTACK),
                Command::insert_child(2, 3, u32::MAX),
                Command::create_node(4, component_type::SPACER),
                Command::insert_child(3, 4, u32::MAX),
                Command::create_node(5, component_type::TEXT),
                Command::insert_child(3, 5, u32::MAX),
                Command::set_property(5, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(5, property_id::HEIGHT, value_type::F32, 20.0f32.to_bits()),
                Command::set_text(5, "bar"),
                Command::create_node(6, component_type::SPACER),
                Command::insert_child(3, 6, u32::MAX),
                Command::create_node(7, component_type::VSTACK),
                Command::insert_child(2, 7, u32::MAX),
                Command::set_property(7, property_id::WIDTH, value_type::F32, (-1.0f32).to_bits()),
                Command::set_property(7, property_id::HEIGHT, value_type::F32, (-1.0f32).to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            let zw = pathland_qt_layer_widget_size(2, 0);
            let zh = pathland_qt_layer_widget_size(2, 1);
            assert!(zw > 100.0 && zh > 100.0, "the ZStack fills the window (got {zw}x{zh})");

            // Explicit-FILL child fills the whole box.
            assert!((pathland_qt_layer_widget_size(7, 0) - zw).abs() < 0.5);
            assert!((pathland_qt_layer_widget_size(7, 1) - zh).abs() < 0.5);

            // Spacer-propagated HStack stretches across the width, hugging height.
            let hw = pathland_qt_layer_widget_size(3, 0);
            let hh = pathland_qt_layer_widget_size(3, 1);
            assert!((hw - zw).abs() < 0.5, "the HStack stretches to the stack width (got {hw})");
            assert!(hh < zh, "the HStack hugs its content height (got {hh})");
            assert!(
                (pathland_qt_layer_widget_position(3, 1) - (zh - hh)).abs() < 0.5,
                "the HStack is bottom-aligned"
            );
            // Its Spacers center the fixed-width content.
            assert!(
                (pathland_qt_layer_widget_position(5, 0) - (zw - 100.0) / 2.0).abs() < 1.0,
                "Spacers center the content (got x={})",
                pathland_qt_layer_widget_position(5, 0)
            );
        });
    }

    /// A ZStack hugs its largest child on each axis — the maximum, never the sum
    /// (spec/PRIMITIVES.md §ZStack).
    #[test]
    fn zstack_hugs_its_largest_child() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::VSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::ZSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 80.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 40.0f32.to_bits()),
                Command::create_node(4, component_type::TEXT),
                Command::insert_child(2, 4, u32::MAX),
                Command::set_property(4, property_id::WIDTH, value_type::F32, 40.0f32.to_bits()),
                Command::set_property(4, property_id::HEIGHT, value_type::F32, 60.0f32.to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            assert!(
                (pathland_qt_layer_widget_implicit_size(2, 0) - 80.0).abs() < 0.5,
                "own width = max child width (not the sum)"
            );
            assert!(
                (pathland_qt_layer_widget_implicit_size(2, 1) - 60.0).abs() < 0.5,
                "own height = max child height (not the sum)"
            );
        });
    }

    /// A Fixed ZStack box is exact; a larger child overflows and is not clipped
    /// unless CLIPS_TO_BOUNDS is set (spec/PRIMITIVES.md §ZStack).
    #[test]
    fn zstack_fixed_size_makes_an_exact_box_and_does_not_clip() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::VSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::ZSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 100.0f32.to_bits()),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 200.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 50.0f32.to_bits()),
                Command::set_text(3, "overflow"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert!((pathland_qt_layer_widget_size(2, 0) - 100.0).abs() < 0.5, "fixed box width");
            assert!((pathland_qt_layer_widget_size(2, 1) - 100.0).abs() < 0.5, "fixed box height");
            assert!(
                (pathland_qt_layer_widget_size(3, 0) - 200.0).abs() < 0.5,
                "the oversized child keeps its own width (not clipped)"
            );
            assert_eq!(enum_prop(2, "clip"), 0, "a Fixed box does not clip by default");
        });
    }

    /// Fill propagation up to the parent layout: `ZStack { Color }` fills because
    /// Color is layout-greedy (spec/PRIMITIVES.md §ZStack).
    #[test]
    fn zstack_fill_propagation_fills_a_greedy_child() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::VSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::ZSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::create_node(3, component_type::COLOR),
                Command::insert_child(2, 3, u32::MAX),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert_eq!(pathland_qt_layer_widget_fill(2, 0), 1, "the ZStack fills width");
            assert_eq!(pathland_qt_layer_widget_fill(2, 1), 1, "the ZStack fills height");
            let zw = pathland_qt_layer_widget_size(2, 0);
            let zh = pathland_qt_layer_widget_size(2, 1);
            assert!((pathland_qt_layer_widget_size(3, 0) - zw).abs() < 0.5, "the greedy child stretches");
            assert!((pathland_qt_layer_widget_size(3, 1) - zh).abs() < 0.5, "the greedy child stretches");
        });
    }

    /// In one ZStack, only a Fill-sized child stretches; a non-Fill child keeps
    /// its own size (spec/PRIMITIVES.md §ZStack).
    #[test]
    fn zstack_fill_child_stretches_and_hug_child_keeps_own_size() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::VSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::ZSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 300.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 150.0f32.to_bits()),
                Command::create_node(3, component_type::COLOR),
                Command::insert_child(2, 3, u32::MAX),
                Command::create_node(4, component_type::TEXT),
                Command::insert_child(2, 4, u32::MAX),
                Command::set_property(4, property_id::WIDTH, value_type::F32, 40.0f32.to_bits()),
                Command::set_property(4, property_id::HEIGHT, value_type::F32, 20.0f32.to_bits()),
                Command::set_text(4, "chip"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert!((pathland_qt_layer_widget_size(3, 0) - 300.0).abs() < 0.5, "the Fill child stretches");
            assert!((pathland_qt_layer_widget_size(3, 1) - 150.0).abs() < 0.5, "the Fill child stretches");
            assert!((pathland_qt_layer_widget_size(4, 0) - 40.0).abs() < 0.5, "the non-Fill child keeps its size");
            assert!((pathland_qt_layer_widget_size(4, 1) - 20.0).abs() < 0.5, "the non-Fill child keeps its size");
        });
    }

    /// A `MEDIA_CONTROLLER`'s audio transport (spec/MEDIA.md) — the path the
    /// audio-only MusicPlayer demo uses (no VIDEO surface): `MEDIA_SOURCE` lands
    /// on the `MediaPlayer`, volume/mute on its `AudioOutput`, and loop/rate on
    /// the player.
    #[test]
    fn media_controller_audio_transport() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(2, 0x30), // MEDIA_CONTROLLER (audio only)
                Command::set_string_property(2, property_id::MEDIA_SOURCE, "file:///tmp/track.mp3"),
                Command::set_property(2, property_id::MEDIA_VOLUME, value_type::F32, 0.35f32.to_bits()),
                Command::set_property(2, property_id::MEDIA_MUTED, value_type::U8, 1),
                Command::set_property(2, property_id::MEDIA_LOOP, value_type::U8, 1),
                Command::set_property(2, property_id::MEDIA_RATE, value_type::F32, 1.5f32.to_bits()),
                Command::set_property(2, property_id::PLAYBACK_STATE, value_type::F32, 1.0f32.to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            let mut buf = [0i8; 256];
            pathland_qt_layer_media_source(2, buf.as_mut_ptr(), buf.len() as u32);
            assert_eq!(
                CStr::from_ptr(buf.as_ptr()).to_string_lossy(),
                "file:///tmp/track.mp3",
                "MEDIA_SOURCE lands on the MediaPlayer"
            );
            assert!(
                (pathland_qt_layer_media_volume(2) - 0.35).abs() < 0.001,
                "MEDIA_VOLUME lands on the AudioOutput"
            );
            assert_eq!(
                pathland_qt_layer_media_muted(2),
                1,
                "MEDIA_MUTED -> AudioOutput.muted"
            );
            assert_eq!(
                pathland_qt_layer_media_loops(2),
                -1,
                "MEDIA_LOOP -> loops = -1 (infinite)"
            );
            assert!(
                (pathland_qt_layer_media_rate(2) - 1.5).abs() < 0.001,
                "MEDIA_RATE -> playbackRate"
            );
        });
    }

    /// A media controller's property re-send must not restart the stream. The
    /// diff re-sends the node's FULL property set whenever any prop changes (a
    /// volume drag churns `MEDIA_VOLUME`), so `MEDIA_SOURCE` and `MEDIA_POSITION`
    /// must be applied only when they actually change — otherwise the stream
    /// reloads/seek to the stale target and the track restarts on every volume
    /// adjustment. Mirrors `pathland-render-gtk`'s source/seek guards.
    #[test]
    fn media_transport_is_idempotent_across_full_property_resend() {
        with_qt(|| unsafe {
            let full_set = |source: &str, position: f32, volume: f32| {
                let cmds = [
                    Command::set_string_property(2, property_id::MEDIA_SOURCE, source),
                    Command::set_property(
                        2,
                        property_id::MEDIA_POSITION,
                        value_type::F32,
                        position.to_bits(),
                    ),
                    Command::set_property(
                        2,
                        property_id::MEDIA_VOLUME,
                        value_type::F32,
                        volume.to_bits(),
                    ),
                    Command::set_property(2, property_id::PLAYBACK_STATE, value_type::U32, 1),
                ];
                pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            };

            let create = [Command::create_node(2, 0x30)]; // MEDIA_CONTROLLER
            pathland_qt_layer_apply(create.as_ptr(), create.len() as u32);

            full_set("file:///tmp/a.mp3", 0.0, 0.5);
            assert_eq!(pathland_qt_layer_media_source_sets(2), 1, "the source is loaded once");
            assert_eq!(pathland_qt_layer_media_seeks(2), 1, "the initial seek command applies once");

            // What the renderer's diff does on a volume change: re-send EVERYTHING.
            full_set("file:///tmp/a.mp3", 0.0, 0.8);
            assert_eq!(
                pathland_qt_layer_media_source_sets(2),
                1,
                "an unchanged MEDIA_SOURCE must not reload the stream (no restart)"
            );
            assert_eq!(
                pathland_qt_layer_media_seeks(2),
                1,
                "an unchanged MEDIA_POSITION must not re-seek"
            );
            assert!(
                (pathland_qt_layer_media_volume(2) - 0.8).abs() < 0.001,
                "the volume itself still applies"
            );

            // A real track change / seek still applies.
            full_set("file:///tmp/b.mp3", 0.0, 0.8);
            assert_eq!(pathland_qt_layer_media_source_sets(2), 2, "a new source loads");
            full_set("file:///tmp/b.mp3", 12.0, 0.8);
            assert_eq!(pathland_qt_layer_media_seeks(2), 2, "a changed seek target seeks");
        });
    }

    /// The fit rule (spec/PRIMITIVES.md §SizeThatFits): the selected index is
    /// `max{ i : thresholds[i] <= width }` over the ascending `FIT_QUERY` table,
    /// equal thresholds pick the lower index, and the threshold-0 (or empty
    /// table) candidate is the fallback. The harness drives the measurement via
    /// the `report_fit` hook, since an offscreen layout has no real width.
    #[test]
    fn size_that_fits_derives_index_from_width() {
        with_qt(|| unsafe {
            let create = |id: u32, query: &str| {
                let cmds = [
                    Command::create_node(id, 0x17), // SIZE_THAT_FITS
                    Command::set_string_property(id, 0x1039, query), // FIT_QUERY
                ];
                pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            };

            // Ascending thresholds: 0, 320, 640.
            create(2, "0,320,640");
            pathland_qt_layer_report_fit(2, 100.0);
            assert_eq!(pathland_qt_layer_fit_index(2), 0, "below the first band -> fallback");
            pathland_qt_layer_report_fit(2, 319.99);
            assert_eq!(pathland_qt_layer_fit_index(2), 0, "just below 320 -> index 0");
            pathland_qt_layer_report_fit(2, 320.0);
            assert_eq!(pathland_qt_layer_fit_index(2), 1, "exact boundary 320 -> index 1");
            pathland_qt_layer_report_fit(2, 639.99);
            assert_eq!(pathland_qt_layer_fit_index(2), 1, "just below 640 -> index 1");
            pathland_qt_layer_report_fit(2, 640.0);
            assert_eq!(pathland_qt_layer_fit_index(2), 2, "exact boundary 640 -> index 2");
            pathland_qt_layer_report_fit(2, 1000.0);
            assert_eq!(pathland_qt_layer_fit_index(2), 2, "above the top band -> last index");

            // Equal thresholds pick the lower index (spec MUST).
            create(3, "0,640,640");
            pathland_qt_layer_report_fit(3, 640.0);
            assert_eq!(pathland_qt_layer_fit_index(3), 1, "equal thresholds -> lower index");

            // An empty table falls back to index 0.
            create(4, "");
            pathland_qt_layer_report_fit(4, 500.0);
            assert_eq!(pathland_qt_layer_fit_index(4), 0, "no thresholds -> index 0");
        });
    }

    /// A fit slot shows exactly one child (the selected candidate) and that child
    /// fills the slot's allocated box (spec/PRIMITIVES.md §SizeThatFits).
    #[test]
    fn fit_slot_shows_only_the_selected_child() {
        with_qt(|| {
            let fill = || {
                pathland_view::Frame::new(Some(f32::INFINITY), Some(f32::INFINITY), None)
            };
            let mut root = vstack![
                SizeThatFits::new(vec![
                    Fit::new(hstack![text("wide-l"), text("wide-r")], 640.0),
                    Fit::any(text("compact")),
                ])
                .with_fit(0)
                .modifiers(fill()),
            ]
            .modifiers(fill())
            .build();
            assign_ids(&mut root, &mut 1);

            let mut h = Harness::new();
            let mut r = QtRenderer::new();
            {
                let (slots, arena) = h.emit(&root);
                let frame = Frame::from_parts(&slots, &arena, 0, slots.len());
                r.apply_frame(&frame);
            }
            assert_eq!(unsafe { pathland_qt_layer_render_once() }, 1);

            let slot_w = unsafe { pathland_qt_layer_widget_size(2, 0) };
            let slot_h = unsafe { pathland_qt_layer_widget_size(2, 1) };
            assert!(
                (slot_w - 420.0).abs() < 1.0 && (slot_h - 220.0).abs() < 1.0,
                "the slot fills the window (got {slot_w}x{slot_h})"
            );

            // Only the selected (threshold-0) candidate is in the scene.
            assert_eq!(
                unsafe { pathland_qt_layer_widget_child_count(2) },
                1,
                "the slot shows exactly one candidate"
            );
            assert_eq!(read_text(3), "compact", "the threshold-0 candidate is selected at mount");

            // The selected child fills the slot.
            assert_eq!(unsafe { pathland_qt_layer_widget_position(3, 0) }, 0.0);
            assert_eq!(unsafe { pathland_qt_layer_widget_position(3, 1) }, 0.0);
            assert!(
                (unsafe { pathland_qt_layer_widget_size(3, 0) } - slot_w).abs() < 1.0,
                "the selected child fills the slot's width"
            );
            assert!(
                (unsafe { pathland_qt_layer_widget_size(3, 1) } - slot_h).abs() < 1.0,
                "the selected child fills the slot's height"
            );
        });
    }

    /// On a band crossing the app swaps the slot's child with a `TREE` delta: the
    /// old candidate leaves, the new one takes the slot and fills it, and the slot
    /// still shows exactly one child (spec/PRIMITIVES.md §SizeThatFits).
    #[test]
    fn fit_slot_swaps_the_selected_child() {
        with_qt(|| {
            let fill = || {
                pathland_view::Frame::new(Some(f32::INFINITY), Some(f32::INFINITY), None)
            };
            let build = |index: usize| {
                let mut root = vstack![
                    SizeThatFits::new(vec![
                        Fit::new(hstack![text("wide-l"), text("wide-r")], 640.0),
                        Fit::any(text("compact")),
                    ])
                    .with_fit(index)
                    .modifiers(fill()),
                ]
                .modifiers(fill())
                .build();
                assign_ids(&mut root, &mut 1);
                root
            };

            let mut h = Harness::new();
            let mut r = QtRenderer::new();
            let apply = |root: &Node, h: &mut Harness, r: &mut QtRenderer| {
                let (slots, arena) = h.emit(root);
                let frame = Frame::from_parts(&slots, &arena, 0, slots.len());
                r.apply_frame(&frame);
                assert_eq!(unsafe { pathland_qt_layer_render_once() }, 1);
            };

            // Mount the compact (threshold-0) candidate.
            let compact = build(0);
            apply(&compact, &mut h, &mut r);
            assert_eq!(unsafe { pathland_qt_layer_widget_child_count(2) }, 1);
            assert_eq!(read_text(3), "compact", "the compact candidate is shown first");

            // Swap to the wide candidate (a different subtree, `TEXT` -> `HSTACK`).
            let wide = build(1);
            apply(&wide, &mut h, &mut r);
            assert_eq!(
                unsafe { pathland_qt_layer_widget_child_count(2) },
                1,
                "the slot still shows exactly one child after the swap"
            );
            assert_eq!(read_text(4), "wide-l", "the wide candidate replaced the compact one");
            assert_eq!(read_text(5), "wide-r", "the wide candidate replaced the compact one");

            let slot_w = unsafe { pathland_qt_layer_widget_size(2, 0) };
            let slot_h = unsafe { pathland_qt_layer_widget_size(2, 1) };
            assert!(
                (unsafe { pathland_qt_layer_widget_size(3, 0) } - slot_w).abs() < 1.0,
                "the swapped-in child fills the slot's width"
            );
            assert!(
                (unsafe { pathland_qt_layer_widget_size(3, 1) } - slot_h).abs() < 1.0,
                "the swapped-in child fills the slot's height"
            );
        });
    }

    /// `FIT_CHANGED` is reported once after the first measure, then only when the
    /// derived fit index changes (spec/PRIMITIVES.md §SizeThatFits).
    #[test]
    fn fit_changed_reports_once_then_only_on_band_crossing() {
        with_qt_events(|| unsafe {
            let cmds = [
                Command::create_node(2, 0x17),                   // SIZE_THAT_FITS
                Command::set_string_property(2, 0x1039, "0,640"), // FIT_QUERY
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            // Landing FIT_QUERY measures the not-yet-laid-out slot (width 0 < 640)
            // and reports the threshold-0 selection exactly once.
            assert_eq!(
                captured_fit_changes(),
                vec![(2, 0)],
                "the first measure reports FIT_CHANGED once"
            );

            // Same band -> no re-report.
            pathland_qt_layer_report_fit(2, 100.0);
            pathland_qt_layer_report_fit(2, 200.0);
            assert_eq!(
                captured_fit_changes(),
                vec![(2, 0)],
                "re-measuring within the same band reports nothing"
            );

            // Band crossing -> one report.
            pathland_qt_layer_report_fit(2, 640.0);
            assert_eq!(
                captured_fit_changes(),
                vec![(2, 0), (2, 1)],
                "crossing the 640 threshold reports the new index once"
            );

            // Back within the band -> no re-report.
            pathland_qt_layer_report_fit(2, 700.0);
            assert_eq!(
                captured_fit_changes(),
                vec![(2, 0), (2, 1)],
                "staying in the band reports nothing further"
            );
        });
    }

    /// BACKGROUND_COLOR / BORDER_* / BORDER_RADIUS land on a container's
    /// background rectangle (CSS-like background + rounded border).
    #[test]
    fn container_background_border_and_radius() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, 0x10), // VSTACK
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::set_property(1, 0x1001, value_type::COLOR, 0xFFF2F3F7), // BACKGROUND
                Command::set_property(1, 0x1005, value_type::F32, 8.0f32.to_bits()), // RADIUS
                Command::set_property(1, 0x1003, value_type::F32, 1.0f32.to_bits()), // BORDER WIDTH
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            assert_eq!(pathland_qt_layer_background_color(1), 0xFFF2F3F7);
            assert_eq!(pathland_qt_layer_background_radius(1), 8.0);
            assert_eq!(pathland_qt_layer_background_border_width(1), 1);
        });
    }

    /// A container's COLOR inherits to descendant Text (CSS color inheritance),
    /// and a Text's own COLOR wins.
    #[test]
    fn foreground_color_inherits_to_text() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, 0x11), // HSTACK (container)
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, 0x01), // TEXT
                Command::insert_child(1, 2, u32::MAX),
                Command::set_text(2, "hi"),
                Command::set_property(1, 0x100A, value_type::COLOR, 0xFF0000FF), // container COLOR
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_text_color(2), 0xFF0000FF, "inherited");

            let own = [Command::set_property(
                2,
                0x100A,
                value_type::COLOR,
                0xFFFF0000,
            )];
            pathland_qt_layer_apply(own.as_ptr(), own.len() as u32);
            assert_eq!(pathland_qt_layer_text_color(2), 0xFFFF0000, "own wins");
        });
    }

    /// Cross axis HUGS by default (spec/LAYOUT.md — SwiftUI/Compose, not CSS
    /// `align-items:stretch`): an unsized HStack child does NOT fill the height.
    /// A Spacer still expands on the main axis, and an explicit ALIGNMENT only
    /// positions — it never stretches.
    #[test]
    fn default_cross_axis_hugs_and_spacer_expands() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, 0x11), // HSTACK
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, 0x01), // TEXT
                Command::insert_child(1, 2, u32::MAX),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(
                pathland_qt_layer_widget_fill(2, 1),
                0,
                "an unsized HStack child hugs the cross axis (height), not stretched"
            );

            let spacer = [
                Command::create_node(10, 0x10), // VSTACK
                Command::insert_child(CONTENT_ITEM, 10, u32::MAX),
                Command::create_node(11, 0x06), // SPACER
                Command::insert_child(10, 11, u32::MAX),
            ];
            pathland_qt_layer_apply(spacer.as_ptr(), spacer.len() as u32);
            assert_eq!(
                pathland_qt_layer_widget_fill(11, 1),
                1,
                "spacer expands on the main axis (height)"
            );

            // An explicit ALIGNMENT positions only; it never stretches.
            let aligned = [
                Command::create_node(20, 0x11), // HSTACK
                Command::insert_child(CONTENT_ITEM, 20, u32::MAX),
                Command::set_property(20, 0x0002, value_type::F32, 1.0f32.to_bits()), // ALIGNMENT=Center
                Command::create_node(21, 0x01),                                       // TEXT
                Command::insert_child(20, 21, u32::MAX),
            ];
            pathland_qt_layer_apply(aligned.as_ptr(), aligned.len() as u32);
            assert_eq!(
                pathland_qt_layer_widget_fill(21, 1),
                0,
                "ALIGNMENT positions only — it never stretches the cross axis"
            );
        });
    }

    /// A child stretches on the cross axis only when it is explicitly `FILL` on
    /// that axis (a `HEIGHT=FILL` in an HStack, a `WIDTH=FILL` in a VStack).
    #[test]
    fn explicit_fill_stretches_cross_axis() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::HSTACK),
                Command::insert_child(0, 1, u32::MAX),
                Command::create_node(2, component_type::TEXT),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, (-1.0f32).to_bits()),
                Command::create_node(10, component_type::VSTACK),
                Command::insert_child(0, 10, u32::MAX),
                Command::create_node(11, component_type::TEXT),
                Command::insert_child(10, 11, u32::MAX),
                Command::set_property(11, property_id::WIDTH, value_type::F32, (-1.0f32).to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            assert_eq!(
                pathland_qt_layer_widget_fill(2, 1),
                1,
                "HEIGHT=FILL stretches an HStack child vertically"
            );
            assert_eq!(
                pathland_qt_layer_widget_fill(11, 0),
                1,
                "WIDTH=FILL stretches a VStack child horizontally"
            );
        });
    }

    /// The spec's layout-greedy primitives fill their natural axis without an
    /// explicit frame: `DIVIDER` the cross axis, `COLOR` both axes, `SPACER` the
    /// main axis (spec/LAYOUT.md §Layout-greedy primitives).
    #[test]
    fn greedy_primitives_fill_by_nature() {
        with_qt(|| unsafe {
            let cmds = [
                // DIVIDER in a VStack: fills the cross (horizontal) axis only.
                Command::create_node(1, component_type::VSTACK),
                Command::insert_child(0, 1, u32::MAX),
                Command::create_node(2, component_type::DIVIDER),
                Command::insert_child(1, 2, u32::MAX),
                // COLOR in a VStack: greedy on both axes.
                Command::create_node(3, component_type::COLOR),
                Command::insert_child(1, 3, u32::MAX),
                // SPACER in an HStack: fills the main (horizontal) axis only.
                Command::create_node(10, component_type::HSTACK),
                Command::insert_child(0, 10, u32::MAX),
                Command::create_node(11, component_type::SPACER),
                Command::insert_child(10, 11, u32::MAX),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            assert_eq!(
                pathland_qt_layer_widget_fill(2, 0),
                1,
                "DIVIDER fills the cross axis (width in a VStack)"
            );
            assert_eq!(
                pathland_qt_layer_widget_fill(2, 1),
                0,
                "DIVIDER does not fill the main axis"
            );
            assert_eq!(
                pathland_qt_layer_widget_fill(3, 0),
                1,
                "COLOR fills the cross axis"
            );
            assert_eq!(
                pathland_qt_layer_widget_fill(3, 1),
                1,
                "COLOR fills the main axis"
            );
            assert_eq!(
                pathland_qt_layer_widget_fill(11, 0),
                1,
                "SPACER fills the main axis (width in an HStack)"
            );
            assert_eq!(
                pathland_qt_layer_widget_fill(11, 1),
                0,
                "SPACER does not fill the cross axis"
            );
        });
    }

    /// A composite control's body (a `Button` that gained a child) is a Hug
    /// stack wrapping the app's style content, so it propagates a FILL body up:
    /// a FILL-width menu-row label makes the whole row stretch cross-axis.
    #[test]
    fn composite_button_body_propagates_fill() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::VSTACK),
                Command::insert_child(0, 1, u32::MAX),
                Command::create_node(2, component_type::BUTTON),
                Command::insert_child(1, 2, u32::MAX),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, (-1.0f32).to_bits()),
                Command::set_text(3, "Home"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            // Sidebar stack: cross axis is horizontal, so the effectively-FILL
            // row stretches to the sidebar width.
            assert_eq!(
                pathland_qt_layer_widget_fill(2, 0),
                1,
                "menu row stretches to the sidebar width"
            );
            assert_eq!(
                pathland_qt_layer_widget_fill(3, 0),
                1,
                "the FILL-width label fills the button body"
            );
        });
    }

    /// Fill propagation + the split layout: a VStack sidebar containing a
    /// `Spacer` is effectively `FILL` on the vertical axis, so an HStack
    /// stretches it full height (not centered). Driven through the real diff
    /// (`apply_frame` emits `RESET_NODE`), which previously wiped the fill.
    #[test]
    fn fill_propagation_stretches_sidebar() {
        with_qt(|| {
            // Nodes (pre-order): 1 root HStack, 2 sidebar VStack, 3 "Menu",
            // 4 Spacer, 5 content Text.
            let mut root = hstack![
                vstack![text("Menu"), spacer()]
                    .modifiers(pathland_view::Frame::width(200.0)),
                text("content").modifiers(pathland_view::Frame::width(f32::INFINITY))
            ]
            .build();
            assign_ids(&mut root, &mut 1);

            let mut h = Harness::new();
            let mut r = QtRenderer::new();
            {
                let (slots, arena) = h.emit(&root);
                let frame = Frame::from_parts(&slots, &arena, 0, slots.len());
                r.apply_frame(&frame);
            }
            assert_eq!(unsafe { pathland_qt_layer_render_once() }, 1);

            let sx = unsafe { pathland_qt_layer_widget_position(2, 0) };
            let sy = unsafe { pathland_qt_layer_widget_position(2, 1) };
            let sh = unsafe { pathland_qt_layer_widget_size(2, 1) };
            let cx = unsafe { pathland_qt_layer_widget_position(5, 0) };

            assert_eq!(sx, 0.0, "sidebar starts at the left");
            assert_eq!(sy, 0.0, "sidebar is not vertically centered");
            assert!(
                sh > 200.0,
                "sidebar stretches to the window height (got {sh})"
            );
            assert!(
                (cx - 200.0).abs() < 0.5,
                "content sits right of the 200pt sidebar (got x={cx})"
            );
        });
    }

    /// Regression for the MusicPlayerView root shape: a FILL-framed root stack
    /// wrapping a FILL-framed `SIZE_THAT_FITS` slot must stretch to fill the
    /// window, not collapse to a small top-left box. Driven through the real
    /// diff (`apply_frame` emits `RESET_NODE`).
    #[test]
    fn fill_root_and_fit_slot_stretch_to_window() {
        with_qt(|| {
            let fill = || {
                pathland_view::Frame::new(
                    Some(f32::INFINITY),
                    Some(f32::INFINITY),
                    None,
                )
            };
            // Root VStack (FILL both) -> SizeThatFits (FILL both) -> content.
            let mut root = vstack![
                SizeThatFits::new(vec![Fit::new(text("content"), 0.0)]).modifiers(fill()),
            ]
            .modifiers(fill())
            .build();
            assign_ids(&mut root, &mut 1);

            let mut h = Harness::new();
            let mut r = QtRenderer::new();
            {
                let (slots, arena) = h.emit(&root);
                let frame = Frame::from_parts(&slots, &arena, 0, slots.len());
                r.apply_frame(&frame);
            }
            assert_eq!(unsafe { pathland_qt_layer_render_once() }, 1);

            // Root (1) anchors to and fills the window.
            assert_eq!(
                unsafe { pathland_qt_layer_anchors_fill(1) },
                1,
                "root is anchored to the window"
            );
            assert!(
                (unsafe { pathland_qt_layer_widget_size(1, 0) } - 420.0).abs() < 1.0,
                "root width fills the window (got {})",
                unsafe { pathland_qt_layer_widget_size(1, 0) }
            );
            assert!(
                (unsafe { pathland_qt_layer_widget_size(1, 1) } - 220.0).abs() < 1.0,
                "root height fills the window (got {})",
                unsafe { pathland_qt_layer_widget_size(1, 1) }
            );

            // The fit slot (2) must fill the root, not sit tiny at the top-left.
            assert_eq!(
                unsafe { pathland_qt_layer_widget_position(2, 0) },
                0.0,
                "fit slot starts at the left"
            );
            assert_eq!(
                unsafe { pathland_qt_layer_widget_position(2, 1) },
                0.0,
                "fit slot starts at the top"
            );
            assert!(
                (unsafe { pathland_qt_layer_widget_size(2, 0) } - 420.0).abs() < 1.0,
                "fit slot width fills the root (got {})",
                unsafe { pathland_qt_layer_widget_size(2, 0) }
            );
            assert!(
                (unsafe { pathland_qt_layer_widget_size(2, 1) } - 220.0).abs() < 1.0,
                "fit slot height fills the root (got {})",
                unsafe { pathland_qt_layer_widget_size(2, 1) }
            );
        });
    }

    /// TEXT_STYLE applies a base font; an explicit FONT_SIZE overrides it. A
    /// shadow lands on an offset layer behind the node.
    #[test]
    fn text_style_and_shadow() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, 0x01), // TEXT
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::set_text(1, "Title"),
                Command::set_property(1, 0x1032, value_type::F32, 1.0f32.to_bits()), // TEXT_STYLE=Title
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_text_pixel_size(1), 28, "Title base size");

            let override_size = [Command::set_property(
                1,
                0x1007,
                value_type::F32,
                12.0f32.to_bits(),
            )];
            pathland_qt_layer_apply(override_size.as_ptr(), override_size.len() as u32);
            assert_eq!(
                pathland_qt_layer_text_pixel_size(1),
                12,
                "explicit FONT_SIZE wins"
            );

            let shadow = [
                Command::create_node(2, 0x10), // VSTACK
                Command::insert_child(CONTENT_ITEM, 2, u32::MAX),
                Command::set_property(2, 0x1021, value_type::COLOR, 0x33000000), // SHADOW_COLOR
                Command::set_property(2, 0x1023, value_type::F32, 2.0f32.to_bits()), // SHADOW_X
            ];
            pathland_qt_layer_apply(shadow.as_ptr(), shadow.len() as u32);
            assert_eq!(pathland_qt_layer_shadow_color(2), 0x33000000);
            assert_eq!(pathland_qt_layer_shadow_x(2), 2.0);
        });
    }

    /// TEXT + LINE_LIMIT: the text content round-trips and `LINE_LIMIT` (U32)
    /// maps to QML `maximumLineCount`, clamping the rendered line count. A
    /// sibling with the same constrained width but no limit wraps past the
    /// limit, proving the clamp is the line limit (not the width).
    #[test]
    fn text_line_limit_clamps_line_count() {
        with_qt(|| unsafe {
            let sample = "one two three four five six seven eight nine ten";
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(0, 1, u32::MAX),
                Command::create_node(2, component_type::TEXT),
                Command::insert_child(1, 2, u32::MAX),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(1, 3, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 80.0f32.to_bits()),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 80.0f32.to_bits()),
                Command::set_property(2, property_id::LINE_LIMIT, value_type::U32, 2),
                Command::set_text(2, sample),
                Command::set_text(3, sample),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            // The text content reached the QML Text item.
            assert_eq!(read_text(2), sample);

            // LINE_LIMIT lands on maximumLineCount ...
            assert_eq!(int_prop(2, "maximumLineCount"), 2);
            // ... and actually clamps the laid-out lines.
            assert_eq!(
                int_prop(2, "lineCount"),
                2,
                "limited text is clamped to 2 lines"
            );

            // The same width without a limit still wraps past the limit.
            assert_eq!(int_prop(3, "maximumLineCount"), i32::MAX);
            assert!(
                int_prop(3, "lineCount") > 2,
                "unlimited text wraps past the limit (got {})",
                int_prop(3, "lineCount")
            );
        });
    }

    /// FONT_STYLE selects an italic font (spec/MODIFIERS.md §Text Formatting).
    #[test]
    fn font_style_italic_toggles_the_font() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::TEXT),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::set_text(1, "italic"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_font_italic(1), 0, "Normal by default");

            let italic = [Command::set_property(
                1,
                property_id::FONT_STYLE,
                value_type::F32,
                1.0f32.to_bits(),
            )];
            pathland_qt_layer_apply(italic.as_ptr(), italic.len() as u32);
            assert_eq!(pathland_qt_layer_font_italic(1), 1, "Italic=1 sets the font italic");

            let normal = [Command::set_property(
                1,
                property_id::FONT_STYLE,
                value_type::F32,
                0.0f32.to_bits(),
            )];
            pathland_qt_layer_apply(normal.as_ptr(), normal.len() as u32);
            assert_eq!(pathland_qt_layer_font_italic(1), 0, "Normal=0 clears italic");
        });
    }

    /// ALLOWS_HIT_TESTING(false) blocks input (the node and its pointer
    /// MouseArea) without hiding it (spec/MODIFIERS.md §Interaction).
    #[test]
    fn allows_hit_testing_gates_input() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::TEXT),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::set_text(1, "tap"),
                Command::set_property(
                    1,
                    property_id::EVENT_LISTENERS,
                    value_type::U32,
                    listener::POINTER_DOWN,
                ),
                Command::set_property(1, property_id::ALLOWS_HIT_TESTING, value_type::U8, 0),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(
                pathland_qt_layer_allows_hit_testing(1),
                0,
                "allowsHitTesting(false) blocks input"
            );

            let on = [Command::set_property(
                1,
                property_id::ALLOWS_HIT_TESTING,
                value_type::U8,
                1,
            )];
            pathland_qt_layer_apply(on.as_ptr(), on.len() as u32);
            assert_eq!(
                pathland_qt_layer_allows_hit_testing(1),
                1,
                "allowsHitTesting(true) restores input"
            );
        });
    }

    /// PADDING insets a stack's content (uniform margins, per-edge override)
    /// (spec/MODIFIERS.md §Layout).
    #[test]
    fn padding_insets_the_stack_content() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::VSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::TEXT),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_text(2, "x"),
                Command::set_property(1, property_id::PADDING, value_type::F32, 12.0f32.to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert!(
                (pathland_qt_layer_padding(1, c"".as_ptr()) - 12.0).abs() < 0.5,
                "uniform PADDING -> the layout's margins"
            );

            // A per-edge value overrides only that edge.
            let top = [Command::set_property(
                1,
                property_id::PADDING_TOP,
                value_type::F32,
                4.0f32.to_bits(),
            )];
            pathland_qt_layer_apply(top.as_ptr(), top.len() as u32);
            assert!(
                (pathland_qt_layer_padding(1, c"topMargin".as_ptr()) - 4.0).abs() < 0.5,
                "PADDING_TOP -> topMargin"
            );
        });
    }

    /// Per-edge padding is part of a stack's own-size hug (not just the inner
    /// inset): the wrapper's implicit size adds the individual edges.
    #[test]
    fn stack_hugs_including_per_edge_padding() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::VSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 40.0f32.to_bits()),
                Command::set_property(2, property_id::PADDING_LEFT, value_type::F32, 12.0f32.to_bits()),
                Command::set_property(2, property_id::PADDING_RIGHT, value_type::F32, 8.0f32.to_bits()),
                Command::set_property(2, property_id::PADDING_TOP, value_type::F32, 6.0f32.to_bits()),
                Command::set_property(2, property_id::PADDING_BOTTOM, value_type::F32, 4.0f32.to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert!(
                (pathland_qt_layer_widget_implicit_size(2, 0) - 120.0).abs() < 0.5,
                "hug width = child + left + right (got {})",
                pathland_qt_layer_widget_implicit_size(2, 0)
            );
            assert!(
                (pathland_qt_layer_widget_implicit_size(2, 1) - 50.0).abs() < 0.5,
                "hug height = child + top + bottom (got {})",
                pathland_qt_layer_widget_implicit_size(2, 1)
            );
        });
    }

    /// Padding on a leaf view with no inner layout is applied as margins
    /// (spec/MODIFIERS.md §Layout: ".padding works on a Text and a VStack alike").
    /// A root leaf is inset from the window by its anchors margins.
    #[test]
    fn leaf_padding_insets_a_root_view() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::TEXT),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::set_text(1, "padded"),
                Command::set_property(1, property_id::PADDING, value_type::F32, 10.0f32.to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert!((pathland_qt_layer_widget_position(1, 0) - 10.0).abs() < 0.5, "left inset");
            assert!((pathland_qt_layer_widget_position(1, 1) - 10.0).abs() < 0.5, "top inset");
            assert!((pathland_qt_layer_widget_size(1, 0) - 400.0).abs() < 0.5, "width inset by padding");
            assert!((pathland_qt_layer_widget_size(1, 1) - 200.0).abs() < 0.5, "height inset by padding");
        });
    }

    /// Padding on a leaf inside a stack is applied as `Layout` margins, which
    /// offset the leaf within its cell without changing its own size.
    #[test]
    fn leaf_padding_insets_within_a_stack() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::HSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 300.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 100.0f32.to_bits()),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 50.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 20.0f32.to_bits()),
                Command::set_text(3, "leaf"),
                Command::set_property(3, property_id::PADDING_TOP, value_type::F32, 6.0f32.to_bits()),
                Command::set_property(3, property_id::PADDING_LEFT, value_type::F32, 12.0f32.to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert!((pathland_qt_layer_widget_position(3, 0) - 12.0).abs() < 0.5, "Layout left margin");
            assert!((pathland_qt_layer_widget_position(3, 1) - 6.0).abs() < 0.5, "Layout top margin");
            assert!((pathland_qt_layer_widget_size(3, 0) - 50.0).abs() < 0.5, "the leaf keeps its own size");
            assert!((pathland_qt_layer_widget_size(3, 1) - 20.0).abs() < 0.5, "the leaf keeps its own size");
        });
    }

    /// OFFSET is a post-layout translation: it moves the node visually without
    /// changing the position the layout assigned (spec/MODIFIERS.md §Layout).
    #[test]
    fn offset_translates_without_affecting_layout() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::VSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::TEXT),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_text(2, "x"),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 20.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 20.0f32.to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);
            let x0 = pathland_qt_layer_widget_position(2, 0);
            let y0 = pathland_qt_layer_widget_position(2, 1);

            let off = [
                Command::set_property(2, property_id::OFFSET_X, value_type::F32, 15.0f32.to_bits()),
                Command::set_property(2, property_id::OFFSET_Y, value_type::F32, (-30.0f32).to_bits()),
            ];
            pathland_qt_layer_apply(off.as_ptr(), off.len() as u32);
            assert!((pathland_qt_layer_offset(2, 0) - 15.0).abs() < 0.5, "OFFSET_X translates x");
            assert!((pathland_qt_layer_offset(2, 1) + 30.0).abs() < 0.5, "OFFSET_Y translates y");
            assert!(
                (pathland_qt_layer_widget_position(2, 0) - x0).abs() < 0.5
                    && (pathland_qt_layer_widget_position(2, 1) - y0).abs() < 0.5,
                "offset does not change the layout-assigned position"
            );
        });
    }

    /// `CLIPS_TO_BOUNDS` turns on the item's clip (spec/MODIFIERS.md §Appearance).
    #[test]
    fn clips_to_bounds_sets_the_item_clip() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::TEXT),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::set_text(1, "clip"),
                Command::set_property(1, property_id::CLIPS_TO_BOUNDS, value_type::U8, 1),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(enum_prop(1, "clip"), 1, "CLIPS_TO_BOUNDS -> clip = true");

            let off = [Command::set_property(
                1,
                property_id::CLIPS_TO_BOUNDS,
                value_type::U8,
                0,
            )];
            pathland_qt_layer_apply(off.as_ptr(), off.len() as u32);
            assert_eq!(enum_prop(1, "clip"), 0, "clearing CLIPS_TO_BOUNDS turns clip off");
        });
    }

    /// IMAGE maps to a QML `Image`; `IMAGE_SOURCE` lands on its `source`, the
    /// default `CONTENT_MODE` is aspect-fit, and an explicit mode selects
    /// aspect-crop (spec/PRIMITIVES.md §Image).
    #[test]
    fn image_sets_source_and_content_mode() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::IMAGE),
                Command::insert_child(0, 1, u32::MAX),
                Command::set_string_property(1, property_id::IMAGE_SOURCE, "/tmp/image.png"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            // The source is set and the node is an Image (fillMode exists).
            assert_eq!(str_prop(1, "source"), "/tmp/image.png");
            assert_eq!(
                str_prop(1, "fillMode"),
                "PreserveAspectFit",
                "default content mode is aspect-fit"
            );

            // CONTENT_MODE >= 0.5 selects aspect-crop.
            let crop = [Command::set_property(
                1,
                property_id::CONTENT_MODE,
                value_type::F32,
                1.0f32.to_bits(),
            )];
            pathland_qt_layer_apply(crop.as_ptr(), crop.len() as u32);
            assert_eq!(
                str_prop(1, "fillMode"),
                "PreserveAspectCrop",
                "CONTENT_MODE = fill selects aspect-crop"
            );
        });
    }

    /// Web-style `/_pathland/...` image sources resolve against the host's
    /// extracted asset root (the Qt analogue of `pathland_gtk_set_asset_root`).
    #[test]
    fn image_source_resolves_against_asset_root() {
        with_qt(|| unsafe {
            pathland_qt_layer_set_asset_root(c"/tmp/pathland-assets".as_ptr());
            let cmds = [
                Command::create_node(1, component_type::IMAGE),
                Command::insert_child(0, 1, u32::MAX),
                Command::set_string_property(1, property_id::IMAGE_SOURCE, "/_pathland/img.png"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            assert_eq!(
                str_prop(1, "source"),
                "file:///tmp/pathland-assets/img.png",
                "asset-root sources resolve to the extracted directory"
            );

            // The asset root is process-global state: restore it for other tests.
            pathland_qt_layer_set_asset_root(std::ptr::null());
        });
    }

    /// A Button with children renders in composite mode: the style body (child)
    /// replaces the native chrome and lives in the composite layout.
    #[test]
    fn button_with_child_renders_composite() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, 0x20), // BUTTON
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, 0x01), // TEXT (the ButtonStyle body)
                Command::insert_child(1, 2, u32::MAX),
                Command::set_text(2, "Home"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_widget_child_count(1), 1);
            assert_eq!(read_text(2), "Home");
        });
    }

    /// A BUTTON whose body is an ICON (the music demo's transport controls) also
    /// uses composite override mode: the icon becomes the button's content.
    #[test]
    fn button_with_icon_child_renders_composite() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::BUTTON),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::ICON),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_string_property(2, property_id::ICON_NAME, "play"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(
                pathland_qt_layer_widget_child_count(1),
                1,
                "the icon replaces the button's native label"
            );
            assert_eq!(read_text(2), "\u{25B6}", "the icon glyph is the composite body");
        });
    }

    /// GRID_ROW cells are flattened into the parent Grid (spec/PRIMITIVES.md), so
    /// columns align across rows and the row itself takes no cell.
    #[test]
    fn grid_row_flattens_into_the_grid() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, 0x13), // GRID
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, 0x1D), // GRID_ROW
                Command::insert_child(1, 2, u32::MAX),
                Command::create_node(3, 0x01), // cell a
                Command::insert_child(2, 3, u32::MAX),
                Command::create_node(4, 0x01), // cell b
                Command::insert_child(2, 4, u32::MAX),
                Command::create_node(5, 0x01), // bare cell
                Command::insert_child(1, 5, u32::MAX),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            // The grid holds the two row cells + the bare cell; no row item.
            assert_eq!(pathland_qt_layer_widget_child_count(1), 3);
        });
    }

    /// DATE_PICKER formats a SET_DATE value as an ISO date (and time when set).
    #[test]
    fn date_picker_formats_set_date() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, 0x27), // DATE_PICKER
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::set_date(1, 19_723, 0), // 2024-01-01
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(read_text(1), "2024-01-01");

            let with_time = [Command::set_date(1, 19_723, 3_600_000)];
            pathland_qt_layer_apply(with_time.as_ptr(), with_time.len() as u32);
            assert_eq!(read_text(1), "2024-01-01 01:00");
        });
    }

    /// TEXT_FIELD renders its value, `PROMPT` becomes the placeholder, and
    /// `IS_SECURE` masks the input (`echoMode` = Password), per
    /// spec/PRIMITIVES.md §TextField / §SecureField.
    #[test]
    fn text_field_prompt_secure_and_value() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, 0x21),                      // TEXT_FIELD
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::set_string_property(1, 0x200B, "Enter a name"), // PROMPT
                Command::set_text(1, "Ada"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            assert_eq!(str_prop(1, "placeholderText"), "Enter a name", "PROMPT -> placeholder");
            assert_eq!(read_text(1), "Ada", "the field value rides SET_TEXT");
            assert_eq!(enum_prop(1, "echoMode"), 0, "a normal field echoes the value");

            // IS_SECURE masks the input (`TextInput.Password` = 1).
            let secure = [Command::set_property(
                1,
                property_id::IS_SECURE,
                value_type::U8,
                1,
            )];
            pathland_qt_layer_apply(secure.as_ptr(), secure.len() as u32);
            assert_eq!(enum_prop(1, "echoMode"), 1, "a secure field masks its value");
        });
    }

    /// SHAPE_KIND selects the geometry (spec/PRIMITIVES.md §Shape): Circle,
    /// Capsule, and Ellipse round to `min(w,h)/2`, a Rectangle is square-cornered,
    /// and a RoundedRectangle keeps its BORDER_RADIUS.
    #[test]
    fn shape_kind_sets_the_geometry() {
        with_qt(|| unsafe {
            let kind = |id: u32, k: f32| {
                Command::set_property(id, property_id::SHAPE_KIND, value_type::F32, k.to_bits())
            };
            let shape = |id: u32| {
                [
                    Command::create_node(id, 0x04), // SHAPE
                    Command::insert_child(1, id, u32::MAX),
                    Command::set_property(id, property_id::WIDTH, value_type::F32, 100.0f32.to_bits()),
                    Command::set_property(id, property_id::HEIGHT, value_type::F32, 50.0f32.to_bits()),
                ]
            };
            let mut cmds = vec![
                Command::create_node(1, component_type::VSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
            ];
            for id in 2..=6 {
                cmds.extend(shape(id));
            }
            // Only the RoundedRectangle carries an explicit corner radius.
            cmds.push(Command::set_property(
                5,
                property_id::BORDER_RADIUS,
                value_type::F32,
                8.0f32.to_bits(),
            ));
            cmds.push(kind(2, 0.0)); // Circle
            cmds.push(kind(3, 3.0)); // Capsule
            cmds.push(kind(4, 1.0)); // Rectangle
            cmds.push(kind(5, 2.0)); // RoundedRectangle
            cmds.push(kind(6, 4.0)); // Ellipse
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert!((float_prop(2, "radius") - 25.0).abs() < 0.5, "Circle radius = min(w,h)/2");
            assert!((float_prop(3, "radius") - 25.0).abs() < 0.5, "Capsule radius = min(w,h)/2");
            assert_eq!(float_prop(4, "radius"), 0.0, "Rectangle has square corners");
            assert!((float_prop(5, "radius") - 8.0).abs() < 0.5, "RoundedRectangle keeps BORDER_RADIUS");
            assert!((float_prop(6, "radius") - 25.0).abs() < 0.5, "Ellipse radius (approximation)");
        });
    }

    /// SCROLLVIEW is layout-greedy on both axes (fills the space it is given)
    /// and carries a single content subtree attached to its contentItem
    /// (spec/PRIMITIVES.md §ScrollView).
    #[test]
    fn scroll_view_fills_and_holds_content() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::SCROLLVIEW),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::VSTACK),
                Command::insert_child(1, 2, u32::MAX),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert_eq!(
                pathland_qt_layer_anchors_fill(1),
                1,
                "the scroll view fills the window (greedy on both axes)"
            );
            assert!(
                (pathland_qt_layer_widget_size(1, 0) - 420.0).abs() < 1.0,
                "the viewport fills the window width"
            );
            assert!(
                (pathland_qt_layer_widget_size(1, 1) - 220.0).abs() < 1.0,
                "the viewport fills the window height"
            );
            assert_eq!(
                pathland_qt_layer_scroll_content_has_child(1, 2),
                1,
                "the content subtree attaches inside the scroll contentItem"
            );
        });
    }

    /// A default (vertical) SCROLLVIEW is greedy on its **scroll axis** (height)
    /// only; on the cross axis (width) it hugs its content — it does not fill
    /// unless the content does (spec/PRIMITIVES.md §ScrollView).
    #[test]
    fn scroll_view_fills_only_its_scroll_axis() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::VSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 300.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 200.0f32.to_bits()),
                Command::create_node(3, component_type::SCROLLVIEW),
                Command::insert_child(2, 3, u32::MAX),
                // A hug (fixed-width) content: the cross axis shrinks to it.
                Command::create_node(4, component_type::TEXT),
                Command::insert_child(3, 4, u32::MAX),
                Command::set_property(4, property_id::WIDTH, value_type::F32, 50.0f32.to_bits()),
                Command::set_property(4, property_id::HEIGHT, value_type::F32, 20.0f32.to_bits()),
                Command::set_text(4, "short"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert_eq!(pathland_qt_layer_widget_fill(3, 1), 1, "the scroll axis (height) is greedy");
            assert!(
                (pathland_qt_layer_widget_size(3, 1) - 200.0).abs() < 1.0,
                "the scroll axis fills the stack height"
            );
            assert_eq!(
                pathland_qt_layer_widget_fill(3, 0),
                0,
                "the cross axis hugs its content (no implicit width greed)"
            );
            let w = pathland_qt_layer_widget_size(3, 0);
            assert!(w > 0.0 && w < 300.0, "the viewport shrinks to its content width (got {w})");
        });
    }

    /// A `FILL`-width child of the content stack propagates `Fill` up, so the
    /// viewport takes the full cross size and the content fills it
    /// (spec/PRIMITIVES.md §ScrollView — full-width rows).
    #[test]
    fn scroll_view_fill_content_expands_cross_axis() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::VSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 300.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 200.0f32.to_bits()),
                Command::create_node(3, component_type::SCROLLVIEW),
                Command::insert_child(2, 3, u32::MAX),
                Command::create_node(4, component_type::VSTACK),
                Command::insert_child(3, 4, u32::MAX),
                Command::create_node(5, component_type::TEXT),
                Command::insert_child(4, 5, u32::MAX),
                Command::set_property(5, property_id::WIDTH, value_type::F32, (-1.0f32).to_bits()), // FILL
                Command::set_property(5, property_id::HEIGHT, value_type::F32, 20.0f32.to_bits()),
                Command::set_text(5, "row"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert_eq!(
                pathland_qt_layer_widget_fill(3, 0),
                1,
                "a FILL-width content child makes the viewport cross-fill"
            );
            assert!(
                (pathland_qt_layer_widget_size(3, 0) - 300.0).abs() < 1.0,
                "the viewport takes the full cross size"
            );
            assert!(
                (pathland_qt_layer_widget_size(4, 0) - 300.0).abs() < 1.0,
                "the content is bounded to the viewport on the cross axis"
            );
            assert!(
                (pathland_qt_layer_widget_size(5, 0) - 300.0).abs() < 1.0,
                "the FILL row expands across the content (got {})",
                pathland_qt_layer_widget_size(5, 0)
            );
        });
    }

    /// `SCROLL_AXIS=Horizontal` flips greed: the viewport is greedy on width and
    /// hugs its content on the cross (vertical) axis.
    #[test]
    fn scroll_view_horizontal_axis_is_greedy_on_horizontal() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::VSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 300.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 200.0f32.to_bits()),
                Command::create_node(3, component_type::SCROLLVIEW),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::SCROLL_AXIS, value_type::F32, 1.0f32.to_bits()),
                Command::create_node(4, component_type::TEXT),
                Command::insert_child(3, 4, u32::MAX),
                Command::set_property(4, property_id::WIDTH, value_type::F32, 50.0f32.to_bits()),
                Command::set_property(4, property_id::HEIGHT, value_type::F32, 20.0f32.to_bits()),
                Command::set_text(4, "wide"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert_eq!(
                pathland_qt_layer_widget_fill(3, 0),
                1,
                "a horizontal scroll axis is greedy on width"
            );
            assert!(
                (pathland_qt_layer_widget_size(3, 0) - 300.0).abs() < 1.0,
                "width fills the stack"
            );
            assert_eq!(
                pathland_qt_layer_widget_fill(3, 1),
                0,
                "the cross (vertical) axis hugs its content"
            );
            let h = pathland_qt_layer_widget_size(3, 1);
            assert!(h > 0.0 && h < 200.0, "the viewport shrinks to its content height (got {h})");
        });
    }

    /// An explicit WIDTH/HEIGHT overrides layout greed: a fixed axis is exact and
    /// the other axis keeps filling (spec/PRIMITIVES.md §ScrollView).
    #[test]
    fn scroll_view_fixed_size_overrides_greed() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::VSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 300.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 200.0f32.to_bits()),
                Command::create_node(3, component_type::SCROLLVIEW),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 120.0f32.to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert_eq!(pathland_qt_layer_widget_fill(3, 0), 0, "fixed width overrides greed");
            assert!((pathland_qt_layer_widget_size(3, 0) - 120.0).abs() < 1.0, "fixed width");
            assert_eq!(pathland_qt_layer_widget_fill(3, 1), 1, "height still greedy");
            assert!((pathland_qt_layer_widget_size(3, 1) - 200.0).abs() < 1.0, "height fills");

            // Fixing both axes makes an exact box (no greed left).
            let both = [Command::set_property(
                3,
                property_id::HEIGHT,
                value_type::F32,
                90.0f32.to_bits(),
            )];
            pathland_qt_layer_apply(both.as_ptr(), both.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);
            assert_eq!(pathland_qt_layer_widget_fill(3, 1), 0, "fixed height overrides greed");
            assert!((pathland_qt_layer_widget_size(3, 0) - 120.0).abs() < 1.0);
            assert!((pathland_qt_layer_widget_size(3, 1) - 90.0).abs() < 1.0);
        });
    }

    /// A non-greedy (fixed) SCROLLVIEW participates in its parent stack's
    /// cross-axis ALIGNMENT (spec/MODIFIERS.md §Layout).
    #[test]
    fn scroll_view_cross_axis_alignment() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::VSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 300.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 200.0f32.to_bits()),
                Command::create_node(3, component_type::SCROLLVIEW),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 120.0f32.to_bits()),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            for (code, want_x) in [(0u32, 0.0), (1, 90.0), (2, 180.0)] {
                let set = [Command::set_property(
                    2,
                    property_id::ALIGNMENT,
                    value_type::F32,
                    (code as f32).to_bits(),
                )];
                pathland_qt_layer_apply(set.as_ptr(), set.len() as u32);
                assert_eq!(pathland_qt_layer_render_once(), 1);
                let x = pathland_qt_layer_widget_position(3, 0);
                assert!(
                    (x - want_x).abs() < 0.5,
                    "ALIGNMENT {code} places the scroll view at x={want_x}, got x={x}"
                );
                assert!((pathland_qt_layer_widget_size(3, 0) - 120.0).abs() < 0.5, "size unchanged");
            }
        });
    }

    /// The content is measured with an unbounded proposal: it keeps its own size
    /// (not stretched to the viewport) and may grow beyond it
    /// (spec/PRIMITIVES.md §ScrollView — "that is what scrolls").
    #[test]
    fn scroll_view_content_is_measured_unbounded() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::SCROLLVIEW),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 300.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 150.0f32.to_bits()),
                Command::create_node(3, component_type::TEXT),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, 50.0f32.to_bits()),
                Command::set_property(3, property_id::HEIGHT, value_type::F32, 50.0f32.to_bits()),
                Command::set_text(3, "content"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert_eq!(
                pathland_qt_layer_scroll_content_has_child(2, 3),
                1,
                "the content attaches to the contentItem"
            );
            // Not stretched to the 300x150 viewport.
            assert!(
                (pathland_qt_layer_widget_size(3, 0) - 50.0).abs() < 1.0,
                "content keeps its own width (got {})",
                pathland_qt_layer_widget_size(3, 0)
            );
            assert!(
                (pathland_qt_layer_widget_size(3, 1) - 50.0).abs() < 1.0,
                "content keeps its own height (got {})",
                pathland_qt_layer_widget_size(3, 1)
            );

            // It may grow beyond the viewport (scrolls).
            let tall = [Command::set_property(
                3,
                property_id::HEIGHT,
                value_type::F32,
                400.0f32.to_bits(),
            )];
            pathland_qt_layer_apply(tall.as_ptr(), tall.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);
            assert!(
                (pathland_qt_layer_widget_size(3, 1) - 400.0).abs() < 1.0,
                "content grows past the viewport height (got {})",
                pathland_qt_layer_widget_size(3, 1)
            );
        });
    }

    /// HUG_CONTENT (-2) overrides greed: the viewport sizes to the content on
    /// that axis (spec/PRIMITIVES.md §ScrollView: "a Hug WIDTH/HEIGHT ... sizes
    /// the viewport to the content's natural size").
    #[test]
    fn scroll_view_hug_content_sizes_to_content() {
        with_qt(|| unsafe {
            let cmds = [
                Command::create_node(1, component_type::ZSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::HSTACK),
                Command::insert_child(1, 2, u32::MAX),
                Command::set_property(2, property_id::WIDTH, value_type::F32, 400.0f32.to_bits()),
                Command::set_property(2, property_id::HEIGHT, value_type::F32, 200.0f32.to_bits()),
                Command::create_node(3, component_type::SCROLLVIEW),
                Command::insert_child(2, 3, u32::MAX),
                Command::set_property(3, property_id::WIDTH, value_type::F32, (-2.0f32).to_bits()),
                Command::create_node(4, component_type::TEXT),
                Command::insert_child(3, 4, u32::MAX),
                Command::set_property(4, property_id::WIDTH, value_type::F32, 80.0f32.to_bits()),
                Command::set_property(4, property_id::HEIGHT, value_type::F32, 40.0f32.to_bits()),
                Command::set_text(4, "hug"),
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);
            assert_eq!(pathland_qt_layer_render_once(), 1);

            assert_eq!(
                pathland_qt_layer_widget_fill(3, 0),
                0,
                "HUG_CONTENT overrides the greedy main-axis fill"
            );
            assert_eq!(pathland_qt_layer_widget_fill(3, 1), 1, "height is still greedy");
            assert!((pathland_qt_layer_widget_size(3, 1) - 200.0).abs() < 1.0, "height fills");
            let w = pathland_qt_layer_widget_size(3, 0);
            assert!(w > 0.0 && w < 400.0, "width hugs the content, not the stack (got {w})");
        });
    }

    /// EVENT_LISTENERS pointer bits attach a MouseArea overlay to the node's
    /// QML item (spec/EVENTS.md: any element can emit raw inputs).
    #[test]
    fn pointer_listeners_attach_mousearea() {
        with_qt(|| {
            let mut view = vstack![text("listener")
                .modifiers(PointerEvents(listener::POINTER_DOWN | listener::POINTER_UP))]
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
                ProgressView::with(|p| {
                    p.value(0.5);
                }),
                Stepper::with(|s| {
                    s.value(3.0).min(0.0).max(10.0);
                }),
                Gauge::with(|g| {
                    g.value(4.0).min(0.0).max(10.0);
                }),
                Menu::new(),
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
                pathland_qt_layer_widget_prop_text(
                    2,
                    c"value".as_ptr(),
                    buf.as_mut_ptr(),
                    buf.len() as u32,
                )
            };
            assert_eq!(
                unsafe { CStr::from_ptr(buf.as_ptr()) }.to_string_lossy(),
                "0.5"
            );
            // node 3 = SpinBox with the (int-cast) value applied.
            unsafe {
                pathland_qt_layer_widget_prop_text(
                    3,
                    c"value".as_ptr(),
                    buf.as_mut_ptr(),
                    buf.len() as u32,
                )
            };
            assert_eq!(
                unsafe { CStr::from_ptr(buf.as_ptr()) }.to_string_lossy(),
                "3"
            );
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
                pathland_qt_layer_widget_prop_text(
                    2,
                    c"count".as_ptr(),
                    buf.as_mut_ptr(),
                    buf.len() as u32,
                )
            };
            assert_eq!(
                unsafe { CStr::from_ptr(buf.as_ptr()) }.to_string_lossy(),
                "2"
            );
        });
    }

    // -- Navigation adapter (Phase C): hand-built command batches drive the C++
    //    page-stack reconcile by ROUTE / NAV_DEPTH / NAV_CHROME. ---------------

    fn nav_depth() -> u32 {
        unsafe { pathland_qt_layer_nav_depth(1) }
    }

    /// A slot carrying ROUTE promotes to a StackView and pushes its root page
    /// (the destination subtree stays alive inside the page).
    #[test]
    fn nav_slot_promotes_and_pushes_root_page() {
        with_qt(|| {
            let cmds = [
                Command::create_node(1, component_type::VSTACK),
                Command::create_node(2, component_type::VSTACK),
                Command::create_node(3, component_type::TEXT),
                Command::set_string_property(1, property_id::ROUTE, "/home"),
                Command::set_property(1, property_id::NAV_DEPTH, value_type::U32, 1),
                Command::insert_child(2, 3, u32::MAX),
                Command::insert_child(1, 2, u32::MAX),
                Command::insert_child(0, 1, u32::MAX),
                Command::set_text(3, "hello"),
            ];
            unsafe { pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32) };

            assert_eq!(
                nav_depth(),
                1,
                "slot promoted to a nav container with one page"
            );
            assert_eq!(
                unsafe { pathland_qt_layer_nav_page_fills(1) },
                1,
                "the page wrapper fills the nav container (content-area sizing)"
            );
            assert_eq!(
                read_text(3),
                "hello",
                "destination subtree alive in the page"
            );
        });
    }

    /// The real content-area layout: a nav slot inside a ScrollView under an
    /// HStack. After showing + laying out, the destination subtree fills the
    /// content (not 0×0), so a route page renders more than an overflowing title.
    #[test]
    fn nav_destination_fills_the_content_area() {
        with_qt(|| {
            let cmds = [
                Command::create_node(1, component_type::HSTACK),
                Command::insert_child(CONTENT_ITEM, 1, u32::MAX),
                Command::create_node(2, component_type::SCROLLVIEW),
                Command::insert_child(1, 2, u32::MAX),
                Command::create_node(3, component_type::VSTACK), // nav slot
                Command::insert_child(2, 3, u32::MAX),
                // Full-width content (the scroll view hugs its cross axis unless
                // the content fills — spec/PRIMITIVES.md §ScrollView).
                Command::set_property(3, property_id::WIDTH, value_type::F32, (-1.0f32).to_bits()),
                Command::create_node(4, component_type::VSTACK), // destination
                Command::insert_child(3, 4, u32::MAX),
                Command::create_node(5, component_type::TEXT),
                Command::insert_child(4, 5, u32::MAX),
                Command::set_text(5, "Home"),
                Command::set_string_property(3, property_id::ROUTE, "/home"),
                Command::set_property(3, property_id::NAV_DEPTH, value_type::U32, 1),
            ];
            unsafe { pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32) };

            // Show + process events so the offscreen window runs a layout pass.
            assert_eq!(unsafe { pathland_qt_layer_render_once() }, 1);
            let w = unsafe { pathland_qt_layer_widget_size(4, 0) };
            let h = unsafe { pathland_qt_layer_widget_size(4, 1) };
            assert!(
                w > 0.0 && h > 0.0,
                "destination fills the content area (got {w}x{h})"
            );
        });
    }

    /// Deeper NAV_DEPTH pushes a page; a shallower depth pops back down.
    #[test]
    fn nav_depth_push_and_pop() {
        with_qt(|| {
            let base = [
                Command::create_node(1, component_type::VSTACK),
                Command::create_node(2, component_type::VSTACK),
                Command::create_node(3, component_type::TEXT),
                Command::set_string_property(1, property_id::ROUTE, "/home"),
                Command::set_property(1, property_id::NAV_DEPTH, value_type::U32, 1),
                Command::insert_child(2, 3, u32::MAX),
                Command::insert_child(1, 2, u32::MAX),
                Command::insert_child(0, 1, u32::MAX),
                Command::set_text(3, "home"),
            ];
            unsafe { pathland_qt_layer_apply(base.as_ptr(), base.len() as u32) };
            assert_eq!(nav_depth(), 1);

            // Push: depth 2 with a new destination subtree (4 -> 5).
            // Deeper + new route -> push (a same-route frame is a Refresh).
            let push = [
                Command::create_node(4, component_type::VSTACK),
                Command::create_node(5, component_type::TEXT),
                Command::set_property(1, property_id::NAV_DEPTH, value_type::U32, 2),
                Command::set_string_property(1, property_id::ROUTE, "/detail"),
                Command::insert_child(4, 5, u32::MAX),
                Command::remove_child(1, 2),
                Command::insert_child(1, 4, u32::MAX),
                Command::set_text(5, "detail"),
            ];
            unsafe { pathland_qt_layer_apply(push.as_ptr(), push.len() as u32) };
            assert_eq!(nav_depth(), 2);
            assert_eq!(
                unsafe { pathland_qt_layer_nav_page_fills(1) },
                1,
                "a pushed page also fills the nav container"
            );
            assert_eq!(read_text(5), "detail");

            // Pop: back to depth 1 and the previous destination.
            let pop = [
                Command::set_property(1, property_id::NAV_DEPTH, value_type::U32, 1),
                Command::remove_child(1, 4),
                Command::insert_child(1, 2, u32::MAX),
            ];
            unsafe { pathland_qt_layer_apply(pop.as_ptr(), pop.len() as u32) };
            assert_eq!(nav_depth(), 1);
            assert_eq!(read_text(3), "home");
        });
    }

    /// Same depth with a new route replaces the top page's content in place.
    #[test]
    fn nav_replace_same_depth_new_route() {
        with_qt(|| {
            let base = [
                Command::create_node(1, component_type::VSTACK),
                Command::create_node(2, component_type::VSTACK),
                Command::create_node(3, component_type::TEXT),
                Command::set_string_property(1, property_id::ROUTE, "/home"),
                Command::set_property(1, property_id::NAV_DEPTH, value_type::U32, 1),
                Command::insert_child(2, 3, u32::MAX),
                Command::insert_child(1, 2, u32::MAX),
                Command::insert_child(0, 1, u32::MAX),
                Command::set_text(3, "home"),
            ];
            unsafe { pathland_qt_layer_apply(base.as_ptr(), base.len() as u32) };
            assert_eq!(nav_depth(), 1);

            // Depth 2 + new route -> push.
            let push = [
                Command::create_node(4, component_type::VSTACK),
                Command::create_node(5, component_type::TEXT),
                Command::set_property(1, property_id::NAV_DEPTH, value_type::U32, 2),
                Command::set_string_property(1, property_id::ROUTE, "/detail"),
                Command::insert_child(4, 5, u32::MAX),
                Command::remove_child(1, 2),
                Command::insert_child(1, 4, u32::MAX),
                Command::set_text(5, "detail"),
            ];
            unsafe { pathland_qt_layer_apply(push.as_ptr(), push.len() as u32) };
            assert_eq!(nav_depth(), 2);

            // Same depth, new route -> replace the top page content.
            let replace = [
                Command::create_node(6, component_type::VSTACK),
                Command::create_node(7, component_type::TEXT),
                Command::set_string_property(1, property_id::ROUTE, "/settings"),
                Command::remove_child(1, 4),
                Command::insert_child(1, 6, u32::MAX),
                Command::set_text(7, "settings"),
            ];
            unsafe { pathland_qt_layer_apply(replace.as_ptr(), replace.len() as u32) };
            assert_eq!(nav_depth(), 2, "replace keeps the stack depth");
            assert_eq!(read_text(7), "settings");
        });
    }

    /// NAV_CHROME=1 (custom) pushes the bare destination as the page (no
    /// renderer-supplied chrome wrapper).
    #[test]
    fn nav_custom_chrome_bare_pages() {
        with_qt(|| {
            let cmds = [
                Command::create_node(1, component_type::VSTACK),
                Command::create_node(2, component_type::VSTACK),
                Command::create_node(3, component_type::TEXT),
                Command::set_string_property(1, property_id::ROUTE, "/custom"),
                Command::set_property(1, property_id::NAV_DEPTH, value_type::U32, 1),
                Command::set_property(
                    1,
                    property_id::NAV_CHROME,
                    value_type::F32,
                    1.0f32.to_bits(),
                ),
                Command::insert_child(2, 3, u32::MAX),
                Command::insert_child(1, 2, u32::MAX),
                Command::insert_child(0, 1, u32::MAX),
                Command::set_text(3, "custom"),
            ];
            unsafe { pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32) };

            assert_eq!(nav_depth(), 1, "custom chrome still uses the nav container");
            assert_eq!(read_text(3), "custom");
        });
    }
}
