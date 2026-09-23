//! # pathland-render-gtk
//!
//! The Pathland **GTK4 renderer**. It consumes opcode frames (via
//! [`crate::host::RenderTree`]) and maps them **incrementally** onto native GTK4
//! widgets — `GtkBox` for stacks, `GtkLabel` for text, `GtkButton` for buttons.
//!
//! This is the only place in the desktop path that touches GTK/glib/pango APIs.
//! Demos author their UI with the Pathland DSL and drive this renderer; they
//! never call GTK directly.
//!
//! ## Rust host
//!
//! `GtkRenderer::run(app_id, title, ring, on_event)` owns the `gtk::Application`
//! / `ApplicationWindow` / main loop, pumps the shared ring (frames in, events
//! out), and dispatches raw-input [`Event`]s back to the application.
//!
//! ## Java host
//!
//! The `pathland_gtk_*` C ABI (`capi` module) exposes the same renderer to a
//! foreign host (Java via JNA): `pathland_gtk_run(host, on_event)` runs GTK
//! in-process over the `pathland-view-native` host's shared ring.

mod capi;
mod host;
mod layout;
mod tokens;

pub use host::{describe, render_tree_from_frame, HostNode, RenderTree};

use std::cell::{Cell, RefCell};
use std::collections::HashMap;
use std::rc::Rc;

use libadwaita::prelude::*;
use libadwaita::NavigationView;
use gtk::{
    Align, Box as GtkBox, Button, EventControllerKey, EventControllerMotion, GestureClick, Label,
    PropagationPhase,
};
use pathland_core::tokens::Scheme;
use pathland_core::{component_type, listener, property_id, size, Event, Frame};
use pathland_core_transport::{DriverTransport, FrameSource, OpcodeBatch, RingTransport, TransportError};
use glib::translate::IntoGlib;

/// The bidirectional transport surface the renderer pumps: frames in
/// (guest → host) and events out (host → guest).
///
/// The renderer **never drains events** — it only writes them as `EVENT`
/// opcodes. The host (application) drains the event ring itself, so raw inputs
/// return to the app through the opcode engine, symmetric with how frames flow
/// to the renderer.
pub trait Pump {
    /// Read the next guest → host frame.
    fn next_frame(&mut self) -> Result<Option<OpcodeBatch<'_>>, TransportError>;
    /// Write a host → guest raw input as an `EVENT` opcode.
    fn send_input(&mut self, event: &Event) -> Result<(), TransportError>;
}

impl Pump for RingTransport {
    fn next_frame(&mut self) -> Result<Option<OpcodeBatch<'_>>, TransportError> {
        FrameSource::next_frame(self)
    }
    fn send_input(&mut self, event: &Event) -> Result<(), TransportError> {
        DriverTransport::send_input(self, event)
    }
}

/// The GTK4 renderer: a retained `id → gtk::Widget` map kept in sync with the
/// opcode stream, applying deltas incrementally.
pub struct GtkRenderer {
    tree: RenderTree,
    widgets: HashMap<u32, gtk::Widget>,
    /// node id → listener bitmask already attached (idempotency guard so delta
    /// frames don't double-attach controllers).
    attached_listeners: HashMap<u32, u32>,
    /// node id → composite-body box (Composite Override Mode): the container
    /// that holds a control's custom child tree.
    composite_boxes: HashMap<u32, gtk::Widget>,
    /// Set once before the first render so buttons can report clicks.
    event_sink: Option<Rc<RefCell<dyn FnMut(Event)>>>,
    /// A widget's style context captured at first build, used to enrich the
    /// design-token defaults with GTK-native theme colors (`None` headless).
    native_ctx: Option<gtk::StyleContext>,
    /// `AdwNavigationView` handles for navigation slots (a container carrying
    /// the `ROUTE` property), keyed by slot node id.
    nav_views: HashMap<u32, NavigationView>,
    /// Per-navigation-slot page stack mirror (shared with the `popped`
    /// handler), keyed by slot node id. The native `AdwNavigationView` is the
    /// renderer's rendered-output cache of the app's back-stack; this vec is
    /// the adapter's depth bookkeeping (the widget's own page stack is not
    /// directly countable).
    nav_pages: HashMap<u32, Rc<RefCell<Vec<libadwaita::NavigationPage>>>>,
    /// Per-navigation-slot "reconciling" flag: while the renderer pops pages
    /// itself (a route change it already applied), the `popped` signal must not
    /// be re-emitted as a back request — the app already knows it navigated.
    nav_reconciling: HashMap<u32, Rc<Cell<bool>>>,
}

impl Default for GtkRenderer {
    fn default() -> Self {
        Self::new()
    }
}

impl GtkRenderer {
    /// Create an empty renderer. Call [`GtkRenderer::apply_frame`] per frame.
    pub fn new() -> Self {
        Self {
            tree: RenderTree::default(),
            widgets: HashMap::new(),
            attached_listeners: HashMap::new(),
            composite_boxes: HashMap::new(),
            event_sink: None,
            native_ctx: None,
            nav_views: HashMap::new(),
            nav_pages: HashMap::new(),
            nav_reconciling: HashMap::new(),
        }
    }

    /// Set the event sink (called when a native widget reports an input).
    pub fn set_event_sink(&mut self, sink: Rc<RefCell<dyn FnMut(Event)>>) {
        self.event_sink = Some(sink);
    }

    /// The effective color scheme the renderer resolved from the platform.
    pub fn scheme(&self) -> Scheme {
        self.tree.scheme()
    }

    /// Set the effective color scheme (renderer-derived, never carried by the
    /// protocol) and re-resolve every design token — native defaults for the
    /// new scheme are re-enriched from the GTK theme when a widget exists, then
    /// all token-referencing properties re-resolve and the widget tree re-applies.
    pub fn set_scheme(&mut self, scheme: Scheme) {
        self.enrich_native_defaults();
        self.tree.set_scheme(scheme);
        self.sync();
    }

    /// One-time best-effort: replace Tier-1 default tokens the current GTK
    /// theme can resolve (`@theme_*` named colors) with native colors, using
    /// the first widget's style context. Headless → concrete fallbacks remain.
    fn enrich_native_defaults(&mut self) {
        if let Some(ctx) = self.native_ctx.as_ref() {
            let scheme = self.tree.scheme();
            let (base, dark) = tokens::enrich_defaults(
                &|name| tokens::lookup_color(ctx, name),
                scheme,
            );
            self.tree.set_defaults(base, dark);
        }
    }

    /// Apply one frame of opcode deltas and reconcile the GTK widget tree.
    ///
    /// Unchanged widgets are preserved (no full rebuild); only nodes the frame
    /// touched get their native widget created, updated, or removed.
    pub fn apply_frame(&mut self, frame: &Frame<'_>) {
        self.tree.apply_frame(frame);
        self.sync();
    }

    /// The root widget (the native element for the tree root).
    pub fn root_widget(&self) -> Option<gtk::Widget> {
        self.tree
            .root()
            .and_then(|n| self.widgets.get(&n.id).cloned())
    }

    /// Reconcile the GTK widget tree against the current `RenderTree`.
    fn sync(&mut self) {
        // 1. Drop widgets for nodes that no longer exist.
        let live: std::collections::HashSet<u32> = self.tree.nodes.keys().copied().collect();
        let stale: Vec<u32> = self
            .widgets
            .keys()
            .copied()
            .filter(|id| !live.contains(id))
            .collect();
        for id in stale {
            self.widgets.remove(&id);
            self.attached_listeners.remove(&id);
            self.composite_boxes.remove(&id);
            self.nav_views.remove(&id);
            self.nav_pages.remove(&id);
            self.nav_reconciling.remove(&id);
        }

        // 2. Pre-order walk: get-or-create widgets, update text/style, and
        //    reconcile each stack's child list/order.
        if let Some(root_id) = self.tree.root().map(|n| n.id) {
            self.sync_node(root_id);
        }
    }

    fn sync_node(&mut self, id: u32) {
        let Some(node) = self.tree.node(id).cloned() else {
            return;
        };

        let widget = self.get_or_create(id, &node);
        // The first get_or_create may have just enriched the token defaults
        // with GTK theme colors; re-read the node so the values applied now
        // are the native-resolved ones, not the pre-enrichment fallbacks.
        let node = self.tree.node(id).cloned().unwrap_or(node);
        self.update(widget, &node);

        // Recurse into children (build them first so they exist to attach).
        let children = node.children.clone();
        for child in &children {
            self.sync_node(*child);
        }

        // Containers: reconcile the native children (order/content/positions)
        // against the tree.
        if is_container(node.component_type) {
            self.sync_container(id, &children, &node);
        } else if is_composite(&node) {
            self.sync_composite_children(id, &children);
        }
    }

    /// Composite Override Mode: attach a control's custom child tree as a box
    /// wrapped by the control's native shell (the widget itself).
    ///
    /// Composite bodies attach to button-like controls (`GtkButton` and
    /// subclasses — `ToggleButton`, `CheckButton`, `MenuButton`); other controls
    /// ignore children.
    fn sync_composite_children(&mut self, parent_id: u32, children: &[u32]) {
        let Some(parent) = self.widgets.get(&parent_id).cloned() else {
            return;
        };
        let Ok(btn) = parent.downcast::<gtk::Button>() else {
            return;
        };
        let box_widget: gtk::Widget = if let Some(bx) = self.composite_boxes.get(&parent_id).cloned() {
            bx
        } else {
            let bx = GtkBox::new(gtk::Orientation::Vertical, 0);
            bx.set_hexpand(true);
            bx.set_vexpand(true);
            btn.set_child(Some(&bx));
            let bxw: gtk::Widget = bx.upcast();
            self.composite_boxes.insert(parent_id, bxw.clone());
            bxw
        };
        self.reconcile_box_children(&box_widget, children);
    }

