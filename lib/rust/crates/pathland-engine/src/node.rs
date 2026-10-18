//! Retained view tree types.
//!
//! The retained tree is the application's canonical UI tree: the single source
//! of truth the engine diffs into declarative `TREE`/`PARAMETER` opcodes. It lives
//! here (in the opcode-engine core) so the diff emitter can walk it without the
//! engine depending on the authoring DSL.

use alloc::collections::{BTreeMap, BTreeSet};
use alloc::rc::Rc;
use alloc::string::String;
use alloc::vec::Vec;
use core::cell::RefCell;

use pathland_core::{component_type, property_id};

use crate::media::MediaControllerRef;
use crate::signal::SignalId;

/// An app-side tap callback.
pub type TapHandler = Rc<RefCell<dyn FnMut() + 'static>>;
/// An app-side value (`f32`) input sink.
pub type ValueInputHandler = Rc<RefCell<dyn FnMut(f32) + 'static>>;
/// An app-side text input sink.
pub type TextInputHandler = Rc<RefCell<dyn FnMut(&str) + 'static>>;
/// An app-side date input sink.
pub type DateInputHandler = Rc<RefCell<dyn FnMut(i32, u32) + 'static>>;

/// A node in the retained view tree (the application's canonical UI tree).
#[derive(Clone, PartialEq)]
pub struct Node {
    /// Stable node id (0 until `assign_ids`).
    pub id: u32,
    pub component: Component,
    pub children: Vec<Node>,
    /// Constraint/style properties (propertyId to value) emitted as
    /// `PARAMETER:SET_PROPERTY`.
    pub properties: BTreeMap<u16, u32>,
    /// String-valued properties (`propertyId → value`, e.g. `FONT_FAMILY`,
    /// `IMAGE_SOURCE`, `LABEL`), emitted as `PARAMETER:SET_PROPERTY` with the
    /// `STRING` value type (the string is arena-allocated at emit time).
    pub string_properties: BTreeMap<u16, String>,
    /// Token-referenced properties (`propertyId → token path`), emitted as
    /// `PARAMETER:SET_PROPERTY` with the `DESIGN_TOKEN` value type. A token ref
    /// overrides the literal `properties` entry for the same id (spec/TOKENS.md).
    pub token_properties: BTreeMap<u16, String>,
    /// List-valued properties (`propertyId → values`, e.g. a `FIT_QUERY`
    /// threshold table), emitted as `PARAMETER:SET_PROPERTY` with the `LIST`
    /// value type (arena-allocated as `[u32 count][f32 × count]` at emit time).
    pub list_properties: BTreeMap<u16, Vec<f32>>,
    /// A signal the node's text is bound to (overrides `Component::Text` /
    /// `Component::Button` label when set).
    pub text_binding: Option<SignalId>,
    /// Property signals (propertyId to signal), overriding the static
    /// `properties` entry for that id when both are present.
    pub property_bindings: BTreeMap<u16, SignalId>,
    /// `STRING`-valued property signals (propertyId to signal), overriding the
    /// static `string_properties` entry for that id when both are present. A
    /// change re-emits only that property (`SET_PROPERTY` with the `STRING`
    /// value type) — e.g. a media controller's `MEDIA_SOURCE`.
    pub string_bindings: BTreeMap<u16, SignalId>,
    /// The media controller this node references (spec/MEDIA.md): a
    /// `MEDIA_CONTROLLER` node **is** the controller's node, and a `VIDEO`
    /// surface's `MEDIA_CONTROLLER_ID` points at it. `None` otherwise.
    pub media_controller: Option<MediaControllerRef>,
    /// App-side gesture handlers (callbacks). These live only in the
    /// application's retained tree and are **never** emitted as protocol.
    pub gestures: Vec<Gesture>,
}

impl core::fmt::Debug for Node {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("Node")
            .field("id", &self.id)
            .field("component", &self.component)
            .field("children", &self.children)
            .field("properties", &self.properties)
            .field("string_properties", &self.string_properties)
            .field("token_properties", &self.token_properties)
            .field("list_properties", &self.list_properties)
            .field("text_binding", &self.text_binding)
            .field("property_bindings", &self.property_bindings)
            .field("string_bindings", &self.string_bindings)
            .field("media_controller", &self.media_controller)
            .field("gestures", &self.gestures.len())
            .finish()
    }
}

