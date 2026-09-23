//! The Qt renderer: shared decode core (`RenderTree`) + a sent-state cache
//! that produces the delta command batch handed to the C++ Qt layer.
//!
//! The engine emits diffs, and this renderer forwards diffs: it keeps a mirror
//! of what the C++ layer already has (`sent`) and, per frame, emits only the
//! commands that change it — an unchanged tree emits **zero** commands. The C++
//! layer stays dumb (applies the batch, resets to renderer defaults on
//! `RESET_NODE`).

use std::collections::{HashMap, HashSet};

use pathland_core::{value_type, Frame};
use pathland_render_core::RenderTree;

use crate::ffi::Command;

/// Mirror of the C++ layer's state for one node.
#[derive(Debug, Default)]
struct SentNode {
    text: Option<String>,
    props: HashMap<u16, u32>,
    strings: HashMap<u16, String>,
}

/// Pseudo-parent for tree roots: id `0` maps to the Qt window's contentItem.
pub const CONTENT_ITEM: u32 = 0;

/// The retained Qt renderer. Owns the decoded tree (shared core) and the
/// sent-state cache used to produce deltas.
pub struct QtRenderer {
    tree: RenderTree,
    sent: HashMap<u32, SentNode>,
    sent_children: HashMap<u32, Vec<u32>>,
}

impl Default for QtRenderer {
    fn default() -> Self {
        Self::new()
    }
}

impl QtRenderer {
    pub fn new() -> Self {
        Self {
            tree: RenderTree::default(),
            sent: HashMap::new(),
            sent_children: HashMap::new(),
        }
    }

    /// The decoded tree (shared renderer core).
    pub fn tree(&self) -> &RenderTree {
        &self.tree
    }

    /// Apply one opcode frame and forward the resulting delta batch to the
    /// C++ layer. Unchanged nodes produce no commands.
    pub fn apply_frame(&mut self, frame: &Frame<'_>) {
        self.tree.apply_frame(frame);
        let cmds = self.diff();
        crate::ffi::apply(&cmds);
    }

    /// Clear the whole scene (`META::RESET`): empty the tree and tell the C++
    /// layer to drop every widget.
    pub fn reset(&mut self) {
        self.tree = RenderTree::default();
        self.sent.clear();
        self.sent_children.clear();
        unsafe { crate::ffi::pathland_qt_layer_reset() };
    }

