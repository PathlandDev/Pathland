//! # pathland-host
//!
//! The **canonical host reader**: the retained `RenderTree` that decodes opcode
//! frames into a native-element description shared by every renderer (GTK4
//! desktop, HTML, TUI).
//!
//! Consumes frames and builds a **native-element description**: the tree
//! structure (`TREE`) plus constraint properties (`PARAMETER`) a renderer maps onto
//! that platform's native elements (GTK widgets, DOM elements, HTML).
//!
//! The engine does not compute layout; this stores only what the renderer needs
//! to create native elements and let them lay themselves out.

use std::collections::BTreeMap;
use std::collections::HashMap;

use pathland_core::tokens::{resolve as resolve_token, Scheme, TokenTables, TokenValue};
use pathland_core::{category, component_type, parameter, tree, value_type, Frame, Opcode};

/// A decoded node in the host's native-element description.
#[derive(Debug, Clone, PartialEq)]
pub struct HostNode {
    pub id: u32,
    pub component_type: u16,
    pub text: Option<String>,
    pub parent: Option<u32>,
    pub children: Vec<u32>,
    /// Constraint properties (propertyId -> value) for native layout/styling.
    pub properties: HashMap<u16, u32>,
    /// `STRING`-valued properties resolved at apply time (propertyId -> text).
    ///
    /// The wire carries strings in the arena / batch string section referenced
    /// by an offset; this map holds the resolved text for `LABEL`, `PROMPT`,
    /// `IMAGE_SOURCE`, `FONT_FAMILY`, … (see `spec/OPCODE.md`).
    pub strings: HashMap<u16, String>,
    /// `DESIGN_TOKEN`-typed properties (propertyId → token path, spec/TOKENS.md).
    ///
    /// The renderer resolves these against the theme and the active color
    /// scheme at apply time, materializing the concrete value into
    /// [`Self::properties`] / [`Self::strings`] (see
    /// [`RenderTree::resolve_tokens`]); the path is kept so it can be
    /// re-resolved when the scheme changes.
    pub token_refs: BTreeMap<u16, String>,
}

impl HostNode {
    /// A `STRING`-valued property resolved from the frame's string section.
    pub fn string_property(&self, prop: u16) -> Option<&str> {
        self.strings.get(&prop).map(String::as_str)
    }

    /// An `F32` property value, or a default when absent.
    pub fn f32_property(&self, prop: u16, default: f32) -> f32 {
        self.properties
            .get(&prop)
            .copied()
            .map(f32::from_bits)
            .unwrap_or(default)
    }

    /// A raw `U32` property value (no f32 reinterpretation), or a default.
    pub fn u32_property(&self, prop: u16, default: u32) -> u32 {
        self.properties.get(&prop).copied().unwrap_or(default)
    }

    /// The `SELECTED` checked state (U8 0/1).
    pub fn checked(&self) -> bool {
        self.u32_property(pathland_core::property_id::SELECTED, 0) != 0
    }
}

impl Default for HostNode {
    fn default() -> Self {
        Self {
            id: 0,
            component_type: component_type::HSTACK,
            text: None,
            parent: None,
            children: Vec::new(),
            properties: HashMap::new(),
            strings: HashMap::new(),
            token_refs: BTreeMap::new(),
        }
    }
}

/// The host's native-element description of a rendered tree.
#[derive(Debug)]
pub struct RenderTree {
    /// nodeId -> node.
    pub nodes: HashMap<u32, HostNode>,
    /// Design-token overrides (`PARAMETER::SET_DESIGN_TOKEN`), base (light) values
    /// keyed by full path.
    overrides: BTreeMap<String, TokenValue>,
    /// `dark.*` overrides keyed by the bare path (the prefix stripped).
    dark_overrides: BTreeMap<String, TokenValue>,
    /// Renderer default token values (Tier 1 light + dark, spec/TOKENS.md) —
    /// concrete platform-appropriate fallbacks, optionally enriched with
    /// platform-native theme colors (see each renderer's token module).
    defaults: BTreeMap<String, TokenValue>,
    dark_defaults: BTreeMap<String, TokenValue>,
    /// The effective color scheme (renderer-derived, never carried by the
    /// protocol).
    scheme: Scheme,
}

impl Default for RenderTree {
    fn default() -> Self {
        let (defaults, dark_defaults) = concrete_default_tables();
        Self {
            nodes: HashMap::new(),
            overrides: BTreeMap::new(),
            dark_overrides: BTreeMap::new(),
            defaults,
            dark_defaults,
            scheme: Scheme::Light,
        }
    }
}