impl Node {
    /// Build an empty node (id 0, no children/properties) for a component.
    pub fn from_component(component: &Component) -> Self {
        Self {
            id: 0,
            component: component.clone(),
            children: Vec::new(),
            properties: BTreeMap::new(),
            string_properties: BTreeMap::new(),
            token_properties: BTreeMap::new(),
            list_properties: BTreeMap::new(),
            text_binding: None,
            property_bindings: BTreeMap::new(),
            string_bindings: BTreeMap::new(),
            media_controller: None,
            gestures: Vec::new(),
        }
    }
}

/// An app-side gesture / input handler attached to a view.
///
/// Callbacks live only in the application's retained tree and are **never**
/// emitted as protocol — the protocol carries only the declarative
/// `EVENT_LISTENERS` / `BINDING_ID` signals. A tap is composed app-side from raw
/// pointer events; `ValueInput`/`TextInput`/`DateInput` are the two-way binding
/// sinks a host routes a control's `VALUE_CHANGED` / `TEXT_CHANGED` /
/// `DATE_CHANGED` into.
#[derive(Clone)]
pub enum Gesture {
    /// A tap gesture: invoke the closure when a tap is recognized.
    Tap(TapHandler),
    /// A value control's sink: write the bound signal from `VALUE_CHANGED`.
    ValueInput(ValueInputHandler),
    /// A text control's sink: write the bound signal from `TEXT_CHANGED`.
    TextInput(TextInputHandler),
    /// A date control's sink: write the bound signal from `DATE_CHANGED`.
    DateInput(DateInputHandler),
}

impl PartialEq for Gesture {
    fn eq(&self, other: &Self) -> bool {
        match (self, other) {
            (Gesture::Tap(a), Gesture::Tap(b)) => Rc::ptr_eq(a, b),
            (Gesture::ValueInput(a), Gesture::ValueInput(b)) => Rc::ptr_eq(a, b),
            (Gesture::TextInput(a), Gesture::TextInput(b)) => Rc::ptr_eq(a, b),
            (Gesture::DateInput(a), Gesture::DateInput(b)) => Rc::ptr_eq(a, b),
            _ => false,
        }
    }
}

impl core::fmt::Debug for Gesture {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        match self {
            Gesture::Tap(_) => f.write_str("Tap(..)"),
            Gesture::ValueInput(_) => f.write_str("ValueInput(..)"),
            Gesture::TextInput(_) => f.write_str("TextInput(..)"),
            Gesture::DateInput(_) => f.write_str("DateInput(..)"),
        }
    }
}

/// Core component types. These describe **structure only** — spacing, padding,
/// and sizing arrive as modifiers (properties), never as layout math here.
#[derive(Clone, PartialEq)]
pub enum Component {
    /// Vertical stack (native vertical container).
    VStack,
    /// Horizontal stack (native horizontal container).
    HStack,
    /// Depth-overlapping stack.
    ZStack,
    /// Text leaf.
    Text { text: String },
    /// Interactive button.
    Button { label: String },
    /// Flexible spacer.
    Spacer,
    /// Image leaf (`IMAGE_SOURCE` property).
    Image,
    /// Semantic icon leaf (`ICON_NAME`; renderer maps to a native symbol).
    Icon,
    /// Solid-color view (`COLOR` property).
    Color,
    /// Vector geometry (`SHAPE_KIND` property).
    Shape,
    /// Separator line.
    Divider,
    /// Determinate progress / activity indicator.
    ProgressView,
    /// Range meter.
    Gauge,
    /// Static 2D grid.
    Grid,
    /// Scrollable container.
    ScrollView,
    /// Virtualized vertical grid.
    LazyVGrid,
    /// Virtualized horizontal grid.
    LazyHGrid,
    /// Virtualized vertical stack.
    LazyVStack,
    /// Virtualized horizontal stack.
    LazyHStack,
    /// Non-visual media controller node (spec/MEDIA.md): owns a
    /// [`MediaController`](crate::MediaController)'s resource + transport. It
    /// renders nothing and takes no space.
    MediaController,
    /// Video playback **surface**: renders a `MEDIA_CONTROLLER`'s video output
    /// (referenced via `MEDIA_CONTROLLER_ID`).
    Video,
    /// Fit slot (`SIZE_THAT_FITS`): shows its single (selected) child — only
    /// the selected candidate is ever transmitted; the renderer measures its
    /// own allocated width against the `FIT_QUERY` list and reports `FIT_CHANGED`.
    SizeThatFits,
    /// Boolean control (`TOGGLE_STYLE`).
    Toggle,
    /// Numeric range control.
    Slider,
    /// Single-line text input.
    TextField,
    /// Multi-line text input.
    TextEditor,
    /// Increment/decrement control.
    Stepper,
    /// Date & time selection.
    DatePicker,
    /// Selection control (options are children).
    Picker,
    /// Action trigger + popover container.
    Menu,
    /// Native color picker.
    ColorPicker,
}