    fn get_or_create(&mut self, id: u32, node: &HostNode) -> gtk::Widget {
        if let Some(w) = self.widgets.get(&id) {
            return w.clone();
        }
        let widget = build_widget(node);
        // Register a navigation slot's `AdwNavigationView` so page reconcile
        // (`sync_navigation_children`) can push/pop on it.
        if is_nav_slot(node) {
            if let Ok(nav) = widget.clone().downcast::<NavigationView>() {
                self.nav_views.insert(id, nav);
            }
        }
        // Capture a style context once the first native widget exists so the
        // design-token defaults can be enriched with GTK theme colors.
        if self.native_ctx.is_none() {
            self.native_ctx = Some(widget.style_context());
            self.enrich_native_defaults();
        }
        self.widgets.insert(id, widget.clone());
        widget
    }

    /// Apply the current text/style/layout to an existing widget (idempotent).
    fn update(&mut self, widget: gtk::Widget, node: &HostNode) {
        // Padding is styling (a DSL modifier): apply it to any widget as margins.
        apply_padding(&widget, node);
        apply_style(&widget, node);

        // Attach (or reconcile) native input recognition for the node's event
        // listeners. Buttons listen to pointer down/move/up by default; any
        // node can request more via the `EVENT_LISTENERS` property.
        let mask = desired_listeners(node);
        self.attach_listeners(node.id, &widget, mask);
        // Value/text events: value-bearing controls report by component type,
        // gated by the transport-aware event guards (a `BINDING_ID`).
        self.attach_control_events(node.id, &widget, node);

        match widget_kind(node.component_type) {
            WidgetKind::Stack => {
                if let Ok(bx) = widget.downcast::<GtkBox>() {
                    if let Some(l) = layout::stack_layout(node) {
                        bx.set_spacing(l.spacing);
                    }
                }
            }
            WidgetKind::Text => {
                if let Ok(label) = widget.downcast::<Label>() {
                    label.set_text(node.text.as_deref().unwrap_or(""));
                    apply_text_style(&label, node);
                }
            }
            WidgetKind::Button => {
                if let Ok(btn) = widget.downcast::<Button>() {
                    if !is_composite(node) {
                        btn.set_label(node.text.as_deref().unwrap_or(""));
                    }
                }
            }
            WidgetKind::Picker => {
                if let Ok(dd) = widget.downcast::<gtk::DropDown>() {
                    let options: Vec<String> = node
                        .children
                        .iter()
                        .filter_map(|c| self.tree.node(*c).and_then(|n| n.text.clone()))
                        .collect();
                    let strings: Vec<&str> = options.iter().map(String::as_str).collect();
                    dd.set_model(Some(&gtk::StringList::new(&strings)));
                    dd.set_selected(node.u32_property(property_id::SELECTION, 0));
                }
            }
            WidgetKind::Image => {
                if let Ok(pic) = widget.downcast::<gtk::Picture>() {
                    if let Some(src) = node.string_property(property_id::IMAGE_SOURCE) {
                        pic.set_filename(Some(src));
                    }
                }
            }
            WidgetKind::Toggle => {
                let active = node.checked();
                if let Ok(sw) = widget.clone().downcast::<gtk::Switch>() {
                    sw.set_active(active);
                } else if let Ok(tb) = widget.clone().downcast::<gtk::ToggleButton>() {
                    tb.set_active(active);
                }
            }
            WidgetKind::Slider => {
                if let Ok(scale) = widget.downcast::<gtk::Scale>() {
                    let min = f64::from(node.f32_property(property_id::MIN_VALUE, 0.0));
                    let max = f64::from(node.f32_property(property_id::MAX_VALUE, 1.0));
                    let step = f64::from(node.f32_property(property_id::STEP_VALUE, 0.0));
                    let value = f64::from(node.f32_property(property_id::VALUE, 0.0));
                    scale.set_range(min, max);
                    if step > 0.0 {
                        scale.set_increments(step, step * 10.0);
                    }
                    scale.set_value(value);
                }
            }
            WidgetKind::TextField => {
                if let Ok(entry) = widget.downcast::<gtk::Entry>() {
                    entry.set_text(node.text.as_deref().unwrap_or(""));
entry.set_placeholder_text(Some(node.string_property(property_id::PROMPT).unwrap_or("")));
                    entry.set_visibility(node.f32_property(property_id::IS_SECURE, 0.0) == 0.0);
                }
            }
            WidgetKind::TextEditor => {
                if let Ok(view) = widget.downcast::<gtk::TextView>() {
                    let buf = view.buffer();
                    if buf.text(&buf.start_iter(), &buf.end_iter(), false).as_str()
                        != node.text.as_deref().unwrap_or("")
                    {
                        buf.set_text(node.text.as_deref().unwrap_or(""));
                    }
                }
            }
            WidgetKind::ProgressView => {
                if let Ok(bar) = widget.downcast::<gtk::ProgressBar>() {
                    let value = node.f32_property(property_id::PROGRESS, 0.0);
                    let max = node.f32_property(property_id::MAX_VALUE, 1.0);
                    bar.set_fraction(if max > 0.0 { f64::from(value / max) } else { 0.0 });
                }
            }
            WidgetKind::Gauge => {
                if let Ok(level) = widget.downcast::<gtk::LevelBar>() {
                    level.set_min_value(f64::from(node.f32_property(property_id::MIN_VALUE, 0.0)));
                    level.set_max_value(f64::from(node.f32_property(property_id::MAX_VALUE, 1.0)));
                    level.set_value(f64::from(node.f32_property(property_id::VALUE, 0.0)));
                }
            }
            WidgetKind::Stepper => {
                if let Ok(spin) = widget.downcast::<gtk::SpinButton>() {
                    let min = f64::from(node.f32_property(property_id::MIN_VALUE, 0.0));
                    let max = f64::from(node.f32_property(property_id::MAX_VALUE, 100.0));
                    let step = f64::from(node.f32_property(property_id::STEP_VALUE, 1.0));
                    let adj = spin.adjustment();
                    adj.set_lower(min);
                    adj.set_upper(max);
                    adj.set_step_increment(step);
                    spin.set_value(f64::from(node.f32_property(property_id::VALUE, 0.0)));
                }
            }
            WidgetKind::ColorPicker => {
                if let Ok(btn) = widget.downcast::<gtk::ColorButton>() {
                    btn.set_rgba(&color_rgba(node.u32_property(property_id::COLOR_VALUE, 0xFF00_0000)));
                }
            }
            _ => {}
        }
    }

    /// Attach value/text-event emission for value-bearing controls, gated by
    /// the transport-aware event guards: a control reports `VALUE_CHANGED` /
    /// `TEXT_CHANGED` only when it carries a `BINDING_ID`.
    fn attach_control_events(&mut self, id: u32, widget: &gtk::Widget, node: &HostNode) {
        let binding = node.properties.get(&property_id::BINDING_ID).copied();
        if binding.is_none() {
            return;
        }
        let Some(sink) = self.event_sink.clone() else {
            return;
        };
        match widget_kind(node.component_type) {
            WidgetKind::Toggle => {
                if let Ok(sw) = widget.clone().downcast::<gtk::Switch>() {
                    sw.connect_active_notify(move |sw| {
                        sink.borrow_mut()(Event::ValueChanged {
                            target: id,
                            value: if sw.is_active() { 1.0 } else { 0.0 },
                        });
                    });
                } else if let Ok(tb) = widget.clone().downcast::<gtk::ToggleButton>() {
                    tb.connect_toggled(move |tb| {
                        sink.borrow_mut()(Event::ValueChanged {
                            target: id,
                            value: if tb.is_active() { 1.0 } else { 0.0 },
                        });
                    });
                }
            }
            WidgetKind::Slider => {
                if let Ok(scale) = widget.clone().downcast::<gtk::Scale>() {
                    scale.connect_value_changed(move |scale| {
                        sink.borrow_mut()(Event::ValueChanged {
                            target: id,
                            value: scale.value() as f32,
                        });
                    });
                }
            }
            WidgetKind::TextField => {
                if let Ok(entry) = widget.clone().downcast::<gtk::Entry>() {
                    entry.connect_changed(move |entry| {
                        sink.borrow_mut()(Event::TextChanged {
                            target: id,
                            value: entry.text().to_string(),
                        });
                    });
                }
            }
            WidgetKind::Picker => {
                if let Ok(dd) = widget.clone().downcast::<gtk::DropDown>() {
                    dd.connect_selected_notify(move |dd| {
                        sink.borrow_mut()(Event::ValueChanged {
                            target: id,
                            value: dd.selected() as f32,
                        });
                    });
                }
            }
            _ => {}
        }
    }

    /// Attach native input recognition for the listener bits not yet wired for
    /// `id`. Guarded by `attached_listeners` so delta frames don't double-attach.
    fn attach_listeners(&mut self, id: u32, widget: &gtk::Widget, mask: u32) {
        let attached = *self.attached_listeners.get(&id).unwrap_or(&0);
        let new = mask & !attached;
        if new == 0 {
            return;
        }
        if let Some(sink) = &self.event_sink {
            let sink = sink.clone();
            let want_down = new & listener::POINTER_DOWN != 0;
            let want_up = new & listener::POINTER_UP != 0;
            if want_down || want_up {
                attach_pointer_gesture(widget, id, sink.clone(), want_down, want_up);
            }
            if new & listener::POINTER_MOVE != 0 {
                attach_motion(widget, id, sink.clone());
            }
        }
        self.attached_listeners.insert(id, mask);
    }

