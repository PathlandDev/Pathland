//! Diff-based reactive emitter.
//!
//! The engine holds the application's retained view tree and emits the
//! declarative structure as `TREE`/`PARAMETER` opcodes into the 16-byte ring
//! buffer (VStack, HStack, Text, spacing, padding, alignment). It does not
//! compute rects or layout; native renderers lay out their own elements.
//!
//! Emission is diff-based: on each `emit` the engine compares the current tree
//! against a snapshot of what it last emitted and writes only the opcodes for
//! nodes that actually changed. An unchanged tree emits zero opcodes.
//!
//! The emit pass performs zero dynamic heap allocations in the steady state.
//!
//! ## Signals
//!
//! Nodes may bind their text or a property to a [`crate::SignalId`]. The engine
//! resolves those bindings during emit (recording the dependency) and re-emits
//! only the affected nodes on [`Engine::set_signal`] — no full tree walk.

use alloc::borrow::ToOwned;
use alloc::collections::BTreeMap;
use alloc::string::{String, ToString};
use alloc::vec::Vec;

use pathland_core::{value_type, value_type_for};
use pathland_core::Guest;

use crate::node::{component_type_id, Component, Node};
use crate::signal::{Dep, SignalId, SignalStore, SignalValue};

/// Per-node state the engine remembers from the last emission.
#[derive(Debug, Clone, PartialEq)]
struct SnapshotNode {
    component_type: u16,
    parent: Option<u32>,
    index: usize,
    text: Option<String>,
    properties: BTreeMap<u16, u32>,
    /// Last-emitted `STRING` properties: property → (value, arena offset). The
    /// arena offset is reused across passes so an unchanged string never
    /// re-allocates into the bump arena.
    string_properties: BTreeMap<u16, (String, u32)>,
    /// Last-emitted `DESIGN_TOKEN` refs: property → (token path, arena offset).
    /// The arena offset is reused across passes so an unchanged ref never
    /// re-allocates into the bump arena.
    token_refs: BTreeMap<u16, (String, u32)>,
    /// Generation stamp set on each emit pass; used to detect deleted nodes.
    gen: u64,
}

/// Result of an emission pass.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct EmitResult {
    /// Number of opcodes written to the ring.
    pub opcodes: usize,
}

/// The diff-based reactive emitter.
///
/// `Engine` keeps the snapshot of what was last emitted and owns the signal
/// store. Calling `emit` with a (possibly mutated) retained tree writes only
/// the delta opcodes; `set_signal` writes deltas for only the bound nodes.
pub struct Engine {
    /// nodeId to last emitted state (a preallocated slab; node ids are dense).
    snapshot: Vec<Option<SnapshotNode>>,
    /// Monotonic generation stamp for the current pass.
    gen: u64,
    /// Reactive signal store (owns values + the reverse dependency index).
    store: SignalStore,
}

impl Default for Engine {
    fn default() -> Self {
        Self::new()
    }
}

impl Engine {
    pub fn new() -> Self {
        Self {
            snapshot: Vec::new(),
            gen: 0,
            store: SignalStore::default(),
        }
    }

    /// Reconcile `root` against the snapshot, emitting only changed opcodes.
    pub fn emit(
        &mut self,
        root: &Node,
        guest: &mut Guest<'_>,
    ) -> Result<EmitResult, pathland_core::RingError> {
        self.gen = self.gen.wrapping_add(1);
        // Rebuild the signal dependency index from scratch; reconcile re-adds
        // the bindings this tree actually uses, so stale subscriptions vanish.
        self.store.clear_deps();
        let mut out = 0usize;
        self.reconcile_node(root, None, 0, guest, &mut out)?;

        for (id, slot) in self.snapshot.iter_mut().enumerate() {
            if let Some(s) = slot {
                if s.gen != self.gen {
                    guest.delete_node(id as u32)?;
                    out += 1;
                    *slot = None;
                }
            }
        }
        Ok(EmitResult { opcodes: out })
    }

    /// Number of nodes tracked in the snapshot (diagnostics).
    pub fn tracked_nodes(&self) -> usize {
        self.snapshot.iter().filter(|s| s.is_some()).count()
    }

    /// Emit a global design-token override (`PARAMETER::SET_DESIGN_TOKEN`).
    /// Overrides are renderer-global and apply everywhere the renderer resolves
    /// tokens; a `dark.`-prefixed path overrides the dark variant
    /// (spec/TOKENS.md). Returns the arena offset of the token path.
    pub fn set_design_token(
        &mut self,
        path: &str,
        value_type: u8,
        value: u32,
        guest: &mut Guest<'_>,
    ) -> Result<u32, pathland_core::RingError> {
        guest.set_design_token(path, value_type, value)
    }