impl RenderTree {
    /// Install renderer default token tables (see [`RenderTree::defaults`]).
    pub fn set_defaults(
        &mut self,
        defaults: BTreeMap<String, TokenValue>,
        dark_defaults: BTreeMap<String, TokenValue>,
    ) {
        self.defaults = defaults;
        self.dark_defaults = dark_defaults;
        self.resolve_tokens();
    }

    /// The effective color scheme.
    pub fn scheme(&self) -> Scheme {
        self.scheme
    }

    /// Set the effective color scheme and re-resolve every token-referencing
    /// property (spec/TOKENS.md resolution contract).
    pub fn set_scheme(&mut self, scheme: Scheme) {
        self.scheme = scheme;
        self.resolve_tokens();
    }

    /// Re-resolve every `DESIGN_TOKEN`-typed property against the theme, the
    /// active scheme, and the parent-fallback chain, materializing concrete
    /// values into [`HostNode::properties`] (numeric) or [`HostNode::strings`]
    /// (STRING tokens). The paths are kept in [`HostNode::token_refs`] for
    /// re-resolution on scheme change.
    pub fn resolve_tokens(&mut self) {
        let tables = TokenTables {
            overrides: &self.overrides,
            dark_overrides: &self.dark_overrides,
            defaults: &self.defaults,
            dark_defaults: &self.dark_defaults,
        };
        for node in self.nodes.values_mut() {
            let refs: Vec<(u16, String)> = node.token_refs.iter().map(|(k, v)| (*k, v.clone())).collect();
            for (prop, path) in refs {
                if let Some(value) = resolve_token(&path, self.scheme, &tables) {
                    match value {
                        TokenValue::Str(s) => {
                            node.strings.insert(prop, s);
                        }
                        _ => {
                            if let Some(bits) = value.to_bits() {
                                node.properties.insert(prop, bits);
                            }
                        }
                    }
                }
            }
        }
    }

    /// Apply a frame's opcodes to this tree (a delta).
    pub fn apply_frame(&mut self, frame: &Frame<'_>) {
        for op in frame.opcodes() {
            match (op.category(), op.command()) {
                (category::TREE, tree::CREATE_NODE) => {
                    let id = op.a();
                    let ty = op.b() as u16;
                    let entry = self.nodes.entry(id).or_default();
                    entry.id = id;
                    entry.component_type = ty;
                }
                (category::TREE, tree::DELETE_NODE) => {
                    self.nodes.remove(&op.a());
                }
                (category::TREE, tree::INSERT_CHILD) => {
                    let (parent, child) = (op.a(), op.b());
                    if let Some(p) = self.nodes.get_mut(&parent) {
                        if !p.children.contains(&child) {
                            p.children.push(child);
                        }
                    }
                    if let Some(c) = self.nodes.get_mut(&child) {
                        c.parent = Some(parent);
                    }
                }
                (category::TREE, tree::REMOVE_CHILD) => {
                    let (parent, child) = (op.a(), op.b());
                    if let Some(p) = self.nodes.get_mut(&parent) {
                        p.children.retain(|c| *c != child);
                    }
                    if let Some(c) = self.nodes.get_mut(&child) {
                        c.parent = None;
                    }
                }
                (category::TREE, tree::MOVE_CHILD) => {
                    let (parent, child, new_index) = (op.a(), op.b(), op.c() as usize);
                    if let Some(p) = self.nodes.get_mut(&parent) {
                        if let Some(pos) = p.children.iter().position(|c| *c == child) {
                            p.children.remove(pos);
                        }
                        let idx = if new_index == usize::MAX {
                            p.children.len()
                        } else {
                            new_index.min(p.children.len())
                        };
                        p.children.insert(idx, child);
                    }
                }
                (category::PARAMETER, parameter::SET_PROPERTY) => {
                    let prop_id = op.b() as u16;
                    let vt = (op.b() >> 16) as u8;
                    if let Some(n) = self.nodes.get_mut(&op.a()) {
                        if vt == value_type::STRING {
                            // Resolve the arena offset into the actual text.
                            if let Ok(text) = frame.arena_str(op.c()) {
                                n.strings.insert(prop_id, text.to_string());
                            }
                        } else if vt == value_type::DESIGN_TOKEN {
                            // A token reference: keep the path for resolution
                            // against the theme (spec/TOKENS.md). C is the arena
                            // offset of the token path.
                            if let Ok(path) = frame.arena_str(op.c()) {
                                n.token_refs.insert(prop_id, path.to_string());
                            }
                        } else {
                            n.properties.insert(prop_id, op.c());
                        }
                    }
                }
                (category::PARAMETER, parameter::SET_TEXT) => {
                    if let Some(n) = self.nodes.get_mut(&op.a()) {
                        n.text = frame.arena_str(op.b()).ok().map(|s| s.to_string());
                    }
                }
                (category::PARAMETER, parameter::SET_DESIGN_TOKEN) => {
                    // Global override: A = arenaRef (token path), B = valueType,
                    // C = value (for STRING, an arenaRef to the value string).
                    if let Ok(path) = frame.arena_str(op.a()) {
                        let vt = (op.b() & 0xFF) as u8;
                        let value = if vt == value_type::STRING {
                            frame
                                .arena_str(op.c())
                                .map(|s| TokenValue::Str(s.to_string()))
                                .unwrap_or(TokenValue::U32(0))
                        } else {
                            TokenValue::from_wire(vt, op.c()).unwrap_or(TokenValue::U32(op.c()))
                        };
                        if let Some(bare) = path.strip_prefix("dark.") {
                            self.dark_overrides.insert(bare.to_string(), value);
                        } else {
                            self.overrides.insert(path.to_string(), value);
                        }
                    }
                }
                _ => {}
            }
        }
        self.resolve_tokens();
    }