    /// Reconcile a container's native children against the tree-ordered child
    /// widgets. Leaf widgets are preserved (never recreated); only the
    /// parent→child links are re-established, so unchanged subtrees stay put.
    fn sync_container(&mut self, parent_id: u32, children: &[u32], node: &HostNode) {
        if is_nav_slot(node) {
            self.sync_navigation_children(parent_id, children, node);
            return;
        }
        match widget_kind(node.component_type) {
            WidgetKind::Stack => self.sync_stack_children(parent_id, children, node),
            WidgetKind::Grid => self.sync_grid_children(parent_id, children, node),
            WidgetKind::ScrollView => self.sync_scroll_children(parent_id, children),
            WidgetKind::Overlay => self.sync_overlay_children(parent_id, children),
            _ => {}
        }
    }

    /// Reconcile a stack's children: apply cross-axis alignment to each child
    /// and re-append the tree-ordered child widgets.
    fn sync_stack_children(&mut self, parent_id: u32, children: &[u32], node: &HostNode) {
        let Some(parent) = self.widgets.get(&parent_id).cloned() else {
            return;
        };
        let Some(_bx) = parent.downcast_ref::<GtkBox>() else {
            return;
        };
        let Some(l) = layout::stack_layout(node) else {
            return;
        };

        // Cross-axis alignment of each child: a vertical stack aligns children
        // horizontally (`halign`), a horizontal stack vertically (`valign`).
        for child_id in children {
            if let Some(w) = self.widgets.get(child_id) {
                if layout::cross_axis_is_horizontal(l.orientation) {
                    w.set_halign(l.child_align);
                } else {
                    w.set_valign(l.child_align);
                }
            }
        }

        self.reconcile_box_children(&parent, children);
    }

    /// Reconcile a `GtkGrid`'s children at their row-major cell positions.
    fn sync_grid_children(&mut self, parent_id: u32, children: &[u32], node: &HostNode) {
        let Some(parent) = self.widgets.get(&parent_id).cloned() else {
            return;
        };
        let Some(grid) = parent.downcast_ref::<gtk::Grid>() else {
            return;
        };
        let columns = layout::grid_columns(node);
        if grid_matches(grid, children, columns, &self.widgets) {
            return;
        }
        // Rebuild: remove all current children, then attach at target cells.
        for w in child_widgets(&parent) {
            grid.remove(&w);
        }
        for (idx, child_id) in children.iter().enumerate() {
            if let Some(w) = self.widgets.get(child_id) {
                let (row, col) = layout::grid_cell_position(idx as u32, columns);
                grid.attach(w, col as i32, row as i32, 1, 1);
            }
        }
    }

    /// Reconcile a `GtkScrolledWindow`: exactly one child (the first).
    fn sync_scroll_children(&mut self, parent_id: u32, children: &[u32]) {
        let Some(parent) = self.widgets.get(&parent_id).cloned() else {
            return;
        };
        let Some(sw) = parent.downcast_ref::<gtk::ScrolledWindow>() else {
            return;
        };
        let want: Option<gtk::Widget> = children
            .first()
            .and_then(|id| self.widgets.get(id).cloned());
        if sw.child().as_ref() != want.as_ref() {
            if let Some(w) = want {
                sw.set_child(Some(&w));
            } else {
                sw.set_child(None::<&gtk::Widget>);
            }
        }
    }

    /// Reconcile a `GtkOverlay`: the first child is the main child, the rest
    /// are overlays (later = drawn on top).
    fn sync_overlay_children(&mut self, parent_id: u32, children: &[u32]) {
        let Some(parent) = self.widgets.get(&parent_id).cloned() else {
            return;
        };
        let Some(ov) = parent.downcast_ref::<gtk::Overlay>() else {
            return;
        };
        let target: Vec<gtk::Widget> = children
            .iter()
            .filter_map(|id| self.widgets.get(id).cloned())
            .collect();
        if child_widgets(&parent) == target {
            return;
        }
        for w in child_widgets(&parent) {
            ov.remove_overlay(&w);
        }
        if let Some(main) = target.first() {
            ov.set_child(Some(main));
            for overlay in target.iter().skip(1) {
                ov.add_overlay(overlay);
            }
        }
    }

    /// Re-append the tree-ordered child widgets to a box (order/content diff).
    fn reconcile_box_children(&mut self, parent: &gtk::Widget, children: &[u32]) {
        let current = child_widgets(parent);
        let target: Vec<gtk::Widget> = children
            .iter()
            .filter_map(|id| self.widgets.get(id).cloned())
            .collect();
        if current == target {
            return;
        }
        if let Ok(bx) = parent.clone().downcast::<GtkBox>() {
            for w in current {
                bx.remove(&w);
            }
            for w in &target {
                bx.append(w);
            }
        }
    }

    /// Reconcile a navigation slot's pages against the app's current destination.
    /// The slot carries exactly one child — the current destination subtree —
    /// plus a `ROUTE` string and a `NAV_DEPTH` (U32) back-stack depth. The
    /// adapter reconciles the `AdwNavigationView` page stack **by depth**:
    ///
    /// - **deeper than the current stack** → `push` a new page for the route,
    /// - **shallower** → `pop` pages down to the target depth (back, or a
    ///   deep-link stack reset),
    /// - **same depth, new route** → **replace** the top page (a guard
    ///   redirect / `replace()`),
    /// - **same depth, same route** → refresh the top page's content in place
    ///   (a signal-driven update of the current destination).
    ///
    /// The back-stack stays app-owned; the `AdwNavigationView` is only the
    /// renderer's rendered-output cache of it. Programmatic pops here are
    /// guarded so they never re-emit as a back request.
    fn sync_navigation_children(&mut self, slot_id: u32, children: &[u32], node: &HostNode) {
        let Some(nav) = self.nav_views.get(&slot_id).cloned() else {
            return;
        };
        let Some(target) = children
            .first()
            .and_then(|id| self.widgets.get(id).cloned())
        else {
            return;
        };
        // Without a ROUTE the slot is not a navigation slot (the renderer would
        // not have promoted it). Depth defaults to 1.
        let route = node.string_property(property_id::ROUTE).unwrap_or_default();
        let depth = node.u32_property(property_id::NAV_DEPTH, 1).max(1) as usize;
        // Custom chrome mode: the developer owns all nav UI → no native chrome.
        let custom_chrome = is_custom_chrome(node);

        let stack = self.nav_pages.entry(slot_id).or_default().clone();
        self.attach_nav_back(slot_id, &nav, &stack);

        let mut pages = stack.borrow_mut();

        // Pop down to the app's depth (an app-driven pop or a stack reset). The
        // `popped` signal fires for each, so the reconciling flag suppresses it
        // (and we pop the mirror here, not in the handler).
        if let Some(flag) = self.nav_reconciling.get(&slot_id).cloned() {
            flag.set(true);
        }
        while pages.len() > depth {
            nav.pop();
            pages.pop();
        }
        if let Some(flag) = self.nav_reconciling.get(&slot_id).cloned() {
            flag.set(false);
        }

        if pages.is_empty() {
            // Root page: push the current destination.
            let page = nav_page(route, route, &target, custom_chrome);
            nav.push(&page);
            pages.push(page);
            return;
        }

        let top = pages.last().expect("non-empty").clone();
        match nav_action(pages.len(), depth, top.tag().as_deref(), route) {
            NavAction::Refresh => {
                // Same destination route → refresh content in place (signal
                // update, or a re-emit of the current destination on a
                // user-initiated back that already popped the page).
                top.set_child(Some(&target));
            }
            NavAction::Push => {
                // Deeper than the current stack → push a new page.
                let page = nav_page(route, route, &target, custom_chrome);
                nav.push(&page);
                pages.push(page);
            }
            NavAction::Replace => {
                // Same depth, different route → replace the top page (a guard
                // redirect / `replace()`, or a stack reset that kept this page).
                top.set_child(Some(&target));
                top.set_tag(Some(route));
                top.set_title(route);
            }
        }
    }

    /// Wire the native back affordance of a navigation slot once: when a page
    /// is popped (the header-bar back button, or a swipe/gesture), drop it from
    /// the adapter's page mirror and emit `Event::Navigate { url: None }` so the
    /// app pops its own back-stack and re-emits the previous destination.
    /// Suppressed while the renderer pops pages itself during reconcile.
    fn attach_nav_back(
        &mut self,
        slot_id: u32,
        nav: &NavigationView,
        stack: &Rc<RefCell<Vec<libadwaita::NavigationPage>>>,
    ) {
        if self.nav_reconciling.contains_key(&slot_id) {
            return;
        }
        let flag = Rc::new(Cell::new(false));
        self.nav_reconciling.insert(slot_id, flag.clone());
        let stack = stack.clone();
        if let Some(sink) = self.event_sink.clone() {
            nav.connect_popped(move |_nav, page| {
                if flag.get() {
                    return; // renderer-driven pop: the mirror is managed here
                }
                if let Ok(mut pages) = stack.try_borrow_mut() {
                    pages.retain(|p| p != page);
                }
                sink.borrow_mut()(Event::Navigate { url: None });
            });
        }
    }
}