    /// Emit a batch of global design-token overrides ([`Theme`](crate::Theme))
    /// as `PARAMETER::SET_DESIGN_TOKEN` opcodes. `STRING`-valued overrides allocate
    /// their value into the guest arena first (the wire carries an arena ref).
    ///
    /// Overrides are renderer-global: they never re-emit nodes and are not part
    /// of the diff. Emit once at mount, before the tree.
    pub fn apply_theme(
        &mut self,
        theme: &crate::Theme,
        guest: &mut Guest<'_>,
    ) -> Result<(), pathland_core::RingError> {
        for (path, value) in theme.iter() {
            self.emit_token_override(path, value, guest)?;
        }
        Ok(())
    }

    /// Emit an adaptive theme ([`AdaptiveTheme`](crate::AdaptiveTheme)): the
    /// **light** theme's overrides as base tokens, the **dark** theme's with the
    /// `dark.` scheme prefix (spec/TOKENS.md).
    pub fn apply_adaptive_theme(
        &mut self,
        adaptive: &crate::AdaptiveTheme,
        guest: &mut Guest<'_>,
    ) -> Result<(), pathland_core::RingError> {
        self.apply_theme(&adaptive.light, guest)?;
        for (path, value) in adaptive.dark.iter() {
            let dark_path = if path.starts_with("dark.") {
                path.to_string()
            } else {
                alloc::format!("dark.{path}")
            };
            self.emit_token_override(&dark_path, value, guest)?;
        }
        Ok(())
    }

    /// Emit one `SET_DESIGN_TOKEN` override, arena-allocating STRING values.
    fn emit_token_override(
        &mut self,
        path: &str,
        value: &pathland_core::tokens::TokenValue,
        guest: &mut Guest<'_>,
    ) -> Result<(), pathland_core::RingError> {
        use pathland_core::tokens::TokenValue;
        match value {
            TokenValue::Str(s) => {
                let ref_ = guest
                    .alloc_str(s)
                    .map_err(|_| pathland_core::RingError::Full)?;
                guest.set_design_token(path, value.value_type(), ref_)?;
            }
            _ => {
                if let Some(bits) = value.to_bits() {
                    guest.set_design_token(path, value.value_type(), bits)?;
                }
            }
        }
        Ok(())
    }

    // --- Signals ---

    /// Create a new signal with an initial value.
    pub fn create_signal(&mut self, value: SignalValue) -> SignalId {
        self.store.create(value)
    }

    /// Read a signal's current value.
    pub fn get_signal(&self, id: SignalId) -> Option<&SignalValue> {
        self.store.get(id)
    }

    /// Replace a signal's value and re-emit only the nodes that depend on it.
    ///
    /// Writes `SET_PROPERTY` / `SET_TEXT` deltas for the bound nodes whose
    /// materialized value changed. Returns the number of opcodes written.
    pub fn set_signal(
        &mut self,
        id: SignalId,
        value: SignalValue,
        guest: &mut Guest<'_>,
    ) -> Result<usize, pathland_core::RingError> {
        if !self.store.set(id, value) {
            return Ok(0);
        }
        let mut out = 0usize;
        let deps: Vec<Dep> = self.store.deps(id).to_vec();
        for dep in deps {
            match dep {
                Dep::Text { node } => {
                    let new_text: Option<String> = self
                        .store
                        .get(id)
                        .and_then(SignalValue::to_text)
                        .map(ToOwned::to_owned);
                    let prev_text = self.snapshot_text(node);
                    if new_text.as_deref() != prev_text {
                        if let Some(t) = new_text.as_deref() {
                            guest.set_text(node, t)?;
                            out += 1;
                        }
                        if let Some(s) = self.snapshot_mut(node) {
                            s.text = new_text;
                        }
                    }
                }
                Dep::Property { node, prop } => {
                    let new_value = self
                        .store
                        .get(id)
                        .and_then(SignalValue::to_property_u32);
                    let prev_value = self.snapshot_property(node, prop);
                    if new_value != prev_value {
                        if let Some(v) = new_value {
                            guest.set_property(node, prop, value_type_for(prop), v)?;
                            out += 1;
                        }
                        if let Some(s) = self.snapshot_mut(node) {
                            match new_value {
                                Some(v) => {
                                    s.properties.insert(prop, v);
                                }
                                None => {
                                    s.properties.remove(&prop);
                                }
                            }
                        }
                    }
                }
            }
        }
        Ok(out)
    }

    fn snapshot_mut(&mut self, node: u32) -> Option<&mut SnapshotNode> {
        self.snapshot
            .get_mut(node as usize)
            .and_then(|s| s.as_mut())
    }