    /// Compute the delta batch vs the sent-state cache.
    ///
    /// Commands with strings point into `self.tree`'s owned strings; the
    /// returned batch is only valid while `self` is not mutated — callers must
    /// forward it immediately (as [`Self::apply_frame`] does).
    pub fn diff(&mut self) -> Vec<Command> {
        let mut cmds = Vec::new();

        // 1. Deletions first: nodes gone from the tree.
        let live: HashSet<u32> = self.tree.nodes.keys().copied().collect();
        let dead: Vec<u32> = self
            .sent
            .keys()
            .copied()
            .filter(|id| !live.contains(id))
            .collect();
        for id in dead {
            cmds.push(Command::delete_node(id));
            self.sent.remove(&id);
            self.sent_children.remove(&id);
        }

        // 2. Creates (pre-order, so a parent exists before its children are
        //    inserted, and a child before any insert references it).
        let order = self.pre_order();
        for &id in &order {
            if self.sent.contains_key(&id) {
                continue;
            }
            let node = self.tree.node(id).unwrap();
            cmds.push(Command::create_node(id, node.component_type));
            self.sent.insert(
                id,
                SentNode::default(),
            );
        }

        // 3. Text + property deltas. On any property change we re-send the
        //    node's *complete* property set (preceded by RESET_NODE), so the
        //    C++ layer can reset removed properties to its renderer defaults.
        for &id in &order {
            let node = self.tree.node(id).unwrap();
            let s = self.sent.get_mut(&id).unwrap();

            if s.text != node.text {
                cmds.push(Command::set_text(id, node.text.as_deref().unwrap_or("")));
                s.text = node.text.clone();
            }
            if node.properties != s.props || node.strings != s.strings {
                cmds.push(Command::reset_node(id));
                // Emit numeric props in a deterministic order with VALUE last:
                // Qt Quick clamps a control's `value` when `from`/`to` aren't
                // configured yet, so min/max must land before the value.
                let mut props: Vec<(u16, u32)> =
                    node.properties.iter().map(|(k, v)| (*k, *v)).collect();
                props.sort_by_key(|(p, _)| (*p == pathland_core::property_id::VALUE, *p));
                for (prop, value) in &props {
                    let vt = value_type_for(*prop);
                    cmds.push(Command::set_property(id, *prop, vt, *value));
                }
                for (prop, value) in &node.strings {
                    cmds.push(Command::set_string_property(id, *prop, value));
                }
                s.props = node.properties.clone();
                s.strings = node.strings.clone();
            }
        }

        // 4. Child order (parents before children already exist). The shared
        //    core's DELETE_NODE leaves a ghost in the parent's child list, so
        //    filter to live nodes before diffing.
        for &id in &order {
            let node = self.tree.node(id).unwrap();
            let children: Vec<u32> = node
                .children
                .iter()
                .copied()
                .filter(|c| self.tree.nodes.contains_key(c))
                .collect();
            let prev = self.sent_children.entry(id).or_default();
            cmds.extend(children_diff(id, prev, &children));
            *prev = children;
        }

        // 5. Roots are children of the pseudo-parent 0 (window contentItem).
        let mut roots: Vec<u32> = order
            .iter()
            .copied()
            .filter(|&id| self.tree.node(id).map_or(false, |n| n.parent.is_none()))
            .collect();
        roots.sort_unstable();
        let prev_roots = self.sent_children.entry(CONTENT_ITEM).or_default();
        cmds.extend(children_diff(CONTENT_ITEM, prev_roots, &roots));
        *prev_roots = roots;

        cmds
    }

    fn pre_order(&self) -> Vec<u32> {
        let mut order = Vec::new();
        let mut roots: Vec<u32> = self
            .tree
            .nodes
            .keys()
            .copied()
            .filter(|&id| self.tree.node(id).map_or(false, |n| n.parent.is_none()))
            .collect();
        roots.sort_unstable();
        for r in roots {
            self.walk(r, &mut order);
        }
        order
    }

    fn walk(&self, id: u32, out: &mut Vec<u32>) {
        // Guard against ghost children (a node deleted from `tree.nodes` but
        // still listed in a parent's `children` after DELETE_NODE).
        if !self.tree.nodes.contains_key(&id) {
            return;
        }
        out.push(id);
        if let Some(node) = self.tree.node(id) {
            for c in node.children.clone() {
                self.walk(c, out);
            }
        }
    }
}

/// The opcode wire's value type for a property (same mapping the emit side
/// uses; the C++ layer needs it to interpret the raw u32).
fn value_type_for(prop: u16) -> u8 {
    if is_f32_property(prop) {
        value_type::F32
    } else {
        value_type::U32
    }
}

fn is_f32_property(prop: u16) -> bool {
    matches!(
        prop,
        pathland_core::property_id::SPACING
            | pathland_core::property_id::WIDTH
            | pathland_core::property_id::HEIGHT
            | pathland_core::property_id::FONT_SIZE
            | pathland_core::property_id::OPACITY
            | pathland_core::property_id::BORDER_WIDTH
            | pathland_core::property_id::BORDER_RADIUS
            | pathland_core::property_id::PADDING
            | pathland_core::property_id::PADDING_TOP
            | pathland_core::property_id::PADDING_RIGHT
            | pathland_core::property_id::PADDING_BOTTOM
            | pathland_core::property_id::PADDING_LEFT
            | pathland_core::property_id::VALUE
            | pathland_core::property_id::MIN_VALUE
            | pathland_core::property_id::MAX_VALUE
            | pathland_core::property_id::STEP_VALUE
            | pathland_core::property_id::PROGRESS
    )
}