/// The native widget kind a component type maps to (pure, headless-testable).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WidgetKind {
    /// `VSTACK`/`HSTACK`/`LAZY_VSTACK`/`LAZY_HSTACK` → `GtkBox`.
    Stack,
    /// `TEXT` → `GtkLabel`.
    Text,
    /// `BUTTON` → `GtkButton`.
    Button,
    /// `GRID`/`LAZY_VGRID`/`LAZY_HGRID` → `GtkGrid`.
    Grid,
    /// `SCROLLVIEW` → `GtkScrolledWindow`.
    ScrollView,
    /// `ZSTACK` → `GtkOverlay`.
    Overlay,
    /// `SPACER` → an expanding empty box.
    Spacer,
    /// `IMAGE` → `GtkPicture`.
    Image,
    /// `TOGGLE` → `GtkSwitch`/`GtkCheckButton`/`GtkToggleButton` (`TOGGLE_STYLE`).
    Toggle,
    /// `SLIDER` → `GtkScale`.
    Slider,
    /// `TEXT_FIELD` → `GtkEntry`.
    TextField,
    /// `TEXT_EDITOR` → `GtkTextView`.
    TextEditor,
    /// `COLOR` → `GtkDrawingArea` (solid fill).
    Color,
    /// `SHAPE` → `GtkDrawingArea` (`SHAPE_KIND`).
    Shape,
    /// `DIVIDER` → `GtkSeparator`.
    Divider,
    /// `PROGRESS_VIEW` → `GtkProgressBar`/`GtkSpinner`.
    ProgressView,
    /// `GAUGE` → `GtkLevelBar`.
    Gauge,
    /// `STEPPER` → `GtkSpinButton`.
    Stepper,
    /// `DATE_PICKER` → `GtkCalendar`.
    DatePicker,
    /// `PICKER` → `GtkDropDown` (options are children).
    Picker,
    /// `MENU` → `GtkMenuButton`.
    Menu,
    /// `COLOR_PICKER` → `GtkColorButton`.
    ColorPicker,
    /// Any component the renderer does not map yet → a blank `GtkLabel`.
    Blank,
}

/// Map a component type to its native widget kind.
pub fn widget_kind(component_type: u16) -> WidgetKind {
    match component_type {
        component_type::VSTACK
        | component_type::HSTACK
        | component_type::LAZY_VSTACK
        | component_type::LAZY_HSTACK => WidgetKind::Stack,
        component_type::TEXT => WidgetKind::Text,
        component_type::BUTTON => WidgetKind::Button,
        component_type::GRID
        | component_type::LAZY_VGRID
        | component_type::LAZY_HGRID => WidgetKind::Grid,
        component_type::SCROLLVIEW => WidgetKind::ScrollView,
        component_type::ZSTACK => WidgetKind::Overlay,
        component_type::SPACER => WidgetKind::Spacer,
        component_type::IMAGE => WidgetKind::Image,
        component_type::TOGGLE => WidgetKind::Toggle,
        component_type::SLIDER => WidgetKind::Slider,
        component_type::TEXT_FIELD => WidgetKind::TextField,
        component_type::TEXT_EDITOR => WidgetKind::TextEditor,
        component_type::COLOR => WidgetKind::Color,
        component_type::SHAPE => WidgetKind::Shape,
        component_type::DIVIDER => WidgetKind::Divider,
        component_type::PROGRESS_VIEW => WidgetKind::ProgressView,
        component_type::GAUGE => WidgetKind::Gauge,
        component_type::STEPPER => WidgetKind::Stepper,
        component_type::DATE_PICKER => WidgetKind::DatePicker,
        component_type::PICKER => WidgetKind::Picker,
        component_type::MENU => WidgetKind::Menu,
        component_type::COLOR_PICKER => WidgetKind::ColorPicker,
        _ => WidgetKind::Blank,
    }
}

/// Whether a node is a navigation slot: a stack container carrying the `ROUTE`
/// property (a `NavigationContainer`, spec/DSL.md §4.5). Such nodes render as
/// the platform's native navigation container (`AdwNavigationView`) instead of
/// an in-place `GtkBox` child swap.
fn is_nav_slot(node: &HostNode) -> bool {
    matches!(
        node.component_type,
        component_type::VSTACK | component_type::HSTACK
    ) && node.string_property(property_id::ROUTE).is_some()
}

/// Whether a navigation slot is in `Custom` chrome mode (`NAV_CHROME` 0x201B
/// enum: `PlatformDefault`=0, `Custom`=1; missing = `PlatformDefault`): the
/// developer owns all navigation UI, so the renderer adds no chrome (no native
/// header-bar back button).
fn is_custom_chrome(node: &HostNode) -> bool {
    node.f32_property(property_id::NAV_CHROME, 0.0).round() as u8 == 1
}

/// The native page action for the current destination, decided by depth
/// (spec DSL.md §4.5): a `NavigationContainer` emits `ROUTE` + `NAV_DEPTH` on
/// its slot, and the adapter reconciles its page stack by depth.
///
/// - `Refresh` — the visible page already shows this route: update its content
///   in place (a signal-driven update, or a re-emit after a user-initiated
///   back that already popped the page).
/// - `Push` — deeper than the current stack: push a new page (a normal push, or
///   a single-step deep-link landing deeper).
/// - `Replace` — same depth, different route: replace the top page (a guard
///   redirect / `replace()`, or a stack reset that kept this page).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum NavAction {
    Refresh,
    Push,
    Replace,
}

/// The pure depth-vs-stack decision (headless-testable; see
/// [`GtkRenderer::sync_navigation_children`] for the pop-down step).
fn nav_action(stack_len: usize, depth: usize, top_tag: Option<&str>, route: &str) -> NavAction {
    if top_tag == Some(route) {
        NavAction::Refresh
    } else if stack_len < depth {
        NavAction::Push
    } else {
        NavAction::Replace
    }
}

/// Whether a component type reconciles native children (a container).
fn is_container(component_type: u16) -> bool {
    matches!(
        component_type,
        component_type::VSTACK
            | component_type::HSTACK
            | component_type::LAZY_VSTACK
            | component_type::LAZY_HSTACK
            | component_type::GRID
            | component_type::LAZY_VGRID
            | component_type::LAZY_HGRID
            | component_type::SCROLLVIEW
            | component_type::ZSTACK
    )
}

/// Whether a semantic control has a custom body (Composite Override Mode):
/// children present → the control renders the child tree wrapped in its native
/// shell instead of its default chrome.
fn is_composite(node: &HostNode) -> bool {
    !node.children.is_empty()
        && matches!(
            node.component_type,
            component_type::BUTTON | component_type::TOGGLE | component_type::MENU
        )
}

/// The current child widgets of a native widget, in child/draw order.
fn child_widgets(widget: &gtk::Widget) -> Vec<gtk::Widget> {
    let mut out = Vec::new();
    let mut c = widget.first_child();
    while let Some(w) = c {
        out.push(w.clone());
        c = w.next_sibling();
    }
    out
}

/// Whether a grid's children already sit at their target cell positions.
fn grid_matches(
    grid: &gtk::Grid,
    children: &[u32],
    columns: Option<u32>,
    widgets: &HashMap<u32, gtk::Widget>,
) -> bool {
    if child_widgets(&grid.clone().upcast()).len() != children.len() {
        return false;
    }
    for (idx, child_id) in children.iter().enumerate() {
        let Some(w) = widgets.get(child_id) else {
            return false;
        };
        let (row, col) = layout::grid_cell_position(idx as u32, columns);
        if grid.child_at(col as i32, row as i32).as_ref() != Some(w) {
            return false;
        }
    }
    true
}

/// Build the base native widget for a node (no children attached, no input
/// controllers — those are attached later in [`GtkRenderer::update`]).
fn build_widget(node: &HostNode) -> gtk::Widget {
    // A navigation slot renders as the native navigation container.
    if is_nav_slot(node) {
        return NavigationView::new().upcast();
    }
    match widget_kind(node.component_type) {
        WidgetKind::Stack => stack_widget(node),
        WidgetKind::Text => Label::new(Some(node.text.as_deref().unwrap_or(""))).upcast(),
        WidgetKind::Button => Button::with_label(node.text.as_deref().unwrap_or("")).upcast(),
        WidgetKind::Grid => {
            let grid = gtk::Grid::new();
            grid.set_halign(Align::Fill);
            grid.set_valign(Align::Fill);
            grid.upcast()
        }
        WidgetKind::ScrollView => {
            let sw = gtk::ScrolledWindow::new();
            sw.set_halign(Align::Fill);
            sw.set_valign(Align::Fill);
            sw.set_policy(gtk::PolicyType::Automatic, gtk::PolicyType::Automatic);
            sw.upcast()
        }
        WidgetKind::Overlay => gtk::Overlay::new().upcast(),
        WidgetKind::Spacer => {
            let bx = GtkBox::new(gtk::Orientation::Vertical, 0);
            bx.set_hexpand(true);
            bx.set_vexpand(true);
            bx.upcast()
        }
        WidgetKind::Image => gtk::Picture::new().upcast(),
        WidgetKind::Toggle => toggle_widget(node),
        WidgetKind::Slider => {
            let min = f64::from(node.f32_property(property_id::MIN_VALUE, 0.0));
            let max = f64::from(node.f32_property(property_id::MAX_VALUE, 1.0));
            // GTK requires a non-zero step (`gtk_scale_new_with_range` asserts it);
            // a STEP_VALUE of 0 means "continuous" in the DSL, so derive a small
            // increment instead of panicking.
            let step = f64::from(node.f32_property(property_id::STEP_VALUE, 0.0));
            let step = if step > 0.0 { step } else { ((max - min) / 100.0).max(0.001) };
            let scale = gtk::Scale::with_range(gtk::Orientation::Horizontal, min, max, step);
            scale.set_draw_value(false);
            scale.set_hexpand(true);
            scale.upcast()
        }
        WidgetKind::TextField => {
            let entry = gtk::Entry::new();
            if node.f32_property(property_id::IS_SECURE, 0.0) != 0.0 {
                entry.set_visibility(false);
            }
            entry.set_placeholder_text(Some(node.string_property(property_id::PROMPT).unwrap_or("")));
            entry.upcast()
        }
        WidgetKind::TextEditor => gtk::TextView::new().upcast(),
        WidgetKind::Color => color_widget(node),
        WidgetKind::Shape => shape_widget(node),
        WidgetKind::Divider => gtk::Separator::new(gtk::Orientation::Horizontal).upcast(),
        WidgetKind::ProgressView => progress_widget(node),
        WidgetKind::Gauge => {
            let level = gtk::LevelBar::new();
            level.set_min_value(f64::from(node.f32_property(property_id::MIN_VALUE, 0.0)));
            level.set_max_value(f64::from(node.f32_property(property_id::MAX_VALUE, 1.0)));
            level.set_value(f64::from(node.f32_property(property_id::VALUE, 0.0)));
            level.upcast()
        }
        WidgetKind::Stepper => {
            let min = f64::from(node.f32_property(property_id::MIN_VALUE, 0.0));
            let max = f64::from(node.f32_property(property_id::MAX_VALUE, 100.0));
            let step = f64::from(node.f32_property(property_id::STEP_VALUE, 1.0));
            let spin = gtk::SpinButton::with_range(min, max, step);
            spin.set_value(f64::from(node.f32_property(property_id::VALUE, min as f32)));
            spin.upcast()
        }
        WidgetKind::DatePicker => gtk::Calendar::new().upcast(),
        WidgetKind::Picker => picker_widget(node),
        WidgetKind::Menu => {
            let menu = gtk::MenuButton::new();
            menu.set_label(node.text.as_deref().unwrap_or(""));
            menu.upcast()
        }
        WidgetKind::ColorPicker => {
            let btn = gtk::ColorButton::new();
            btn.set_rgba(&color_rgba(node.u32_property(property_id::COLOR_VALUE, 0xFF00_0000)));
            btn.upcast()
        }
        WidgetKind::Blank => Label::new(Some("")).upcast(),
    }
}

