//! Retained view tree types.
//!
//! The retained tree is the application's canonical UI tree: the single source
//! of truth the engine diffs into declarative `TREE`/`PARAMETER` opcodes. It lives
//! here (in the opcode-engine core) so the diff emitter can walk it without the
//! engine depending on the authoring DSL.

use alloc::collections::BTreeMap;
use alloc::rc::Rc;
use alloc::string::String;
use alloc::vec::Vec;
use core::cell::RefCell;

use pathland_core::component_type;

use crate::signal::SignalId;

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
    /// Token-referenced properties (`propertyId → token path`), emitted as
    /// `PARAMETER:SET_PROPERTY` with the `DESIGN_TOKEN` value type. A token ref
    /// overrides the literal `properties` entry for the same id (spec/TOKENS.md).
    pub token_properties: BTreeMap<u16, String>,
    /// A signal the node's text is bound to (overrides `Component::Text` /
    /// `Component::Button` label when set).
    pub text_binding: Option<SignalId>,
    /// Property signals (propertyId to signal), overriding the static
    /// `properties` entry for that id when both are present.
    pub property_bindings: BTreeMap<u16, SignalId>,
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
            .field("token_properties", &self.token_properties)
            .field("text_binding", &self.text_binding)
            .field("property_bindings", &self.property_bindings)
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
            token_properties: BTreeMap::new(),
            text_binding: None,
            property_bindings: BTreeMap::new(),
            gestures: Vec::new(),
        }
    }
}

/// An app-side gesture handler attached to a view.
///
/// Callbacks live only in the application's retained tree and are **never**
/// emitted as protocol — the protocol carries only the declarative
/// `EVENT_LISTENERS` signal. Tap is the first gesture; more can be added later.
#[derive(Clone)]
pub enum Gesture {
    /// A tap gesture: invoke the closure when a tap is recognized.
    Tap(Rc<RefCell<dyn FnMut() + 'static>>),
}

impl PartialEq for Gesture {
    fn eq(&self, other: &Self) -> bool {
        match (self, other) {
            (Gesture::Tap(a), Gesture::Tap(b)) => Rc::ptr_eq(a, b),
        }
    }
}

impl core::fmt::Debug for Gesture {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        match self {
            Gesture::Tap(_) => f.write_str("Tap(..)"),
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
pub fn assign_ids(root: &mut Node, next: &mut u32) {
    root.id = *next;
    *next += 1;
    for child in &mut root.children {
        assign_ids(child, next);
    }
}

/// Collect a `node id -> tap callback` map from a built (and id-assigned) tree.
///
/// Call after [`assign_ids`] so each node has its stable id; the returned map
/// keys the recognizer's tap target to the closure registered by
/// `.on_tap_gesture(...)`.
pub fn collect_tap_handlers(
    root: &Node,
    out: &mut BTreeMap<u32, Rc<RefCell<dyn FnMut() + 'static>>>,
) {
    for gesture in &root.gestures {
        let Gesture::Tap(handler) = gesture;
        out.insert(root.id, handler.clone());
    }
    for child in &root.children {
        collect_tap_handlers(child, out);
    }
}
