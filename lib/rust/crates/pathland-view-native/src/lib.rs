//! # pathland-view-native
//!
//! Native (non-WASM) projection of the Pathland flat world, exposed over both a
//! Rust API and a C ABI for foreign host languages (Swift / Java / C# / …).
//!
//! This is the **cross-language, zero-copy** path: a host links this crate as a
//! native library (or a `cdylib` and its `pathland_native_*` C functions), drives
//! the retained tree with the flat `builder`/`engine` operations, and reads
//! opcodes from the **shared-memory ring in place** — no serialization, no batch,
//! no WASM.
//!
//! The surface is the flat `builder`/`engine` world over a native shared-memory
//! transport.

use std::collections::{BTreeMap, HashMap};
use std::rc::Rc;

use pathland_engine::{Engine, MediaController, MediaControllerRef, SignalId, SignalValue};
use pathland_core::{component_type, property_id, MemoryLayout};
use pathland_core_transport::{
    DriverTransport, FrameSource, OpcodeBatch, RingTransport, TransportError,
};
use pathland_view::{component_type_id, Node};

mod capi;

/// A media controller transport slot (spec/MEDIA.md): the addressable signals of
/// a controller, so a flat host can read a report or write a command.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MediaControllerSlot {
    /// Play/pause command (`PLAYBACK_STATE`).
    Playing,
    /// Seek command (`MEDIA_POSITION`, seconds).
    Position,
    /// Volume command (`MEDIA_VOLUME`, 0..1).
    Volume,
    /// Mute command (`MEDIA_MUTED`).
    Muted,
    /// Loop-on-end command (`MEDIA_LOOP`).
    Loop,
    /// Playback rate command (`MEDIA_RATE`).
    Rate,
    /// Position report feed (`MEDIA_TIME_UPDATED`).
    Time,
    /// Duration report feed (`MEDIA_DURATION_CHANGED`).
    Duration,
    /// Volume report feed (`MEDIA_VOLUME_CHANGED`).
    VolumeReport,
}

impl MediaControllerSlot {
    /// The slot's signal id on `controller`.
    pub fn id_of(self, controller: &MediaController) -> SignalId {
        match self {
            MediaControllerSlot::Playing => controller.playing_signal().id(),
            MediaControllerSlot::Position => controller.position_signal().id(),
            MediaControllerSlot::Volume => controller.volume_signal().id(),
            MediaControllerSlot::Muted => controller.muted_signal().id(),
            MediaControllerSlot::Loop => controller.looping_signal().id(),
            MediaControllerSlot::Rate => controller.rate_signal().id(),
            MediaControllerSlot::Time => controller.time_signal().id(),
            MediaControllerSlot::Duration => controller.duration_signal().id(),
            MediaControllerSlot::VolumeReport => controller.volume_report_signal().id(),
        }
    }
}

/// A flat node in the host's retained tree (id → node).
#[derive(Clone)]
pub struct FlatNode {
    pub id: u32,
    pub view_component: pathland_view::Component,
    pub properties: BTreeMap<u16, u32>,
    /// A signal the node's text is bound to (overrides the component text).
    pub text_binding: Option<SignalId>,
    /// Property signals (propertyId → signal), overriding static properties.
    pub property_bindings: BTreeMap<u16, SignalId>,
    /// `STRING`-valued property signals (propertyId → signal), overriding the
    /// static value (e.g. a media controller's `MEDIA_SOURCE`).
    pub string_bindings: BTreeMap<u16, SignalId>,
    /// The media controller this node references (a host-side handle, spec/MEDIA.md):
    /// a marker node *is* the controller's node; a `VIDEO` node is one of its
    /// surfaces (`MEDIA_CONTROLLER_ID` is linked at materialization).
    pub media_controller: Option<u32>,
    pub parent: Option<u32>,
    pub children: Vec<u32>,
}