/// Build a `TOGGLE` widget from its `TOGGLE_STYLE` token (`Switch`=0,
/// `Checkbox`=1, `Button`=2).
fn toggle_widget(node: &HostNode) -> gtk::Widget {
    match node.f32_property(property_id::TOGGLE_STYLE, 0.0).round() as u8 {
        1 => {
            let cb = gtk::CheckButton::new();
            cb.set_active(node.checked());
            cb.upcast()
        }
        2 => {
            let tb = gtk::ToggleButton::new();
            tb.set_active(node.checked());
            tb.upcast()
        }
        _ => {
            let sw = gtk::Switch::new();
            sw.set_active(node.checked());
            sw.upcast()
        }
    }
}

/// Build a `COLOR` widget: a `GtkDrawingArea` filled with the `COLOR` value.
fn color_widget(node: &HostNode) -> gtk::Widget {
    let (r, g, b, a) = unpack_color(node.u32_property(property_id::COLOR, 0xFF00_0000));
    let area = gtk::DrawingArea::new();
    area.set_draw_func(move |_, cr, w, h| {
        cr.set_source_rgba(r, g, b, a);
        cr.rectangle(0.0, 0.0, w as f64, h as f64);
        let _ = cr.fill();
    });
    area.set_hexpand(true);
    area.set_vexpand(true);
    area.upcast()
}

/// Build a `SHAPE` widget: a `GtkDrawingArea` filling the `SHAPE_KIND` geometry
/// with the fill color.
fn shape_widget(node: &HostNode) -> gtk::Widget {
    let kind = node.f32_property(property_id::SHAPE_KIND, 0.0).round() as u8;
    let radius = f64::from(node.f32_property(property_id::BORDER_RADIUS, 8.0));
    let (r, g, b, a) = unpack_color(node.u32_property(property_id::COLOR, 0xFF00_0000));
    let area = gtk::DrawingArea::new();
    area.set_draw_func(move |_, cr, w, h| {
        let (w, h) = (w as f64, h as f64);
        cr.set_source_rgba(r, g, b, a);
        match kind {
            0 | 4 => {
                cr.arc(w / 2.0, h / 2.0, w.min(h) / 2.0, 0.0, std::f64::consts::TAU);
                let _ = cr.fill();
            }
            3 => {
                let rad = w.min(h) / 2.0;
                let _ = rad;
                cr.rectangle(0.0, 0.0, w, h);
                let _ = cr.fill();
            }
            2 => {
                let _ = radius;
                cr.rectangle(0.0, 0.0, w, h);
                let _ = cr.fill();
            }
            _ => {
                cr.rectangle(0.0, 0.0, w, h);
                let _ = cr.fill();
            }
        }
    });
    area.set_hexpand(true);
    area.set_vexpand(true);
    area.upcast()
}

/// Build a `PROGRESS_VIEW`: determinate → `GtkProgressBar`, indeterminate →
/// `GtkSpinner`.
fn progress_widget(node: &HostNode) -> gtk::Widget {
    let indeterminate = node.f32_property(property_id::IS_INDETERMINATE, 0.0) != 0.0
        || node.f32_property(property_id::PROGRESS, -1.0) < 0.0;
    if indeterminate {
        let spinner = gtk::Spinner::new();
        spinner.start();
        spinner.upcast()
    } else {
        let bar = gtk::ProgressBar::new();
        bar.set_fraction(f64::from(node.f32_property(property_id::PROGRESS, 0.0)));
        bar.upcast()
    }
}

/// Build a `PICKER` widget: a `GtkDropDown` whose options are the child texts.
fn picker_widget(node: &HostNode) -> gtk::Widget {
    let _ = node;
    let dropdown = gtk::DropDown::from_strings(&[]);
    dropdown.set_hexpand(true);
    dropdown.upcast()
}

/// A `0xAARRGGBB` color as a `gdk::RGBA`.
fn color_rgba(argb: u32) -> gtk::gdk::RGBA {
    let (r, g, b, a) = unpack_color(argb);
    gtk::gdk::RGBA::new(r as f32, g as f32, b as f32, a as f32)
}

/// Unpack a `0xAARRGGBB` color into `(r, g, b, alpha)` where `alpha` is `0..1`.
fn unpack_color(argb: u32) -> (f64, f64, f64, f64) {
    (
        f64::from((argb >> 16) & 0xFF) / 255.0,
        f64::from((argb >> 8) & 0xFF) / 255.0,
        f64::from(argb & 0xFF) / 255.0,
        f64::from((argb >> 24) & 0xFF) / 255.0,
    )
}

/// The listener bitmask a node wants: buttons listen to pointer down/move/up by
/// default; any node can request additional (or different) raw events via the
/// `EVENT_LISTENERS` property.
fn desired_listeners(node: &HostNode) -> u32 {
    let mut mask = 0u32;
    if node.component_type == component_type::BUTTON {
        mask |= listener::POINTER_DOWN | listener::POINTER_MOVE | listener::POINTER_UP;
    }
    if let Some(m) = node.properties.get(&property_id::EVENT_LISTENERS) {
        mask |= *m;
    }
    mask
}

/// Attach a `GestureClick` to `widget`, reporting raw `POINTER_DOWN` /
/// `POINTER_UP` events for `target`. Uses the capture phase so it observes both
/// press and release even on a widget (like a button) whose own internal
/// gesture claims the click sequence.
fn attach_pointer_gesture(
    widget: &gtk::Widget,
    target: u32,
    sink: Rc<RefCell<dyn FnMut(Event)>>,
    want_down: bool,
    want_up: bool,
) {
    let gesture = GestureClick::new();
    gesture.set_propagation_phase(PropagationPhase::Capture);
    if want_down {
        let down_sink = sink.clone();
        gesture.connect_pressed(move |_, _n, x, y| {
            down_sink.borrow_mut()(Event::PointerDown {
                target,
                x: x as f32,
                y: y as f32,
                secondary: false,
            });
        });
    }
    if want_up {
        let up_sink = sink.clone();
        gesture.connect_released(move |_, _n, x, y| {
            up_sink.borrow_mut()(Event::PointerUp {
                target,
                x: x as f32,
                y: y as f32,
                secondary: false,
            });
        });
    }
    widget.add_controller(gesture);
}

/// Attach an `EventControllerMotion` to `widget`, reporting raw `POINTER_MOVE`
/// events (hover enter/move/leave) for `target`.
fn attach_motion(widget: &gtk::Widget, target: u32, sink: Rc<RefCell<dyn FnMut(Event)>>) {
    let motion = EventControllerMotion::new();
    let move_sink = sink.clone();
    motion.connect_motion(move |_motion, x, y| {
        move_sink.borrow_mut()(Event::PointerMove {
            target,
            x: x as f32,
            y: y as f32,
            hovering: true,
            leaving: false,
        });
    });
    let enter_sink = sink.clone();
    motion.connect_enter(move |_motion, x, y| {
        enter_sink.borrow_mut()(Event::PointerMove {
            target,
            x: x as f32,
            y: y as f32,
            hovering: true,
            leaving: false,
        });
    });
    let leave_sink = sink.clone();
    motion.connect_leave(move |_motion| {
        leave_sink.borrow_mut()(Event::PointerMove {
            target,
            x: 0.0,
            y: 0.0,
            hovering: false,
            leaving: true,
        });
    });
    widget.add_controller(motion);
}

/// Build a stack's native `GtkBox` from its mapped layout (orientation +
/// spacing). Padding is applied later, in `update`, so the spacing here is the
/// only stack-specific layout decision made at creation time.
fn stack_widget(node: &HostNode) -> gtk::Widget {
    let layout = layout::stack_layout(node).expect("stack component");
    let bx = GtkBox::new(layout.orientation, layout.spacing);
    bx.set_halign(Align::Fill);
    bx.set_valign(Align::Fill);
    bx.upcast()
}