/// Minimal child-order delta: for each index where the old and new children
/// differ, remove the stale child and insert the new one at that index.
fn children_diff(parent: u32, prev: &[u32], next: &[u32]) -> Vec<Command> {
    let mut cmds = Vec::new();
    let len = prev.len().max(next.len());
    for i in 0..len {
        let old = prev.get(i).copied();
        let new = next.get(i).copied();
        if old == new {
            continue;
        }
        if let Some(c) = old {
            cmds.push(Command::remove_child(parent, c));
        }
        if let Some(c) = new {
            cmds.push(Command::insert_child(parent, c, i as u32));
        }
    }
    cmds
}

#[cfg(test)]
mod tests {
    use super::*;
    use pathland_core::{init_memory, Frame, Guest, Host, MemoryLayout};
    use pathland_engine::Engine;
    use pathland_view::{assign_ids, text, vstack, Node, View, ViewExt};

    /// One persistent engine + ring memory, so consecutive emits are **deltas**
    /// (like a live app) rather than full snapshots.
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

        /// Emit `root` (delta against the previous emit) and return the frame's
        /// slots + arena copied into owned buffers (`frame spans 0..len`).
        /// An unchanged tree emits **zero** opcodes (returns empty buffers).
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
            assert!(frames.len() <= 1, "at most one frame per emit");
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

    fn apply_emit(r: &mut QtRenderer, h: &mut Harness, root: &Node) {
        let (slots, arena) = h.emit(root);
        let frame = Frame::from_parts(&slots, &arena, 0, slots.len());
        r.tree.apply_frame(&frame);
    }

    #[test]
    fn first_frame_creates_tree_and_props() {
        let mut view = vstack![text("ab"), text("cd")].spacing(4.0).padding(8.0).build();
        assign_ids(&mut view, &mut 1);

        let mut h = Harness::new();
        let mut r = QtRenderer::new();
        apply_emit(&mut r, &mut h, &view);
        let cmds = r.diff();

        let kinds: Vec<u8> = cmds.iter().map(|c| c.kind).collect();
        // Creates for 1,2,3 then per-node reset+props/text, then children.
        assert!(kinds.contains(&Command::CREATE_NODE));
        assert!(kinds.contains(&Command::RESET_NODE));
        assert!(kinds.contains(&Command::SET_TEXT));
        assert!(kinds.contains(&Command::SET_PROPERTY));

        // Root (1) is created, reset, gets SPACING + PADDING props.
        let root_props: Vec<&Command> = cmds
            .iter()
            .filter(|c| c.kind == Command::SET_PROPERTY && c.a == 1)
            .collect();
        assert!(!root_props.is_empty());
        let spacing = root_props
            .iter()
            .any(|c| (c.b & 0xFFFF) as u16 == pathland_core::property_id::SPACING);
        assert!(spacing, "root carries SPACING");
    }

    #[test]
    fn unchanged_frame_emits_zero_commands() {
        let mut view = vstack![text("ab")].spacing(4.0).build();
        assign_ids(&mut view, &mut 1);
        let mut h = Harness::new();
        let mut r = QtRenderer::new();
        apply_emit(&mut r, &mut h, &view);
        let first = r.diff();
        assert!(!first.is_empty());

        // Re-emitting the same tree (same engine) changes nothing -> zero commands.
        apply_emit(&mut r, &mut h, &view);
        let second = r.diff();
        assert!(second.is_empty(), "unchanged tree must emit zero commands");
    }