impl core::fmt::Debug for Component {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        match self {
            Component::VStack => f.write_str("VStack"),
            Component::HStack => f.write_str("HStack"),
            Component::ZStack => f.write_str("ZStack"),
            Component::Text { text } => f.debug_tuple("Text").field(text).finish(),
            Component::Button { label } => f.debug_tuple("Button").field(label).finish(),
            Component::Spacer => f.write_str("Spacer"),
            Component::Image => f.write_str("Image"),
            Component::Icon => f.write_str("Icon"),
            Component::Color => f.write_str("Color"),
            Component::Shape => f.write_str("Shape"),
            Component::Divider => f.write_str("Divider"),
            Component::ProgressView => f.write_str("ProgressView"),
            Component::Gauge => f.write_str("Gauge"),
            Component::Grid => f.write_str("Grid"),
            Component::ScrollView => f.write_str("ScrollView"),
            Component::LazyVGrid => f.write_str("LazyVGrid"),
            Component::LazyHGrid => f.write_str("LazyHGrid"),
            Component::LazyVStack => f.write_str("LazyVStack"),
            Component::LazyHStack => f.write_str("LazyHStack"),
            Component::MediaController => f.write_str("MediaController"),
            Component::Video => f.write_str("Video"),
            Component::SizeThatFits => f.write_str("SizeThatFits"),
            Component::Toggle => f.write_str("Toggle"),
            Component::Slider => f.write_str("Slider"),
            Component::TextField => f.write_str("TextField"),
            Component::TextEditor => f.write_str("TextEditor"),
            Component::Stepper => f.write_str("Stepper"),
            Component::DatePicker => f.write_str("DatePicker"),
            Component::Picker => f.write_str("Picker"),
            Component::Menu => f.write_str("Menu"),
            Component::ColorPicker => f.write_str("ColorPicker"),
        }
    }
}

/// Map a component to its protocol component type id.
pub fn component_type_id(component: &Component) -> u16 {
    match component {
        Component::VStack => component_type::VSTACK,
        Component::HStack => component_type::HSTACK,
        Component::ZStack => component_type::ZSTACK,
        Component::Text { .. } => component_type::TEXT,
        Component::Button { .. } => component_type::BUTTON,
        Component::Spacer => component_type::SPACER,
        Component::Image => component_type::IMAGE,
        Component::Icon => component_type::ICON,
        Component::Color => component_type::COLOR,
        Component::Shape => component_type::SHAPE,
        Component::Divider => component_type::DIVIDER,
        Component::ProgressView => component_type::PROGRESS_VIEW,
        Component::Gauge => component_type::GAUGE,
        Component::Grid => component_type::GRID,
        Component::ScrollView => component_type::SCROLLVIEW,
        Component::LazyVGrid => component_type::LAZY_VGRID,
        Component::LazyHGrid => component_type::LAZY_HGRID,
        Component::LazyVStack => component_type::LAZY_VSTACK,
        Component::LazyHStack => component_type::LAZY_HSTACK,
        Component::MediaController => component_type::MEDIA_CONTROLLER,
        Component::Video => component_type::VIDEO,
        Component::SizeThatFits => component_type::SIZE_THAT_FITS,
        Component::Toggle => component_type::TOGGLE,
        Component::Slider => component_type::SLIDER,
        Component::TextField => component_type::TEXT_FIELD,
        Component::TextEditor => component_type::TEXT_EDITOR,
        Component::Stepper => component_type::STEPPER,
        Component::DatePicker => component_type::DATE_PICKER,
        Component::Picker => component_type::PICKER,
        Component::Menu => component_type::MENU,
        Component::ColorPicker => component_type::COLOR_PICKER,
    }
}