/// Build an `AdwNavigationPage` for a navigation slot. In the default chrome
/// mode the destination widget is wrapped in a `AdwToolbarView` with an
/// `AdwHeaderBar`, so the page gets the native top bar and the back button
/// appears once the page is not the root. In `Custom` chrome mode
/// (`NAV_CHROME`=1, `is_custom_chrome`) the destination is the bare page child
/// — the developer owns all navigation UI, so no native chrome is added. The
/// page's tag is the `ROUTE` path — pages are reconciled by depth and tag, and
/// the native back button's pop (via `popped`) surfaces as a `NAVIGATE` request.
fn nav_page(route: &str, title: &str, child: &gtk::Widget, custom_chrome: bool) -> libadwaita::NavigationPage {
    if custom_chrome {
        return libadwaita::NavigationPage::builder()
            .title(title)
            .tag(route)
            .child(child)
            .build();
    }
    let toolbar = libadwaita::ToolbarView::new();
    let header = libadwaita::HeaderBar::new();
    header.set_show_back_button(true);
    // No window controls in the page header — the app's own content provides
    // them; only the navigation back button is native chrome.
    header.set_show_start_title_buttons(false);
    header.set_show_end_title_buttons(false);
    toolbar.add_top_bar(&header);
    toolbar.set_content(Some(child));
    libadwaita::NavigationPage::builder()
        .title(title)
        .tag(route)
        .child(&toolbar)
        .build()
}

/// Apply a node's padding as widget margins (styling, any widget type).
fn apply_padding(widget: &gtk::Widget, node: &HostNode) {
    let e = layout::padding_from(node);
    if e.is_zero() {
        return;
    }
    widget.set_margin_top(e.top);
    widget.set_margin_end(e.right);
    widget.set_margin_bottom(e.bottom);
    widget.set_margin_start(e.left);
}

/// How a `WIDTH`/`HEIGHT` property maps to GTK sizing.
#[derive(Debug, Clone, Copy, PartialEq)]
enum SizeHint {
    /// No hint: leave the widget's natural size and default expand state.
    None,
    /// Expand to the available space (`size::FILL`, i.e. SwiftUI `.infinity`).
    Fill,
    /// A fixed size request.
    Fixed(i32),
}

/// Map a stored `WIDTH`/`HEIGHT` property (bit pattern) to a sizing hint.
/// `FILL` (-1.0) → `Fill`; a positive finite value → `Fixed`; `HUG_CONTENT`
/// (-2.0), zero, and non-finite (defensive: the ring path never normalizes) →
/// `None`.
fn size_hint(bits: Option<u32>) -> SizeHint {
    match bits.map(f32::from_bits) {
        None => SizeHint::None,
        Some(v) if v == size::FILL => SizeHint::Fill,
        Some(v) if v.is_finite() && v > 0.0 => SizeHint::Fixed(v as i32),
        Some(_) => SizeHint::None,
    }
}

/// Apply the directly-mappable style properties to any widget: visibility,
/// opacity, size hints, content margins, plus CSS-backed decoration
/// (background, border, font family/weight).
fn apply_style(widget: &gtk::Widget, node: &HostNode) {
    if let Some(bits) = node.properties.get(&property_id::VISIBLE) {
        widget.set_visible(*bits != 0);
    }
    if let Some(bits) = node.properties.get(&property_id::OPACITY) {
        widget.set_opacity(f64::from(f32::from_bits(*bits)));
    }
    let w = node.properties.get(&property_id::WIDTH).copied();
    let h = node.properties.get(&property_id::HEIGHT).copied();
    let sw = size_hint(w);
    let sh = size_hint(h);
    // FILL (-1.0) means "expand to available space": grow on the axis and align
    // the widget into it (SwiftUI `maxWidth/maxHeight: .infinity`). A finite
    // value becomes a size request; HUG_CONTENT / absence leaves the natural size.
    // A present-but-not-FILL axis resets a previous expansion so updates are
    // idempotent; an absent axis is left untouched (preserves widget defaults
    // such as Spacer's built-in expand).
    if let SizeHint::Fill = sw {
        widget.set_hexpand(true);
        widget.set_halign(Align::Fill);
    } else if w.is_some() {
        widget.set_hexpand(false);
    }
    if let SizeHint::Fill = sh {
        widget.set_vexpand(true);
        widget.set_valign(Align::Fill);
    } else if h.is_some() {
        widget.set_vexpand(false);
    }
    if w.is_some() || h.is_some() {
        widget.set_size_request(
            match sw {
                SizeHint::Fixed(v) => v,
                _ => -1,
            },
            match sh {
                SizeHint::Fixed(v) => v,
                _ => -1,
            },
        );
    }
    if let Some(bits) = node.properties.get(&property_id::CONTENT_MARGINS) {
        let m = f32::from_bits(*bits) as i32;
        widget.set_margin_top(m);
        widget.set_margin_end(m);
        widget.set_margin_bottom(m);
        widget.set_margin_start(m);
    }

    let css = style_css(node);
    if !css.is_empty() {
        widget.set_widget_name(&format!("pathland-{}", node.id));
        let provider = gtk::CssProvider::new();
        let _ = provider.load_from_data(&format!("#pathland-{} {{ {css} }}", node.id));
        widget
            .style_context()
            .add_provider(&provider, gtk::STYLE_PROVIDER_PRIORITY_APPLICATION);
    }
}

/// CSS decoration (background, border, font family) for a node, keyed by its
/// widget name (set in [`apply_style`]).
fn style_css(node: &HostNode) -> String {
    let mut css = String::new();
    if let Some(bits) = node.properties.get(&property_id::BACKGROUND_COLOR) {
        let (r, g, b, a) = unpack_color(*bits);
        css.push_str(&format!(
            "background-color:rgba({:.0},{:.0},{:.0},{:.3});",
            r * 255.0,
            g * 255.0,
            b * 255.0,
            a
        ));
    }
    if let Some(bits) = node.properties.get(&property_id::BORDER_WIDTH) {
        let w = f32::from_bits(*bits);
        let (r, g, b, a) = unpack_color(node.u32_property(property_id::BORDER_COLOR, 0xFF00_0000));
        css.push_str(&format!(
            "border:{w}px solid rgba({:.0},{:.0},{:.0},{:.3});",
            r * 255.0,
            g * 255.0,
            b * 255.0,
            a
        ));
    }
    if let Some(bits) = node.properties.get(&property_id::BORDER_RADIUS) {
        let r = f32::from_bits(*bits);
        if r != 0.0 {
            css.push_str(&format!("border-radius:{r}px;"));
        }
    }
    if let Some(family) = node.string_property(property_id::FONT_FAMILY) {
        css.push_str(&format!("font-family:'{}';", family.replace('\'', "\\'")));
    }
    if let Some(bits) = node.properties.get(&property_id::FONT_WEIGHT) {
        let w = f32::from_bits(*bits);
        if w > 0.0 {
            css.push_str(&format!("font-weight:{w};"));
        }
    }
    css
}

/// Apply color/font-size/font-weight text style from properties via Pango
/// attributes.
fn apply_text_style(label: &Label, node: &HostNode) {
    let attrs = pango::AttrList::new();
    if let Some(fs) = node.properties.get(&property_id::FONT_SIZE) {
        let pts = (f32::from_bits(*fs).max(0.0) as i32) * 1000;
        attrs.insert(pango::AttrSize::new(pts));
    }
    if let Some(color) = node.properties.get(&property_id::COLOR) {
        let c = *color;
        let red = (((c >> 16) & 0xFF) as u16) * 257;
        let green = (((c >> 8) & 0xFF) as u16) * 257;
        let blue = ((c & 0xFF) as u16) * 257;
        let mut fg = pango::AttrColor::new_foreground(red, green, blue);
        fg.set_start_index(0);
        fg.set_end_index(u32::MAX);
        attrs.insert(fg);
    }
    label.set_attributes(Some(&attrs));
}

/// Run the GTK renderer over a shared ring, pumping frames in and waking the
/// host on raw-input events.
///
/// `on_event` is invoked (on the GTK main thread) with the shared ring whenever
/// the renderer wrote a raw input; the app drains the ring's event queue itself
/// (`RingTransport::drain_events`) and may re-emit a new frame.
///
/// Passes the process's real program args to GTK (a native Rust binary's args
/// are just its program name — safe for GTK to parse).
pub fn run<F>(app_id: &str, title: &str, ring: Rc<RefCell<RingTransport>>, on_event: F)
where
    F: FnMut(Rc<RefCell<RingTransport>>) + 'static,
{
    let args: Vec<String> = std::env::args().collect();
    let args: Vec<&str> = args.iter().map(|s| s.as_str()).collect();
    run_with_pump(app_id, title, ring, &args, on_event);
}

/// Generic [`run`] over any [`Pump`] (e.g. a `pathland-view-native` host handle).
///
/// `args` is passed verbatim to `g_application_run`. Embedding hosts must pass
/// an argv that is NOT their own process command line when that command line
/// carries foreign options (e.g. the JVM's `-cp`/`-D` flags).
pub fn run_with_pump<P, F>(
    app_id: &str,
    title: &str,
    pump: Rc<RefCell<P>>,
    args: &[&str],
    on_event: F,
) where
    P: Pump + 'static,
    F: FnMut(Rc<RefCell<P>>) + 'static,
{
    run_with_pump_sized(app_id, title, pump, args, on_event, 420, 220);
}