/// The native host: owns the retained tree, the diff engine, and the shared
/// zero-copy ring.
pub struct NativeHost {
    nodes: HashMap<u32, FlatNode>,
    engine: Engine,
    ring: RingTransport,
    /// Media controllers by host handle (spec/MEDIA.md). A controller is an
    /// engine-side state object; the flat world holds them so a host that links
    /// only this ABI can still author media.
    media_controllers: HashMap<u32, Rc<MediaController>>,
    next_media_controller: u32,
}

/// Safe, checked operations returning `()` (used by the C shim).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HostError {
    /// The operation referenced a missing node.
    NotFound,
}

impl NativeHost {
    /// Create a host with a fresh shared ring (default layout).
    pub fn new() -> Self {
        Self {
            nodes: HashMap::new(),
            engine: Engine::new(),
            ring: RingTransport::new(),
            media_controllers: HashMap::new(),
            next_media_controller: 1,
        }
    }

    /// Create a host over a caller-provided ring buffer + layout.
    pub fn with_buffer(mem: Vec<u8>, layout: MemoryLayout) -> Self {
        Self {
            nodes: HashMap::new(),
            engine: Engine::new(),
            ring: RingTransport::with_buffer(mem, layout),
            media_controllers: HashMap::new(),
            next_media_controller: 1,
        }
    }

    /// Create (or reset) a node with a host-supplied stable id.
    pub fn create_node(&mut self, id: u32, component: pathland_view::Component) {
        let entry = self.nodes.entry(id).or_insert(FlatNode {
            id,
            view_component: component.clone(),
            properties: BTreeMap::new(),
            text_binding: None,
            property_bindings: BTreeMap::new(),
            string_bindings: BTreeMap::new(),
            media_controller: None,
            parent: None,
            children: Vec::new(),
        });
        entry.view_component = component;
        entry.properties.clear();
        entry.text_binding = None;
        entry.property_bindings.clear();
        entry.string_bindings.clear();
        entry.media_controller = None;
    }

    /// Insert `child` into `parent` at `index` (u32::MAX appends).
    pub fn insert_child(&mut self, parent: u32, child: u32, index: u32) {
        if let Some(p) = self.nodes.get_mut(&parent) {
            let idx = if index == u32::MAX {
                p.children.len()
            } else {
                (index as usize).min(p.children.len())
            };
            if !p.children.contains(&child) {
                p.children.insert(idx, child);
            }
        }
        if let Some(c) = self.nodes.get_mut(&child) {
            c.parent = Some(parent);
        }
    }

    /// Remove `child` from `parent`.
    pub fn remove_child(&mut self, parent: u32, child: u32) {
        if let Some(p) = self.nodes.get_mut(&parent) {
            p.children.retain(|c| *c != child);
        }
        if let Some(c) = self.nodes.get_mut(&child) {
            c.parent = None;
        }
    }

    /// Set a node's text content.
    pub fn set_text(&mut self, id: u32, text: &str) {
        if let Some(node) = self.nodes.get_mut(&id) {
            match &mut node.view_component {
                pathland_view::Component::Text { text: t } => *t = text.into(),
                pathland_view::Component::Button { label } => *label = text.into(),
                _ => {}
            }
        }
    }

    /// Set a constraint/style property (raw value bits; valueType derived from
    /// the property id).
    pub fn set_property(&mut self, id: u32, prop: u16, value: u32) {
        if let Some(node) = self.nodes.get_mut(&id) {
            node.properties.insert(prop, value);
        }
    }

    /// Set a float property (stores `f32` bit pattern).
    pub fn set_property_f32(&mut self, id: u32, prop: u16, value: f32) {
        self.set_property(id, prop, value.to_bits());
    }

    /// Bind a node's text to a signal (the signal's `Str` value wins over any
    /// static text). Returns `true` if the node exists.
    pub fn bind_text(&mut self, id: u32, signal: SignalId) -> bool {
        if let Some(node) = self.nodes.get_mut(&id) {
            node.text_binding = Some(signal);
            true
        } else {
            false
        }
    }

    /// Bind a node's property to a signal. Returns `true` if the node exists.
    pub fn bind_property(&mut self, id: u32, prop: u16, signal: SignalId) -> bool {
        if let Some(node) = self.nodes.get_mut(&id) {
            node.property_bindings.insert(prop, signal);
            true
        } else {
            false
        }
    }