/// Assign stable unique sequential ids to a tree (pre-order; root first).
///
/// The engine keys its diff snapshot by node id, so call this after building
/// the (possibly re-built) tree and before `Engine::emit`.
///
/// **Media controllers are pinned**: a `MEDIA_CONTROLLER` node keeps the id it
/// was first given (spec/MEDIA.md), so a structural swap that shifts other ids
/// never changes the playback key — and its `VIDEO` surface's
/// `MEDIA_CONTROLLER_ID` is linked in the same pass.
pub fn assign_ids(root: &mut Node, next: &mut u32) {
    // Reserve every already-pinned controller id up front, so no other node can
    // be handed one even when it is lower than the current cursor.
    let mut reserved = BTreeSet::new();
    collect_pinned_controller_ids(root, &mut reserved);
    assign_ids_pass(root, next, &mut reserved);
}

/// Collect the ids already pinned by the controllers present in this tree.
fn collect_pinned_controller_ids(node: &Node, out: &mut BTreeSet<u32>) {
    if matches!(node.component, Component::MediaController) {
        if let Some(id) = node
            .media_controller
            .as_ref()
            .map(|c| c.0.pinned_node_id())
            .filter(|id| *id != 0)
        {
            out.insert(id);
        }
    }
    for child in &node.children {
        collect_pinned_controller_ids(child, out);
    }
}

/// Take the next free (unreserved) id from the cursor.
fn take_id(next: &mut u32, reserved: &BTreeSet<u32>) -> u32 {
    if *next == 0 {
        *next = 1;
    }
    while reserved.contains(next) {
        *next += 1;
    }
    let id = *next;
    *next += 1;
    id
}

fn assign_ids_pass(node: &mut Node, next: &mut u32, reserved: &mut BTreeSet<u32>) {
    match (&node.component, node.media_controller.clone()) {
        (Component::MediaController, Some(controller)) => {
            // A controller keeps the id it was first pinned to.
            let pinned = controller.0.pinned_node_id();
            node.id = if pinned != 0 {
                pinned
            } else {
                let id = take_id(next, reserved);
                controller.0.pin_node_id(id);
                id
            };
            reserved.insert(node.id);
        }
        _ => {
            node.id = take_id(next, reserved);
        }
    }
    // Link a video surface to its controller (spec/MEDIA.md).
    if matches!(node.component, Component::Video) {
        if let Some(id) = node
            .media_controller
            .as_ref()
            .map(|c| c.0.pinned_node_id())
            .filter(|id| *id != 0)
        {
            node.properties.insert(property_id::MEDIA_CONTROLLER_ID, id);
        }
    }
    for child in &mut node.children {
        assign_ids_pass(child, next, reserved);
    }
}

/// Collect a `node id -> tap callback` map from a built (and id-assigned) tree.
///
/// Call after [`assign_ids`] so each node has its stable id; the returned map
/// keys the recognizer's tap target to the closure registered by
/// `.on_tap_gesture(...)`.
pub fn collect_tap_handlers(root: &Node, out: &mut BTreeMap<u32, TapHandler>) {
    for gesture in &root.gestures {
        if let Gesture::Tap(handler) = gesture {
            out.insert(root.id, handler.clone());
        }
    }
    for child in &root.children {
        collect_tap_handlers(child, out);
    }
}

/// App-side two-way binding sinks collected from a built (and id-assigned) tree,
/// keyed by node id. A host routes the control events into these:
/// `TEXT_CHANGED` → [`InputHandlers::text`], `VALUE_CHANGED` →
/// [`InputHandlers::value`], `DATE_CHANGED` → [`InputHandlers::date`]. Each sink
/// writes its bound signal; re-emit the tree afterwards to flush the delta.
#[derive(Default)]
pub struct InputHandlers {
    /// `TEXT_CHANGED` sinks (text fields / editors).
    pub text: BTreeMap<u32, TextInputHandler>,
    /// `VALUE_CHANGED` sinks (toggle / slider / stepper / picker / color picker).
    pub value: BTreeMap<u32, ValueInputHandler>,
    /// `DATE_CHANGED` sinks (date pickers).
    pub date: BTreeMap<u32, DateInputHandler>,
}

/// Collect the two-way binding sinks from a built (and id-assigned) tree.
pub fn collect_input_handlers(root: &Node, out: &mut InputHandlers) {
    for gesture in &root.gestures {
        match gesture {
            Gesture::TextInput(handler) => {
                out.text.insert(root.id, handler.clone());
            }
            Gesture::ValueInput(handler) => {
                out.value.insert(root.id, handler.clone());
            }
            Gesture::DateInput(handler) => {
                out.date.insert(root.id, handler.clone());
            }
            Gesture::Tap(_) => {}
        }
    }
    for child in &root.children {
        collect_input_handlers(child, out);
    }
}