/// [`run_with_pump`] with an explicit default window size (embedding hosts that
/// know the UI needs more room, e.g. a master-detail layout, pass their own).
pub fn run_with_pump_sized<P, F>(
    app_id: &str,
    title: &str,
    pump: Rc<RefCell<P>>,
    args: &[&str],
    on_event: F,
    default_width: i32,
    default_height: i32,
) where
    P: Pump + 'static,
    F: FnMut(Rc<RefCell<P>>) + 'static,
{
    let app_id = app_id.to_string();
    let title = title.to_string();
    let on_event = Rc::new(RefCell::new(on_event));

    let app = gtk::Application::builder().application_id(&app_id).build();

    app.connect_activate(move |app| {
        let window = gtk::ApplicationWindow::builder()
            .application(app)
            .title(&title)
            .default_width(default_width)
            .default_height(default_height)
            .build();

        let renderer = Rc::new(RefCell::new(GtkRenderer::new()));

        // Color-scheme detection (spec/TOKENS.md): the renderer derives the
        // effective scheme from the native GTK variant and re-resolves design
        // tokens when it changes. Scheme is NEVER carried by the protocol.
        if let Some(settings) = gtk::Settings::default() {
            let renderer_for_scheme = renderer.clone();
            settings.connect_gtk_application_prefer_dark_theme_notify(move |settings| {
                renderer_for_scheme
                    .borrow_mut()
                    .set_scheme(tokens::scheme_from_settings(settings));
            });
            renderer
                .borrow_mut()
                .set_scheme(tokens::scheme_from_settings(&settings));
        }

        // Native inputs are written as EVENT opcodes into the pump (host → guest);
        // the host is then woken to drain the event ring itself.
        //
        // `emit` is the shared path for every raw input the renderer reports —
        // pointer/control events from the sink below, and platform back /
        // Escape-key navigation from the window-level key controller.
        //
        // A frame apply borrows the pump zero-copy (the batch aliases the shared
        // ring). Applying a value-bearing control's *programmatic* value
        // (e.g. `GtkScale::set_value`) fires its connected GTK signal
        // synchronously, re-entering `emit` while the pump is borrowed. Such
        // events are deferred to the idle pump (flushed before the next frame),
        // which also avoids a control echo loop when the host writes the same
        // value back.
        let pending: Rc<RefCell<Vec<Event>>> = Rc::new(RefCell::new(Vec::new()));
        let emit = {
            let pump = pump.clone();
            let wake = on_event.clone();
            let pending = pending.clone();
            move |ev: Event| match pump.try_borrow_mut() {
                Ok(mut p) => {
                    let _ = p.send_input(&ev);
                    drop(p);
                    wake.borrow_mut()(pump.clone());
                }
                Err(_) => {
                    pending.borrow_mut().push(ev);
                }
            }
        };
        {
            let emit_for_sink = emit.clone();
            let sink: Rc<RefCell<dyn FnMut(Event)>> =
                Rc::new(RefCell::new(move |ev: Event| emit_for_sink(ev)));
            renderer.borrow_mut().set_event_sink(sink);

            // Platform back: Escape (and BackSpace when no text entry has focus)
            // is a native back request → `Event::Navigate { url: None }`. NAVIGATE
            // is global (never node-keyed), so it is attached once at the window
            // level, in the capture phase so it fires even when a child widget
            // holds focus. BackSpace is only mapped when the focused widget is
            // not a text input, so it never swallows text editing.
            let emit_for_key = emit.clone();
            let window_for_key = window.clone();
            let key = EventControllerKey::new();
            key.set_propagation_phase(PropagationPhase::Capture);
            key.connect_key_pressed(move |_key, keyval, _code, _state| {
                let keyval = keyval.into_glib();
                let is_escape = keyval == gtk::gdk::ffi::GDK_KEY_Escape as u32;
                let is_backspace = keyval == gtk::gdk::ffi::GDK_KEY_BackSpace as u32
                    && !window_has_text_focus(&window_for_key);
                if is_escape || is_backspace {
                    emit_for_key(Event::Navigate { url: None });
                    glib::Propagation::Stop
                } else {
                    glib::Propagation::Proceed
                }
            });
            window.add_controller(key);
        }

        // Initial render + window root. Deferred control signals from the mount
        // apply are flushed before the idle pump takes over.
        pump_once(&renderer, &pump);
        flush_pending(&pump, &pending, &on_event);
        let root = renderer.borrow().root_widget();
        if let Some(root) = root {
            window.set_child(Some(&root));
        }
        window.present();

        // Idle pump: flush any deferred (re-entrant) events, then apply pending
        // frames to the renderer. Events are drained by the host (woken from the
        // sink above), not here.
        let idle_renderer = renderer.clone();
        let idle_pump = pump.clone();
        let idle_pending = pending.clone();
        let idle_wake = on_event.clone();
        glib::idle_add_local(move || {
            flush_pending(&idle_pump, &idle_pending, &idle_wake);
            pump_once(&idle_renderer, &idle_pump);
            glib::ControlFlow::Continue
        });
    });

    // Run the GTK main loop with the caller-provided argv.
    app.run_with_args(args);
}

/// Whether a text input currently holds keyboard focus, so backspace is not
/// misread as a platform-back navigation while the user is editing text.
fn window_has_text_focus(window: &impl IsA<gtk::Window>) -> bool {
    let focused: Option<gtk::Widget> = window
        .upcast_ref::<gtk::Window>()
        .property::<Option<gtk::Widget>>("focus-widget");
    focused.map_or(false, |w| w.is::<gtk::Entry>() || w.is::<gtk::TextView>())
}

/// Flush events deferred during a frame apply (re-entrant control signals) into
/// the pump and wake the host. Runs between frames, so the pump is free.
fn flush_pending<P, F>(
    pump: &Rc<RefCell<P>>,
    pending: &Rc<RefCell<Vec<Event>>>,
    wake: &Rc<RefCell<F>>,
) where
    P: Pump,
    F: FnMut(Rc<RefCell<P>>),
{
    if pending.borrow().is_empty() {
        return;
    }
    let events = std::mem::take(&mut *pending.borrow_mut());
    let mut p = pump.borrow_mut();
    for ev in events {
        let _ = p.send_input(&ev);
    }
    drop(p);
    wake.borrow_mut()(pump.clone());
}