    /// Bind a node's `STRING`-valued property to a signal (e.g. a controller's
    /// `MEDIA_SOURCE`; a change re-emits only that property). Returns `true` if
    /// the node exists.
    pub fn bind_string_property(&mut self, id: u32, prop: u16, signal: SignalId) -> bool {
        if let Some(node) = self.nodes.get_mut(&id) {
            node.string_bindings.insert(prop, signal);
            true
        } else {
            false
        }
    }

    // --- Media controllers (spec/MEDIA.md) ---

    /// Create a media controller and return its host handle (0 = failure). The
    /// controller owns its transport signals on the host's runtime, reachable
    /// through [`NativeHost::media_controller_signal`].
    pub fn media_controller_create(&mut self) -> u32 {
        let runtime = self.engine.runtime();
        let handle = self.next_media_controller;
        self.next_media_controller += 1;
        self.media_controllers
            .insert(handle, Rc::new(MediaController::new(&runtime)));
        handle
    }

    /// A controller's transport signal for `slot`
    /// ([`MediaControllerSlot`]) — write it with `set_signal` to drive playback.
    pub fn media_controller_signal(&self, handle: u32, slot: MediaControllerSlot) -> Option<SignalId> {
        let controller = self.media_controllers.get(&handle)?;
        Some(slot.id_of(controller))
    }

    /// Set a controller's resource (resource name, file path or URL).
    pub fn media_controller_source(&mut self, handle: u32, source: &str) -> bool {
        match self.media_controllers.get(&handle) {
            Some(existing) => {
                // `MediaController` is shared; rebuild it with the new source and
                // keep the same handle, so views referencing it stay valid.
                let rebuilt = MediaController::new(&self.engine.runtime())
                    .source(source)
                    .playing(existing.playing_signal())
                    .position(existing.position_signal())
                    .volume(existing.volume_signal())
                    .muted(existing.muted_signal())
                    .looping(existing.looping_signal())
                    .rate(existing.rate_signal())
                    .time(existing.time_signal())
                    .duration(existing.duration_signal())
                    .volume_report(existing.volume_report_signal());
                self.media_controllers.insert(handle, Rc::new(rebuilt));
                true
            }
            None => false,
        }
    }

    /// Mark a node as referencing a media controller: a node created as
    /// `MEDIA_CONTROLLER` becomes the controller's own (non-visual) node, and a
    /// `VIDEO` node becomes one of its surfaces (`MEDIA_CONTROLLER_ID` is linked
    /// at materialization). Returns `true` if the node and handle exist.
    pub fn node_media_controller(&mut self, id: u32, handle: u32) -> bool {
        if !self.media_controllers.contains_key(&handle) {
            return false;
        }
        if let Some(node) = self.nodes.get_mut(&id) {
            node.media_controller = Some(handle);
            true
        } else {
            false
        }
    }

    /// Create a new signal with an initial value.
    pub fn create_signal(&mut self, value: SignalValue) -> SignalId {
        self.engine.create_signal(value)
    }

    /// Read a signal's current value.
    pub fn get_signal(&self, id: SignalId) -> Option<SignalValue> {
        self.engine.get_signal(id)
    }

    /// Replace a signal's value, re-emitting only the nodes that depend on it.
    ///
    /// Writes `SET_PROPERTY` / `SET_TEXT` deltas into the shared ring. Returns
    /// `true` if any opcodes were written.
    pub fn set_signal(&mut self, id: SignalId, value: SignalValue) -> bool {
        let mut guest = self.ring.producer();
        guest.begin_frame();
        let n = self.engine.set_signal(id, value, &mut guest).unwrap_or(0);
        guest.end_frame();
        n > 0
    }

    /// Emit the retained tree as opcodes into the shared ring.
    ///
    /// Returns `true` if a frame was written (i.e. the tree is non-empty).
    pub fn emit(&mut self) -> bool {
        let root = self.root_id().and_then(|r| self.materialize(r));
        if let Some(root) = root {
            let mut guest = self.ring.producer();
            guest.begin_frame();
            let _ = self.engine.emit(&root, &mut guest);
            guest.end_frame();
            true
        } else {
            false
        }
    }