    #[test]
    fn spacing_delta_emits_only_root_reset_and_prop() {
        let mut a = vstack![text("ab")].spacing(4.0).build();
        assign_ids(&mut a, &mut 1);
        let mut h = Harness::new();
        let mut r = QtRenderer::new();
        apply_emit(&mut r, &mut h, &a);
        let _ = r.diff();

        let mut b = vstack![text("ab")].spacing(12.0).build();
        assign_ids(&mut b, &mut 1);
        apply_emit(&mut r, &mut h, &b);
        let cmds = r.diff();

        assert_eq!(cmds.len(), 2, "only RESET + SET_PROPERTY for the root");
        assert_eq!(cmds[0].kind, Command::RESET_NODE);
        assert_eq!(cmds[0].a, 1);
        assert_eq!(cmds[1].kind, Command::SET_PROPERTY);
        assert_eq!(cmds[1].a, 1);
        assert_eq!(
            (cmds[1].b & 0xFFFF) as u16,
            pathland_core::property_id::SPACING
        );
        assert_eq!(cmds[1].c, 12.0f32.to_bits());
    }

    #[test]
    fn text_delta_emits_only_set_text() {
        let mut a = vstack![text("ab")].build();
        assign_ids(&mut a, &mut 1);
        let mut h = Harness::new();
        let mut r = QtRenderer::new();
        apply_emit(&mut r, &mut h, &a);
        let _ = r.diff();

        let mut b = vstack![text("xy")].build();
        assign_ids(&mut b, &mut 1);
        apply_emit(&mut r, &mut h, &b);
        let cmds = r.diff();

        assert_eq!(cmds.len(), 1);
        assert_eq!(cmds[0].kind, Command::SET_TEXT);
        assert_eq!(cmds[0].a, 2);
    }

    #[test]
    fn deleted_node_emits_delete() {
        let mut a = vstack![text("ab"), text("cd")].build();
        assign_ids(&mut a, &mut 1);
        let mut h = Harness::new();
        let mut r = QtRenderer::new();
        apply_emit(&mut r, &mut h, &a);
        let _ = r.diff();

        let mut b = vstack![text("ab")].build();
        assign_ids(&mut b, &mut 1);
        apply_emit(&mut r, &mut h, &b);
        let cmds = r.diff();

        assert!(
            cmds.iter().any(|c| c.kind == Command::DELETE_NODE && c.a == 3),
            "removed text node 3 is deleted"
        );
        assert!(
            cmds.iter().any(|c| c.kind == Command::REMOVE_CHILD && c.a == 1 && c.b == 3),
            "child 3 removed from root 1"
        );
    }

    #[test]
    fn children_diff_moves_and_appends() {
        // The DSL reorders by position (ids follow tree order), so a "swap"
        // is a text change, not a structural reorder. Exercise the structural
        // delta directly.
        let cmds = children_diff(1, &[2, 3], &[3, 2, 4]);
        assert!(
            cmds.iter().any(|c| c.kind == Command::REMOVE_CHILD && c.a == 1 && c.b == 2),
            "2 moved out"
        );
        assert!(
            cmds.iter().any(|c| c.kind == Command::INSERT_CHILD && c.a == 1 && c.b == 2 && c.c == 1),
            "2 reinserted at index 1"
        );
        assert!(
            cmds.iter().any(|c| c.kind == Command::INSERT_CHILD && c.a == 1 && c.b == 4 && c.c == 2),
            "4 appended at index 2"
        );

        // Identical lists emit nothing.
        assert!(children_diff(1, &[2, 3], &[2, 3]).is_empty());
    }

    #[test]
    fn roots_are_children_of_content_item() {
        let mut view = vstack![text("ab")].build();
        assign_ids(&mut view, &mut 1);
        let mut h = Harness::new();
        let mut r = QtRenderer::new();
        apply_emit(&mut r, &mut h, &view);
        let cmds = r.diff();
        assert!(
            cmds.iter().any(|c| c.kind == Command::INSERT_CHILD && c.a == CONTENT_ITEM && c.b == 1),
            "root 1 is inserted into the content item"
        );
    }
}