/// Apply pending frames to the renderer. (Events flow the other way: the
/// renderer writes them; the host drains them — see [`Pump`].)
fn pump_once<P>(renderer: &Rc<RefCell<GtkRenderer>>, pump: &Rc<RefCell<P>>)
where
    P: Pump,
{
    loop {
        let mut pump_ref = pump.borrow_mut();
        match pump_ref.next_frame() {
            Ok(Some(batch)) => {
                let frame = batch.frame();
                renderer.borrow_mut().apply_frame(frame);
            }
            _ => break,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use pathland_engine::Engine;
    use pathland_core::tokens::Scheme;
    use pathland_core::{init_memory, property_id, value_type, Guest, Host, MemoryLayout};
    use pathland_view::{assign_ids, text, vstack, View, ViewExt};

    #[test]
    fn size_hint_maps_width_height_sentinels() {
        assert_eq!(size_hint(None), SizeHint::None);
        assert_eq!(size_hint(Some(size::FILL.to_bits())), SizeHint::Fill);
        assert_eq!(size_hint(Some(24.0f32.to_bits())), SizeHint::Fixed(24));
        // HUG_CONTENT (-2.0), zero, and non-finite values are natural size.
        assert_eq!(size_hint(Some(size::HUG_CONTENT.to_bits())), SizeHint::None);
        assert_eq!(size_hint(Some(0.0f32.to_bits())), SizeHint::None);
        assert_eq!(size_hint(Some(f32::INFINITY.to_bits())), SizeHint::None);
    }

    #[test]
    fn render_tree_decodes_a_frame() {
        // Build a tree, emit into a ring, decode via RenderTree (the renderer's
        // underlying store) without touching GTK.
        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        let mut root = vstack![text("ab"), text("cd")]
            .spacing(4.0)
            .build();
        assign_ids(&mut root, &mut 1);

        let mut engine = Engine::new();
        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            engine.emit(&root, &mut guest).unwrap();
            guest.end_frame();
        }

        let mut host = pathland_core::Host::new(&mut mem, &layout);
        let frames = host.frames();
        let mut tree = RenderTree::default();
        tree.apply_frame(&frames[0]);
        assert_eq!(tree.nodes.len(), 3);
        assert_eq!(tree.node(1).unwrap().component_type, component_type::VSTACK);
    }

    #[test]
    fn design_token_property_ref_resolves_against_concrete_defaults() {
        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        let mut root = text("x")
            .foreground_style(pathland_view::Color::token("color.primary"))
            .build();
        assign_ids(&mut root, &mut 1);

        let mut engine = Engine::new();
        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            engine.emit(&root, &mut guest).unwrap();
            guest.end_frame();
        }

        let mut host = Host::new(&mut mem, &layout);
        let mut tree = RenderTree::default();
        tree.apply_frame(&host.frames()[0]);
        assert_eq!(
            tree.node(1).unwrap().properties.get(&property_id::COLOR).copied(),
            Some(0xFF_2563EB),
            "color.primary concrete light default"
        );
    }

    #[test]
    fn design_token_override_and_scheme_change_re_resolve() {
        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        let mut root = text("x")
            .foreground_style(pathland_view::Color::token("color.primary"))
            .build();
        assign_ids(&mut root, &mut 1);
        let mut engine = Engine::new();
        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            engine.emit(&root, &mut guest).unwrap();
            guest.end_frame();
        }
        let mut host = Host::new(&mut mem, &layout);
        let mut tree = RenderTree::default();
        tree.apply_frame(&host.frames()[0]);

        // Base override wins (light).
        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            guest
                .set_design_token("color.primary", value_type::COLOR, 0xFF_123456)
                .unwrap();
            guest.end_frame();
            let mut host = Host::new(&mut mem, &layout);
            tree.apply_frame(&host.frames()[0]);
        }
        assert_eq!(
            tree.node(1).unwrap().properties.get(&property_id::COLOR).copied(),
            Some(0xFF_123456),
            "base override wins (light)"
        );

        // Dark scheme: the base override falls through when the dark layer is empty.
        tree.set_scheme(Scheme::Dark);
        assert_eq!(
            tree.node(1).unwrap().properties.get(&property_id::COLOR).copied(),
            Some(0xFF_123456),
            "base override falls through when the dark layer is empty"
        );

        // A dark.* override wins under the dark scheme.
        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            guest
                .set_design_token("dark.color.primary", value_type::COLOR, 0xFF_ABCDEF)
                .unwrap();
            guest.end_frame();
            let mut host = Host::new(&mut mem, &layout);
            tree.apply_frame(&host.frames()[0]);
        }
        assert_eq!(
            tree.node(1).unwrap().properties.get(&property_id::COLOR).copied(),
            Some(0xFF_ABCDEF),
            "dark.* override wins under dark"
        );

        // Back to light: the base override wins again.
        tree.set_scheme(Scheme::Light);
        assert_eq!(
            tree.node(1).unwrap().properties.get(&property_id::COLOR).copied(),
            Some(0xFF_123456),
            "base override wins again under light"
        );
    }

    #[test]
    fn generative_space_ref_resolves_against_base() {
        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);
        let mut guest = Guest::new(&mut mem, &layout);
        guest.begin_frame();
        guest.create_node(1, component_type::VSTACK).unwrap();
        let path_ref = guest.alloc_str("space.2").unwrap();
        guest
            .set_property(1, property_id::SPACING, value_type::DESIGN_TOKEN, path_ref)
            .unwrap();
        guest.end_frame();

        let mut host = Host::new(&mut mem, &layout);
        let mut tree = RenderTree::default();
        tree.apply_frame(&host.frames()[0]);
        let bits = tree
            .node(1)
            .unwrap()
            .properties
            .get(&property_id::SPACING)
            .copied()
            .unwrap();
        assert_eq!(f32::from_bits(bits), 8.0, "space.base (4.0) × 2");
    }

    #[test]
    fn renderer_placeholder_compiles() {
        // Ensures the renderer type is constructible without a display.
        let _r = GtkRenderer::new();
    }

    #[test]
    fn widget_kind_maps_grouped_components() {
        use component_type::*;
        assert_eq!(widget_kind(VSTACK), WidgetKind::Stack);
        assert_eq!(widget_kind(HSTACK), WidgetKind::Stack);
        assert_eq!(widget_kind(LAZY_VSTACK), WidgetKind::Stack);
        assert_eq!(widget_kind(LAZY_HSTACK), WidgetKind::Stack);
        assert_eq!(widget_kind(TEXT), WidgetKind::Text);
        assert_eq!(widget_kind(BUTTON), WidgetKind::Button);
        assert_eq!(widget_kind(GRID), WidgetKind::Grid);
        assert_eq!(widget_kind(LAZY_VGRID), WidgetKind::Grid);
        assert_eq!(widget_kind(LAZY_HGRID), WidgetKind::Grid);
        assert_eq!(widget_kind(SCROLLVIEW), WidgetKind::ScrollView);
        assert_eq!(widget_kind(ZSTACK), WidgetKind::Overlay);
        assert_eq!(widget_kind(SPACER), WidgetKind::Spacer);
        assert_eq!(widget_kind(IMAGE), WidgetKind::Image);
        assert_eq!(widget_kind(TOGGLE), WidgetKind::Toggle);
        assert_eq!(widget_kind(SLIDER), WidgetKind::Slider);
        assert_eq!(widget_kind(TEXT_FIELD), WidgetKind::TextField);
        assert_eq!(widget_kind(TEXT_EDITOR), WidgetKind::TextEditor);
        assert_eq!(widget_kind(COLOR), WidgetKind::Color);
        assert_eq!(widget_kind(SHAPE), WidgetKind::Shape);
        assert_eq!(widget_kind(DIVIDER), WidgetKind::Divider);
        assert_eq!(widget_kind(PROGRESS_VIEW), WidgetKind::ProgressView);
        assert_eq!(widget_kind(GAUGE), WidgetKind::Gauge);
        assert_eq!(widget_kind(STEPPER), WidgetKind::Stepper);
        assert_eq!(widget_kind(DATE_PICKER), WidgetKind::DatePicker);
        assert_eq!(widget_kind(PICKER), WidgetKind::Picker);
        assert_eq!(widget_kind(MENU), WidgetKind::Menu);
        assert_eq!(widget_kind(COLOR_PICKER), WidgetKind::ColorPicker);
        // Components without a mapping fall back to a blank widget.
        assert_eq!(widget_kind(COMMENT), WidgetKind::Blank);
    }

    #[test]
    fn container_kinds_are_recognized() {
        use component_type::*;
        for ct in [VSTACK, HSTACK, GRID, SCROLLVIEW, ZSTACK] {
            assert!(is_container(ct), "{ct:#x} should be a container");
        }
        for ct in [TEXT, BUTTON, SPACER, IMAGE, TOGGLE, SLIDER] {
            assert!(!is_container(ct), "{ct:#x} should not be a container");
        }
    }

    #[test]
    fn nav_slot_detection_is_route_driven() {
        use component_type::*;
        // A stack carrying ROUTE is a navigation slot.
        let mut node = test_node(1, VSTACK, vec![2], HashMap::new(), HashMap::new());
        assert!(!is_nav_slot(&node), "no ROUTE → not a nav slot");
        node.strings.insert(property_id::ROUTE, "/users/42".into());
        assert!(is_nav_slot(&node), "VSTACK + ROUTE → nav slot");
        node.component_type = HSTACK;
        assert!(is_nav_slot(&node), "HSTACK + ROUTE → nav slot");
        node.component_type = TEXT;
        assert!(!is_nav_slot(&node), "ROUTE on a leaf is not a nav slot");
    }

    #[test]
    fn nav_chrome_mode_is_f32_enum() {
        use component_type::*;
        let mut node = test_node(1, VSTACK, vec![], HashMap::new(), HashMap::new());
        assert!(!is_custom_chrome(&node), "missing NAV_CHROME → PlatformDefault");
        // Custom = enum code 1 carried as an F32 bit pattern (spec convention).
        node.properties.insert(property_id::NAV_CHROME, 1.0f32.to_bits());
        assert!(is_custom_chrome(&node), "NAV_CHROME=1.0 → Custom");
        node.properties.insert(property_id::NAV_CHROME, 0.0f32.to_bits());
        assert!(!is_custom_chrome(&node), "NAV_CHROME=0.0 → PlatformDefault");
    }

    #[test]
    fn nav_action_decides_by_depth() {
        // The depth reconcile contract (spec DSL.md §4.5): same route → refresh
        // in place; deeper than the stack → push; same depth + new route →
        // replace the top page.
        assert_eq!(nav_action(3, 3, Some("/b"), "/b"), NavAction::Refresh,
            "visible page already shows the route → refresh in place");
        assert_eq!(nav_action(2, 3, Some("/b"), "/c"), NavAction::Push,
            "deeper than the stack → push a new page");
        assert_eq!(nav_action(3, 3, Some("/b"), "/c"), NavAction::Replace,
            "same depth, different route → replace the top page (a guard redirect)");
        assert_eq!(nav_action(2, 2, Some("/a"), "/c"), NavAction::Replace,
            "a stack reset that kept the top page → replace it");
        // The pop-down step is separate: a shallower depth pops first.
        assert_eq!(nav_action(1, 1, Some("/a"), "/a"), NavAction::Refresh,
            "after popping down to depth 1, a matching root refreshes");
    }

    #[test]
    fn stack_layout_maps_from_opcode_frames() {
        // Hand-crafted opcode frames (not the DSL) drive the renderer's
        // RenderTree + pure layout mapping, exercising spacing/alignment/
        // padding - including a delta frame - without touching GTK widgets.
        use pathland_core::{Host, APPEND, component_type, property_id, value_type};

        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        // Frame 1: VSTACK (id 1) with SPACING=4, ALIGNMENT=Center, PADDING=8
        // with a PADDING_TOP override of 2; child TEXT (id 2).
        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            guest.create_node(1, component_type::VSTACK).unwrap();
            guest
                .set_property(1, property_id::SPACING, value_type::F32, 4.0f32.to_bits())
                .unwrap();
            guest
                .set_property(1, property_id::ALIGNMENT, value_type::F32, 1.0f32.to_bits())
                .unwrap();
            guest
                .set_property(1, property_id::PADDING, value_type::F32, 8.0f32.to_bits())
                .unwrap();
            guest
                .set_property(1, property_id::PADDING_TOP, value_type::F32, 2.0f32.to_bits())
                .unwrap();
            guest.create_node(2, component_type::TEXT).unwrap();
            guest.insert_child(1, 2, APPEND).unwrap();
            guest.set_text(2, "hi").unwrap();
            guest.end_frame();
        }

        let mut tree = RenderTree::default();
        {
            let mut host = Host::new(&mut mem, &layout);
            tree.apply_frame(&host.frames()[0]);
        }

        let root = tree.root().unwrap().clone();
        let l = crate::layout::stack_layout(&root).unwrap();
        assert_eq!(l.orientation, gtk::Orientation::Vertical);
        assert_eq!(l.spacing, 4);
        assert_eq!(l.child_align, Align::Center);
        assert_eq!(
            crate::layout::padding_from(&root),
            crate::layout::Edges { top: 2, right: 8, bottom: 8, left: 8 }
        );

        // Frame 2 (delta): SPACING 4->12, ALIGNMENT Center->End.
        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            guest
                .set_property(1, property_id::SPACING, value_type::F32, 12.0f32.to_bits())
                .unwrap();
            guest
                .set_property(1, property_id::ALIGNMENT, value_type::F32, 2.0f32.to_bits())
                .unwrap();
            guest.end_frame();
        }
        {
            let mut host = Host::new(&mut mem, &layout);
            tree.apply_frame(&host.frames()[0]);
        }

        let root = tree.root().unwrap().clone();
        let l = crate::layout::stack_layout(&root).unwrap();
        assert_eq!(l.spacing, 12);
        assert_eq!(l.child_align, Align::End);
    }

    /// A minimal `HostNode` for headless widget/kind tests.
    fn test_node(
        id: u32,
        component_type: u16,
        children: Vec<u32>,
        properties: HashMap<u16, u32>,
        strings: HashMap<u16, String>,
    ) -> HostNode {
        HostNode {
            id,
            component_type,
            text: None,
            parent: None,
            children,
            properties,
            strings,
            token_refs: Default::default(),
        }
    }
}