    /// Look up a node by id.
    pub fn node(&self, id: u32) -> Option<&HostNode> {
        self.nodes.get(&id)
    }

    /// The root node: the single node without a parent.
    pub fn root(&self) -> Option<&HostNode> {
        self.nodes.values().find(|n| n.parent.is_none())
    }
}

/// Decode a frame into a fresh `RenderTree`.
pub fn render_tree_from_frame(frame: &Frame<'_>) -> RenderTree {
    let mut tree = RenderTree::default();
    tree.apply_frame(frame);
    tree
}

/// Concrete Tier-1 design-token defaults (light + dark) — platform-appropriate
/// fallbacks used headless and as the base a renderer's platform-native
/// enrichment overwrites. Values are renderer-owned; apps override via
/// `PARAMETER::SET_DESIGN_TOKEN` (spec/TOKENS.md).
pub fn concrete_default_tables() -> (
    BTreeMap<String, TokenValue>,
    BTreeMap<String, TokenValue>,
) {
    fn m(pairs: &[(&str, TokenValue)]) -> BTreeMap<String, TokenValue> {
        pairs
            .iter()
            .map(|(k, v)| ((*k).to_string(), v.clone()))
            .collect()
    }
    let base = m(&[
        ("color.background", TokenValue::Color(0xFF_FFFFFF)),
        ("color.primary", TokenValue::Color(0xFF_2563EB)),
        ("color.secondary", TokenValue::Color(0xFF_4F46E5)),
        ("color.surface", TokenValue::Color(0xFF_FFFFFF)),
        ("color.text.primary", TokenValue::Color(0xFF_111827)),
        ("color.text.secondary", TokenValue::Color(0xFF_6B7280)),
        ("color.text.onPrimary", TokenValue::Color(0xFF_FFFFFF)),
        ("color.border", TokenValue::Color(0xFF_E5E7EB)),
        ("color.accent", TokenValue::Color(0xFF_2563EB)),
        ("color.separator", TokenValue::Color(0xFF_E5E7EB)),
        ("color.success", TokenValue::Color(0xFF_16A34A)),
        ("color.warning", TokenValue::Color(0xFF_D97706)),
        ("color.danger", TokenValue::Color(0xFF_DC2626)),
        ("color.info", TokenValue::Color(0xFF_2563EB)),
        ("font.body.size", TokenValue::F32(16.0)),
        ("font.body.weight", TokenValue::F32(400.0)),
        ("font.body.family", TokenValue::Str("Inter, sans-serif".into())),
        ("space.base", TokenValue::F32(4.0)),
        ("radius.xs", TokenValue::F32(2.0)),
        ("radius.sm", TokenValue::F32(6.0)),
        ("radius.md", TokenValue::F32(6.0)),
        ("radius.lg", TokenValue::F32(8.0)),
        ("radius.xl", TokenValue::F32(12.0)),
        ("radius.full", TokenValue::F32(9999.0)),
        ("border.width.thin", TokenValue::F32(1.0)),
        ("control.background", TokenValue::Color(0xFF_FFFFFF)),
        ("control.foreground", TokenValue::Color(0xFF_111827)),
        ("control.border", TokenValue::Color(0xFF_D1D5DB)),
        ("control.border.width", TokenValue::F32(1.0)),
        ("control.radius", TokenValue::F32(6.0)),
        ("control.height", TokenValue::F32(36.0)),
        ("control.padding.horizontal", TokenValue::F32(12.0)),
        ("control.padding.vertical", TokenValue::F32(6.0)),
        ("control.font.size", TokenValue::F32(14.0)),
        ("control.font.weight", TokenValue::F32(600.0)),
        ("control.accent", TokenValue::Color(0xFF_2563EB)),
        ("button.background", TokenValue::Color(0xFF_FFFFFF)),
        ("button.foreground", TokenValue::Color(0xFF_111827)),
        ("button.border", TokenValue::Color(0xFF_D1D5DB)),
        ("button.radius", TokenValue::F32(6.0)),
        ("button.accent", TokenValue::Color(0xFF_2563EB)),
        ("input.background", TokenValue::Color(0xFF_FFFFFF)),
        ("input.foreground", TokenValue::Color(0xFF_111827)),
        ("input.placeholder", TokenValue::Color(0xFF_9CA3AF)),
        ("input.border", TokenValue::Color(0xFF_D1D5DB)),
        ("input.radius", TokenValue::F32(6.0)),
        ("input.font.size", TokenValue::F32(16.0)),
    ]);
    let dark = m(&[
        ("color.background", TokenValue::Color(0xFF_0F172A)),
        ("color.primary", TokenValue::Color(0xFF_60A5FA)),
        ("color.secondary", TokenValue::Color(0xFF_818CF8)),
        ("color.surface", TokenValue::Color(0xFF_111827)),
        ("color.text.primary", TokenValue::Color(0xFF_F9FAFB)),
        ("color.text.secondary", TokenValue::Color(0xFF_9CA3AF)),
        ("color.text.onPrimary", TokenValue::Color(0xFF_0F172A)),
        ("color.border", TokenValue::Color(0xFF_374151)),
        ("color.accent", TokenValue::Color(0xFF_60A5FA)),
        ("color.separator", TokenValue::Color(0xFF_374151)),
        ("color.success", TokenValue::Color(0xFF_4ADE80)),
        ("color.warning", TokenValue::Color(0xFF_FBBF24)),
        ("color.danger", TokenValue::Color(0xFF_F87171)),
        ("color.info", TokenValue::Color(0xFF_60A5FA)),
        ("font.body.size", TokenValue::F32(16.0)),
        ("font.body.weight", TokenValue::F32(400.0)),
        ("font.body.family", TokenValue::Str("Inter, sans-serif".into())),
        ("space.base", TokenValue::F32(4.0)),
        ("control.background", TokenValue::Color(0xFF_1F2937)),
        ("control.foreground", TokenValue::Color(0xFF_F9FAFB)),
        ("control.border", TokenValue::Color(0xFF_374151)),
        ("control.border.width", TokenValue::F32(1.0)),
        ("control.radius", TokenValue::F32(6.0)),
        ("control.height", TokenValue::F32(36.0)),
        ("control.padding.horizontal", TokenValue::F32(12.0)),
        ("control.padding.vertical", TokenValue::F32(6.0)),
        ("control.font.size", TokenValue::F32(14.0)),
        ("control.font.weight", TokenValue::F32(600.0)),
        ("control.accent", TokenValue::Color(0xFF_60A5FA)),
        ("button.background", TokenValue::Color(0xFF_1F2937)),
        ("button.foreground", TokenValue::Color(0xFF_F9FAFB)),
        ("button.border", TokenValue::Color(0xFF_374151)),
        ("button.radius", TokenValue::F32(6.0)),
        ("button.accent", TokenValue::Color(0xFF_60A5FA)),
        ("input.background", TokenValue::Color(0xFF_1F2937)),
        ("input.foreground", TokenValue::Color(0xFF_FFFFFF)),
        ("input.placeholder", TokenValue::Color(0xFF_6B7280)),
        ("input.border", TokenValue::Color(0xFF_374151)),
        ("input.radius", TokenValue::F32(6.0)),
        ("input.font.size", TokenValue::F32(16.0)),
    ]);
    (base, dark)
}

