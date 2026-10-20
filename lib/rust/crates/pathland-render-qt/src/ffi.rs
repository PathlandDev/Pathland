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
    pub fn pathland_qt_layer_nav_depth(slot: u32) -> u32;
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
    pub fn pathland_qt_layer_widget_fill(id: u32, which: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_widget_preferred(id: u32, which: u32) -> f64;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_widget_size(id: u32, which: u32) -> f64;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_anchors_fill(id: u32) -> u32;
    #[allow(dead_code)]
    pub fn pathland_qt_layer_video_source(id: u32) -> u32;
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
        assign_ids, button, text, vstack, Configurable, Node, PointerEvents, Slider, View, ViewExt,
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

    /// FILL sizing inside a stack (the "sliver" fix): a WIDTH FILL child
    /// stretches via the Layout attached property; a fixed width becomes the
    /// Layout preferredWidth; a stack's ALIGNMENT=FILL stretches children on the
    /// cross axis.
    #[test]
    fn fill_child_stretches_inside_stack() {
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
            // Root ALIGNMENT=FILL -> children fill the cross axis (height).
            assert_eq!(pathland_qt_layer_widget_fill(2, 1), 1);
            assert_eq!(pathland_qt_layer_widget_fill(3, 1), 1);
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

    /// SIZE_THAT_FITS derives its fit index from the allocated width against the
    /// FIT_QUERY threshold table and reports it (spec/PRIMITIVES.md).
    #[test]
    fn size_that_fits_derives_index_from_width() {
        with_qt(|| unsafe {
            // The fit slot stores FIT_QUERY; the renderer measures its allocated
            // width and derives the index (the harness drives the measurement via
            // the report_fit hook, since an offscreen layout has no real width).
            let cmds = [
                Command::create_node(2, 0x17),                    // SIZE_THAT_FITS
                Command::set_string_property(2, 0x1039, "0,640"), // FIT_QUERY
            ];
            pathland_qt_layer_apply(cmds.as_ptr(), cmds.len() as u32);

            pathland_qt_layer_report_fit(2, 500.0);
            assert_eq!(pathland_qt_layer_fit_index(2), 0, "500 < 640 -> compact");
            pathland_qt_layer_report_fit(2, 640.0);
            assert_eq!(pathland_qt_layer_fit_index(2), 1, "640 >= 640 -> wide");
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

    /// Default stack alignment is stretch (CSS align-items:stretch): a child
    /// fills the cross axis, a Spacer expands on the main axis, and an explicit
    /// ALIGNMENT suppresses the default.
    #[test]
    fn default_stack_stretch_and_spacer() {
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
                1,
                "HSTACK child stretches on the cross axis (height)"
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

            // Explicit ALIGNMENT (Center = 1) suppresses the default stretch.
            let aligned = [
                Command::create_node(20, 0x11), // HSTACK
                Command::insert_child(CONTENT_ITEM, 20, u32::MAX),
                Command::set_property(20, 0x0002, value_type::F32, 1.0f32.to_bits()), // ALIGNMENT
                Command::create_node(21, 0x01),                                       // TEXT
                Command::insert_child(20, 21, u32::MAX),
            ];
            pathland_qt_layer_apply(aligned.as_ptr(), aligned.len() as u32);
            assert_eq!(
                pathland_qt_layer_widget_fill(21, 1),
                0,
                "explicit alignment is not overwritten by the stretch default"
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
                read_text(3),
                "hello",
                "destination subtree alive in the page"
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