    /// The ring's linear-memory buffer (for embedding / FFI).
    pub fn ring_buffer(&self) -> &[u8] {
        self.ring.buffer()
    }

    /// Consume the ring transport (for hosts that read frames via `FrameSource`).
    pub fn take_ring(self) -> RingTransport {
        self.ring
    }

    /// The ring memory layout (region geometry).
    pub fn layout(&self) -> MemoryLayout {
        *self.layout_ref()
    }

    fn layout_ref(&self) -> &MemoryLayout {
        self.ring.layout()
    }

    /// The single node without a parent (the root).
    pub fn root_id(&self) -> Option<u32> {
        self.nodes.values().find(|n| n.parent.is_none()).map(|n| n.id)
    }

    /// Materialize a `pathland_view::Node` subtree rooted at `id`.
    fn materialize(&self, id: u32) -> Option<Node> {
        // The controller's node id, resolved up front so a surface can be linked
        // to it before its own properties are written (spec/MEDIA.md).
        let controller_node = self
            .nodes
            .values()
            .find(|n| {
                n.media_controller.is_some()
                    && n.view_component == pathland_view::Component::MediaController
            })
            .map(|n| n.id);
        self.materialize_at(id, controller_node)
    }

    fn materialize_at(&self, id: u32, controller_node: Option<u32>) -> Option<Node> {
        let flat = self.nodes.get(&id)?.clone();
        let mut node = Node::from_component(&flat.view_component);
        node.id = flat.id;
        node.properties = flat.properties.clone();
        node.text_binding = flat.text_binding;
        node.property_bindings = flat.property_bindings.clone();
        node.string_bindings = flat.string_bindings.clone();
        if let Some(handle) = flat.media_controller {
            if let Some(controller) = self.media_controllers.get(&handle) {
                node.media_controller = Some(MediaControllerRef::new(Rc::clone(controller)));
                // A `VIDEO` surface points at the controller's node.
                if flat.view_component == pathland_view::Component::Video {
                    if let Some(target) = controller_node {
                        node.properties.insert(property_id::MEDIA_CONTROLLER_ID, target);
                    }
                }
            }
        }
        for child_id in &flat.children {
            if let Some(child) = self.materialize_at(*child_id, controller_node) {
                node.children.push(child);
            }
        }
        Some(node)
    }
}

impl Default for NativeHost {
    fn default() -> Self {
        Self::new()
    }
}

/// The host is a `FrameSource` over its shared ring: a renderer drains the
/// guest→host frames the engine produced.
impl FrameSource for NativeHost {
    fn next_frame(&mut self) -> Result<Option<OpcodeBatch<'_>>, TransportError> {
        self.ring.next_frame()
    }
}

/// The host can also write raw inputs (host → guest) into its event ring.
impl DriverTransport for NativeHost {
    fn send_input(&mut self, event: &pathland_core::Event) -> Result<(), TransportError> {
        self.ring.send_input(event)
    }
}

impl NativeHost {
    /// Drain raw-input events written by a renderer into the host → guest event
    /// ring (guest side). The application polls this to receive inputs.
    pub fn drain_events(&mut self) -> Vec<pathland_core::Event> {
        self.ring.drain_events()
    }
}