/// Convert a single opcode into a human-readable line (debugging aid).
pub fn describe(op: &Opcode) -> String {
    match (op.category(), op.command()) {
        (category::TREE, tree::CREATE_NODE) => {
            format!("TREE:CREATE_NODE id={} type=0x{:04x}", op.a(), op.b() as u16)
        }
        (category::TREE, tree::DELETE_NODE) => format!("TREE:DELETE_NODE id={}", op.a()),
        (category::TREE, tree::INSERT_CHILD) => format!(
            "TREE:INSERT_CHILD parent={} child={} index={}",
            op.a(),
            op.b(),
            op.c()
        ),
        (category::TREE, tree::REMOVE_CHILD) => {
            format!("TREE:REMOVE_CHILD parent={} child={}", op.a(), op.b())
        }
        (category::TREE, tree::MOVE_CHILD) => format!(
            "TREE:MOVE_CHILD parent={} child={} index={}",
            op.a(),
            op.b(),
            op.c()
        ),
        (category::PARAMETER, parameter::SET_PROPERTY) => format!(
            "PARAMETER:SET_PROPERTY id={} prop=0x{:04x} value=0x{:08x}",
            op.a(),
            op.b() as u16,
            op.c()
        ),
        (category::PARAMETER, parameter::SET_TEXT) => {
            format!("PARAMETER:SET_TEXT id={} arenaRef={}", op.a(), op.b())
        }
        (category::PARAMETER, parameter::SET_DESIGN_TOKEN) => format!(
            "PARAMETER:SET_DESIGN_TOKEN pathArenaRef={} valueType=0x{:02x} value=0x{:08x}",
            op.a(),
            op.b() & 0xFF,
            op.c()
        ),
        _ => format!("{:?}", op),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use pathland_engine::Engine;
    use pathland_core::{init_memory, Guest, MemoryLayout};
    use pathland_view::{assign_ids, text, vstack, View, ViewExt};

    #[test]
    fn decodes_a_declarative_frame() {
        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        let mut root = vstack![text("ab"), text("cd")]
            .spacing(4.0)
            .padding(8.0)
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
        let frame = &frames[0];
        let tree = render_tree_from_frame(frame);

        assert_eq!(tree.nodes.len(), 3);
        let root_node = tree.node(1).unwrap();
        assert_eq!(root_node.component_type, component_type::VSTACK);
        assert_eq!(
            root_node.properties.get(&pathland_core::property_id::SPACING),
            Some(&4.0f32.to_bits())
        );
        let child = tree.node(2).unwrap();
        assert_eq!(child.text.as_deref(), Some("ab"));
        // No layout/rect data is present.
        assert!(!child.properties.contains_key(&pathland_core::property_id::WIDTH));
    }

    #[test]
    fn applies_a_delta_frame() {
        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        let mut engine = Engine::new();
        let mut tree = RenderTree::default();

        let mut a = vstack![text("ab")]
            .spacing(4.0)
            .padding(8.0)
            .build();
        assign_ids(&mut a, &mut 1);
        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            engine.emit(&a, &mut guest).unwrap();
            guest.end_frame();
        }
        {
            let mut host = pathland_core::Host::new(&mut mem, &layout);
            tree.apply_frame(&host.frames()[0]);
        }

        // Delta: spacing 4 -> 12.
        let mut b = vstack![text("ab")]
            .spacing(12.0)
            .padding(8.0)
            .build();
        assign_ids(&mut b, &mut 1);
        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            engine.emit(&b, &mut guest).unwrap();
            guest.end_frame();
        }
        {
            let mut host = pathland_core::Host::new(&mut mem, &layout);
            tree.apply_frame(&host.frames()[0]);
        }

        assert_eq!(tree.nodes.len(), 2);
        let root = tree.node(1).unwrap();
        assert_eq!(
            root.properties.get(&pathland_core::property_id::SPACING),
            Some(&12.0f32.to_bits())
        );
    }

    #[test]
    fn string_properties_resolve_from_frame() {
        use pathland_core::{Guest, Host, MemoryLayout, init_memory, property_id, value_type};

        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        {
            let mut guest = Guest::new(&mut mem, &layout);
            guest.begin_frame();
            guest.create_node(1, component_type::IMAGE).unwrap();
            let ref_src = guest.alloc_str("assets/logo.png").unwrap();
            guest
                .set_property(1, property_id::IMAGE_SOURCE, value_type::STRING, ref_src)
                .unwrap();
            guest.end_frame();
        }
        {
            let mut host = Host::new(&mut mem, &layout);
            let mut tree = RenderTree::default();
            tree.apply_frame(&host.frames()[0]);

            let n = tree.node(1).unwrap();
            assert_eq!(n.component_type, component_type::IMAGE);
            // The STRING property is resolved to its text...
            assert_eq!(
                n.string_property(property_id::IMAGE_SOURCE),
                Some("assets/logo.png")
            );
            // ...and is NOT stored as a raw arena offset in `properties`.
            assert!(!n.properties.contains_key(&property_id::IMAGE_SOURCE));
        }
    }
}