    fn snapshot_text(&self, node: u32) -> Option<&str> {
        self.snapshot
            .get(node as usize)
            .and_then(|s| s.as_ref())
            .and_then(|s| s.text.as_deref())
    }

    fn snapshot_property(&self, node: u32, prop: u16) -> Option<u32> {
        self.snapshot
            .get(node as usize)
            .and_then(|s| s.as_ref())
            .and_then(|s| s.properties.get(&prop).copied())
    }

    fn slot_mut(&mut self, id: u32) -> &mut Option<SnapshotNode> {
        let idx = id as usize;
        if self.snapshot.len() <= idx {
            self.snapshot.resize(idx + 1, None);
        }
        &mut self.snapshot[idx]
    }

    #[allow(clippy::too_many_arguments)]
    fn reconcile_node(
        &mut self,
        node: &Node,
        parent: Option<u32>,
        index: usize,
        guest: &mut Guest<'_>,
        out: &mut usize,
    ) -> Result<(), pathland_core::RingError> {
        let component_type = component_type_id(&node.component);

        // Resolve bindings (owned) before taking the mutable snapshot borrow.
        let bound_text: Option<String> = node
            .text_binding
            .and_then(|sid| self.store.get(sid))
            .and_then(SignalValue::to_text)
            .map(ToOwned::to_owned);

        let merged_props: Option<BTreeMap<u16, u32>> = if node.property_bindings.is_empty() {
            None
        } else {
            let mut m = node.properties.clone();
            for (prop, sid) in &node.property_bindings {
                if let Some(v) = self.store.get(*sid).and_then(SignalValue::to_property_u32) {
                    m.insert(*prop, v);
                }
            }
            Some(m)
        };

        let text_borrowed: Option<&str> = match (&bound_text, &node.component) {
            (Some(s), _) => Some(s.as_str()),
            (None, Component::Text { text }) => Some(text.as_str()),
            (None, Component::Button { label }) => Some(label.as_str()),
            _ => None,
        };
        let props: &BTreeMap<u16, u32> = merged_props.as_ref().unwrap_or(&node.properties);
        let token_refs = &node.token_properties;
        let strings = &node.string_properties;

        let prev = self.slot_mut(node.id).take();
        let token_changed = match &prev {
            Some(p) => p.token_refs.len() != token_refs.len()
                || p.token_refs
                    .iter()
                    .any(|(prop, (old, _))| token_refs.get(prop) != Some(old)),
            None => !token_refs.is_empty(),
        };
        let strings_changed = match &prev {
            Some(p) => {
                p.string_properties.len() != strings.len()
                    || p.string_properties
                        .iter()
                        .any(|(prop, (old, _))| strings.get(prop) != Some(old))
            }
            None => !strings.is_empty(),
        };
        let props_changed = match &prev {
            Some(p) => &p.properties != props || token_changed || strings_changed,
            None => !props.is_empty() || !token_refs.is_empty() || !strings.is_empty(),
        };
        let text_changed = match &prev {
            Some(p) => p.text.as_deref() != text_borrowed,
            None => text_borrowed.is_some(),
        };

        // Token-ref emission. Built only on mount or when a ref changed, so a
        // steady-state pass performs no heap allocations (the unchanged refs'
        // arena offsets are reused from the previous snapshot below).
        let mut token_emit: Option<BTreeMap<u16, (String, u32)>> = None;
        // String-property emission, built only on mount / when a value changed
        // (arena offsets reused from the previous snapshot otherwise).
        let mut string_emit: Option<BTreeMap<u16, (String, u32)>> = None;

        // Properties in two passes — literals (skipping token-ref'd ids) then
        // token refs — so the steady-state emit performs no heap allocations.
        match &prev {
            None => {
                guest.create_node(node.id, component_type)?;
                *out += 1;
                if let Some(p) = parent {
                    guest.insert_child(p, node.id, index as u32)?;
                    *out += 1;
                }
                for (prop, value) in props {
                    if token_refs.contains_key(prop) {
                        continue;
                    }
                    guest.set_property(node.id, *prop, value_type_for(*prop), *value)?;
                    *out += 1;
                }
                if !token_refs.is_empty() {
                    let mut m = BTreeMap::new();
                    for (prop, path) in token_refs {
                        let arena = guest
                            .alloc_str(path)
                            .map_err(|_| pathland_core::RingError::Full)?;
                        guest.set_property(node.id, *prop, value_type::DESIGN_TOKEN, arena)?;
                        *out += 1;
                        m.insert(*prop, (path.clone(), arena));
                    }
                    token_emit = Some(m);
                }
                if !strings.is_empty() {
                    let mut m = BTreeMap::new();
                    for (prop, value) in strings {
                        let arena = guest
                            .alloc_str(value)
                            .map_err(|_| pathland_core::RingError::Full)?;
                        guest.set_property(node.id, *prop, value_type::STRING, arena)?;
                        *out += 1;
                        m.insert(*prop, (value.clone(), arena));
                    }
                    string_emit = Some(m);
                }
                if let Some(t) = text_borrowed {
                    guest.set_text(node.id, t)?;
                    *out += 1;
                }
            }
            Some(p) => {
                if p.parent != parent {
                    if let Some(old) = p.parent {
                        guest.remove_child(old, node.id)?;
                        *out += 1;
                    }
                    if let Some(new) = parent {
                        guest.insert_child(new, node.id, index as u32)?;
                        *out += 1;
                    }
                } else if p.index != index {
                    if let Some(par) = parent {
                        guest.move_child(par, node.id, index as u32)?;
                        *out += 1;
                    }
                }
                if p.component_type != component_type {
                    guest.create_node(node.id, component_type)?;
                    *out += 1;
                }
                if text_changed {
                    if let Some(t) = text_borrowed {
                        guest.set_text(node.id, t)?;
                        *out += 1;
                    }
                }
                for (prop, value) in props {
                    if token_refs.contains_key(prop) {
                        continue;
                    }
                    if p.properties.get(prop) != Some(value) {
                        guest.set_property(node.id, *prop, value_type_for(*prop), *value)?;
                        *out += 1;
                    }
                }
                if token_changed {
                    let mut m = BTreeMap::new();
                    for (prop, path) in token_refs {
                        let (arena, changed) = match p.token_refs.get(prop) {
                            Some((old_path, old_offset)) if old_path == path => (*old_offset, false),
                            _ => {
                                let offset = guest
                                    .alloc_str(path)
                                    .map_err(|_| pathland_core::RingError::Full)?;
                                (offset, true)
                            }
                        };
                        m.insert(*prop, (path.clone(), arena));
                        if changed {
                            guest.set_property(node.id, *prop, value_type::DESIGN_TOKEN, arena)?;
                            *out += 1;
                        }
                    }
                    token_emit = Some(m);
                }
                if strings_changed {
                    let mut m = BTreeMap::new();
                    for (prop, value) in strings {
                        let (arena, changed) = match p.string_properties.get(prop) {
                            Some((old_value, old_offset)) if old_value == value => {
                                (*old_offset, false)
                            }
                            _ => {
                                let offset = guest
                                    .alloc_str(value)
                                    .map_err(|_| pathland_core::RingError::Full)?;
                                (offset, true)
                            }
                        };
                        m.insert(*prop, (value.clone(), arena));
                        if changed {
                            guest.set_property(node.id, *prop, value_type::STRING, arena)?;
                            *out += 1;
                        }
                    }
                    string_emit = Some(m);
                }
            }
        }

        let mut prev_props = None;
        let mut prev_text = None;
        let mut prev_token_refs = None;
        let mut prev_strings = None;
        if let Some(p) = prev {
            if !props_changed {
                prev_props = Some(p.properties);
            }
            if !text_changed {
                prev_text = p.text;
            }
            if !token_changed {
                prev_token_refs = Some(p.token_refs);
            }
            if !strings_changed {
                prev_strings = Some(p.string_properties);
            }
            let _ = p;
        }
        let properties = match prev_props {
            Some(props) => props,
            None => props.clone(),
        };
        let text = match prev_text {
            Some(t) => Some(t),
            None => text_borrowed.map(|s| s.to_owned()),
        };
        let token_refs_snapshot = match token_emit {
            Some(m) => m,
            None => prev_token_refs.unwrap_or_default(),
        };
        let string_properties = match string_emit {
            Some(m) => m,
            None => prev_strings.unwrap_or_default(),
        };
        *self.slot_mut(node.id) = Some(SnapshotNode {
            component_type,
            parent,
            index,
            text,
            properties,
            string_properties,
            token_refs: token_refs_snapshot,
            gen: self.gen,
        });

        // Record signal dependencies for this node.
        if let Some(sid) = node.text_binding {
            self.store.add_dep(sid, Dep::Text { node: node.id });
        }
        for (prop, sid) in &node.property_bindings {
            self.store.add_dep(*sid, Dep::Property { node: node.id, prop: *prop });
        }

        for (i, child) in node.children.iter().enumerate() {
            self.reconcile_node(child, Some(node.id), i, guest, out)?;
        }
        Ok(())
    }
}