/// Map a raw u32 component id (C ABI) to a `pathland_view::Component`.
pub fn component_from_id(id: u32) -> Option<pathland_view::Component> {
    use pathland_view::Component;
    match id as u16 {
        component_type::HSTACK => Some(Component::HStack),
        component_type::VSTACK => Some(Component::VStack),
        component_type::ZSTACK => Some(Component::ZStack),
        component_type::TEXT => Some(Component::Text { text: String::new() }),
        component_type::BUTTON => Some(Component::Button { label: String::new() }),
        component_type::SPACER => Some(Component::Spacer),
        component_type::IMAGE => Some(Component::Image),
        component_type::COLOR => Some(Component::Color),
        component_type::SHAPE => Some(Component::Shape),
        component_type::DIVIDER => Some(Component::Divider),
        component_type::PROGRESS_VIEW => Some(Component::ProgressView),
        component_type::GAUGE => Some(Component::Gauge),
        component_type::GRID => Some(Component::Grid),
        component_type::SCROLLVIEW => Some(Component::ScrollView),
        component_type::LAZY_VGRID => Some(Component::LazyVGrid),
        component_type::LAZY_HGRID => Some(Component::LazyHGrid),
        component_type::LAZY_VSTACK => Some(Component::LazyVStack),
        component_type::LAZY_HSTACK => Some(Component::LazyHStack),
        component_type::MEDIA_CONTROLLER => Some(Component::MediaController),
        component_type::VIDEO => Some(Component::Video),
        component_type::TOGGLE => Some(Component::Toggle),
        component_type::SLIDER => Some(Component::Slider),
        component_type::TEXT_FIELD => Some(Component::TextField),
        component_type::TEXT_EDITOR => Some(Component::TextEditor),
        component_type::STEPPER => Some(Component::Stepper),
        component_type::DATE_PICKER => Some(Component::DatePicker),
        component_type::PICKER => Some(Component::Picker),
        component_type::MENU => Some(Component::Menu),
        component_type::COLOR_PICKER => Some(Component::ColorPicker),
        _ => None,
    }
}

/// Map a component to its raw u32 id (C ABI).
pub fn component_id(component: &pathland_view::Component) -> u32 {
    component_type_id(component) as u32
}

/// Map a raw u32 property id (C ABI) to a protocol property id and derive its
/// value type. `(property_id, value_type)`.
pub fn property_value_type(prop: u32) -> (u16, u8) {
    let pid = prop as u16;
    let vt = if matches!(
        pid,
        property_id::COLOR
            | property_id::BACKGROUND_COLOR
            | property_id::BORDER_COLOR
            | property_id::COLOR_VALUE
            | property_id::COLOR_MULTIPLY
            | property_id::SHADOW_COLOR
    ) {
        pathland_core::value_type::COLOR
    } else if matches!(
        pid,
        property_id::EVENT_LISTENERS
            | property_id::BORDER_EDGES
            | property_id::ACTION_ID
            | property_id::BINDING_ID
            | property_id::LINE_LIMIT
            | property_id::SELECTION
    ) {
        pathland_core::value_type::U32
    } else if matches!(
        pid,
        property_id::LABEL | property_id::PROMPT | property_id::FONT_FAMILY | property_id::IMAGE_SOURCE
    ) {
        pathland_core::value_type::STRING
    } else if matches!(
        pid,
        property_id::SELECTED
            | property_id::VISIBLE
            | property_id::ENABLED
            | property_id::CLIPS_TO_BOUNDS
            | property_id::UNDERLINE
            | property_id::STRIKETHROUGH
            | property_id::COLOR_INVERT
            | property_id::ALLOWS_HIT_TESTING
            | property_id::IS_SECURE
            | property_id::IS_INDETERMINATE
            | property_id::FIXED_SIZE_HORIZONTAL
            | property_id::FIXED_SIZE_VERTICAL
    ) {
        pathland_core::value_type::U8
    } else {
        pathland_core::value_type::F32
    };
    (pid, vt)
}

#[cfg(test)]
mod tests {
    use super::*;
    use pathland_core::Event;

    #[test]
    fn native_host_round_trips_events_via_ring() {
        // The host (application) drains raw inputs written by a renderer into
        // the host → guest event ring, proving the generic opcode-engine path.
        let mut host = NativeHost::new();

        DriverTransport::send_input(
            &mut host,
            &Event::PointerUp { target: 7, x: 3.0, y: 4.0, secondary: false },
        )
        .unwrap();

        assert_eq!(
            host.drain_events(),
            vec![Event::PointerUp { target: 7, x: 3.0, y: 4.0, secondary: false }]
        );
        assert!(host.drain_events().is_empty());
    }
}
