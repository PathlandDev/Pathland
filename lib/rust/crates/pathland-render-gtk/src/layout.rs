//! Pure opcode -> layout-decision mapping for stacks.
//!
//! This module is **display-free**: it translates a host node's component type
//! and constraint properties (`SPACING`, `ALIGNMENT`, `PADDING`, per-edge
//! padding) into plain data (`Orientation`, spacing, cross-axis `gtk::Align`,
//! margins) that the GTK layer then applies to native widgets.
//!
//! `gtk::Align`/`Orientation` are plain enums, not widgets, so none of this
//! needs GTK initialized - which keeps the mapping unit-testable headlessly.

use gtk::{Align, Orientation};
use crate::host::{HostNode, RenderTree};
use pathland_core::{component_type, property_id, size};

/// Map a component type to its native stack orientation.
///
/// `LAZY_VSTACK`/`LAZY_HSTACK` render as eager stacks (no GTK windowing), so
/// they share the stack orientation mapping.
pub fn stack_orientation(component_type: u16) -> Option<Orientation> {
    match component_type {
        component_type::VSTACK | component_type::LAZY_VSTACK => Some(Orientation::Vertical),
        component_type::HSTACK | component_type::LAZY_HSTACK => Some(Orientation::Horizontal),
        _ => None,
    }
}

/// The native layout a stack node maps onto.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct StackLayout {
    pub orientation: Orientation,
    pub spacing: i32,
    pub child_align: Align,
}

/// How a `WIDTH`/`HEIGHT` property maps to GTK sizing.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum SizeHint {
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
pub fn size_hint(bits: Option<u32>) -> SizeHint {
    match bits.map(f32::from_bits) {
        None => SizeHint::None,
        Some(v) if v == size::FILL => SizeHint::Fill,
        Some(v) if v.is_finite() && v > 0.0 => SizeHint::Fixed(v as i32),
        Some(_) => SizeHint::None,
    }
}

/// A spatial axis.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Axis {
    Horizontal,
    Vertical,
}

impl Axis {
    /// The axis a stack's `orientation` lays out along (its main axis).
    pub fn of(orientation: Orientation) -> Axis {
        if orientation == Orientation::Horizontal {
            Axis::Horizontal
        } else {
            Axis::Vertical
        }
    }

    fn property(self) -> u16 {
        match self {
            Axis::Horizontal => property_id::WIDTH,
            Axis::Vertical => property_id::HEIGHT,
        }
    }
}

/// The size hint a node carries on `axis`.
fn axis_hint(node: &HostNode, axis: Axis) -> SizeHint {
    size_hint(node.properties.get(&axis.property()).copied())
}

/// A layout-greedy primitive that fills the main axis (`main_axis` = true) or
/// the cross axis (false) by nature (LAYOUT.md): `SPACER` fills the main axis,
/// `DIVIDER` the cross axis, `COLOR`/`SCROLLVIEW` both.
fn greedy_fills(node: &HostNode, main_axis: bool) -> bool {
    match node.component_type {
        component_type::SPACER => main_axis,
        component_type::DIVIDER => !main_axis,
        component_type::COLOR | component_type::SCROLLVIEW => true,
        _ => false,
    }
}

/// Whether a node is **effectively `FILL`-sized on `axis`** — it is `FILL`
/// itself, a layout-greedy primitive that fills `axis` by nature, or (fill
/// propagation) a Hug-sized container whose subtree carries a `FILL`-sized
/// child / greedy filler on `axis`. A Fixed box bounds its subtree, so a
/// Fixed-sized container never propagates (its children fill within the box).
///
/// `resolve` maps a child id to its node; pass a no-op resolver when the caller
/// has no tree (then only the node's own size kind / greediness is considered).
pub fn effective_fill(node: &HostNode, axis: Axis, main_axis: bool, tree: &RenderTree) -> bool {
    match axis_hint(node, axis) {
        SizeHint::Fill => return true,
        SizeHint::Fixed(_) => return false,
        SizeHint::None => {}
    }
    if greedy_fills(node, main_axis) {
        return true;
    }
    if !propagates_fill(node.component_type) {
        return false;
    }
    // A child's greedy nature is relative to its own parent (`node`): a child's
    // main axis is `axis` iff `node` is a stack whose main axis is `axis`.
    let child_main = stack_orientation(node.component_type)
        .map(|o| Axis::of(o) == axis)
        .unwrap_or(false);
    node.children
        .iter()
        .any(|&cid| tree.node(cid).is_some_and(|c| effective_fill(c, axis, child_main, tree)))
}

/// Containers that hug their content and therefore propagate a `FILL` child up
/// (SwiftUI/Compose parity): stacks and the ZStack. `GRID`/`SCROLLVIEW` are
/// excluded — a grid sizes its cells, a scroll view is greedy already.
fn propagates_fill(component_type: u16) -> bool {
    matches!(
        component_type,
        component_type::VSTACK
            | component_type::HSTACK
            | component_type::ZSTACK
            | component_type::LAZY_VSTACK
            | component_type::LAZY_HSTACK
    )
}

/// The stack's explicit cross-axis `ALIGNMENT` position. `Fill` (3) and absent
/// both mean the default hug positioning (never stretch), so they yield `None`
/// (LAYOUT.md).
pub fn stack_align_option(node: &HostNode) -> Option<Align> {
    node.properties
        .get(&property_id::ALIGNMENT)
        .map(|raw| align_from(*raw))
        .filter(|a| *a != Align::Fill)
}

/// The rounded, non-negative `SPACING` of a node (0 when absent or negative).
/// Applies to stacks, grids, and composite-control bodies (a control whose
/// label flattened a stack carries that stack's `SPACING`).
pub fn spacing(node: &HostNode) -> i32 {
    f32_prop(node, property_id::SPACING).map(round_nonneg).unwrap_or(0)
}

/// Map a `TRUNCATION_MODE` code (`Head`=0, `Middle`=1, `Tail`=2, spec
/// MODIFIERS.md) to the pango ellipsize position (SwiftUI `.truncationMode`
/// parity).
pub fn ellipsize_from(mode: u8) -> pango::EllipsizeMode {
    match mode {
        0 => pango::EllipsizeMode::Start,
        1 => pango::EllipsizeMode::Middle,
        _ => pango::EllipsizeMode::End,
    }
}

/// Main-axis alignment for a stack child: effectively-`FILL` children (and
/// greedy main-axis primitives like `SPACER`) stretch; everything else is
/// positioned at the start — leftover main-axis space goes to `FILL` children
/// only (LAYOUT.md).
pub fn main_axis_align(node: &HostNode, orientation: Orientation, tree: &RenderTree) -> Align {
    if effective_fill(node, Axis::of(orientation), true, tree) {
        Align::Fill
    } else {
        Align::Start
    }
}

/// Cross-axis alignment for a stack child: effectively-`FILL` children (and
/// greedy cross-axis primitives like `DIVIDER`/`COLOR`/`SCROLLVIEW`) stretch;
/// everything else keeps its own size and is positioned by the stack's explicit
/// `ALIGNMENT` (`stack_align`), defaulting to `Start` — never stretched
/// (LAYOUT.md).
pub fn cross_axis_align(
    node: &HostNode,
    orientation: Orientation,
    stack_align: Option<Align>,
    tree: &RenderTree,
) -> Align {
    let cross = if orientation == Orientation::Vertical {
        Axis::Horizontal
    } else {
        Axis::Vertical
    };
    if effective_fill(node, cross, false, tree) {
        Align::Fill
    } else {
        stack_align.unwrap_or(Align::Start)
    }
}

/// Per-axis alignment for a `GRID` cell (no stack direction): a `FILL`-sized
/// cell (or a greedy filler) stretches; otherwise the cell keeps its size and
/// is positioned by the grid's `ALIGNMENT` on that axis (its 2D code's
/// horizontal component for `Horizontal`, vertical for `Vertical`, spec
/// PRIMITIVES.md §ZStack). `grid_align` is the grid's raw `ALIGNMENT`.
pub fn grid_cell_align(node: &HostNode, axis: Axis, grid_align: u32) -> Align {
    if matches!(axis_hint(node, axis), SizeHint::Fill)
        || matches!(
            node.component_type,
            component_type::SPACER | component_type::COLOR | component_type::SCROLLVIEW
        )
    {
        Align::Fill
    } else {
        match axis {
            Axis::Horizontal => align_h(grid_align),
            Axis::Vertical => align_v(grid_align),
        }
    }
}

/// The horizontal position component of an `ALIGNMENT` 2D code (0–8, spec
/// PRIMITIVES.md §ZStack).
pub fn align_h(raw: u32) -> Align {
    match align_code(raw) {
        1 | 3 | 4 => Align::Center,
        2 | 6 | 7 => Align::End,
        _ => Align::Start,
    }
}

/// The vertical position component of an `ALIGNMENT` 2D code (0–8).
pub fn align_v(raw: u32) -> Align {
    match align_code(raw) {
        1 | 5 | 6 => Align::Center,
        2 | 4 | 8 => Align::End,
        _ => Align::Start,
    }
}

/// Decode an `ALIGNMENT` code (f32 bit pattern or low-byte ENUM; 0..=8).
fn align_code(raw: u32) -> u8 {
    let f = f32::from_bits(raw);
    if f.is_finite() && f.fract() == 0.0 && (0.0..=8.0).contains(&f) {
        f as u8
    } else {
        (raw & 0xFF) as u8
    }
}

/// Compute the stack layout from a node's component type + properties.
///
/// Returns `None` for non-stack components. Defaults (absent properties):
/// spacing `0`, cross-axis alignment `Start`.
pub fn stack_layout(node: &HostNode) -> Option<StackLayout> {
    let orientation = stack_orientation(node.component_type)?;
    let spacing = spacing(node);
    let child_align = node
        .properties
        .get(&property_id::ALIGNMENT)
        .map(|raw| align_from(*raw))
        // Absent ALIGNMENT defaults to the hug position (Leading/Start), never
        // a stretch (LAYOUT.md).
        .unwrap_or(Align::Start);
    Some(StackLayout {
        orientation,
        spacing,
        child_align,
    })
}

/// Whether the cross axis is horizontal (a vertical stack aligns its children
/// horizontally, via `halign`); `false` means vertical (a horizontal stack
/// aligns children via `valign`).
pub fn cross_axis_is_horizontal(orientation: Orientation) -> bool {
    orientation == Orientation::Vertical
}

/// Interpret an `ALIGNMENT` property value as a cross-axis `gtk::Align`.
///
/// Both DSLs emit the enum index as an f32 bit pattern (`0.0..=3.0`); a raw
/// low-byte ENUM encoding is also accepted for robustness. Out-of-range values
/// fall back to `Fill`.
/// The stack's single-axis cross position from an `ALIGNMENT` code. Stacks read
/// one axis (`VStack` horizontal / `HStack` vertical); codes `0/1/2` are the
/// positions, anything else (2D codes, absent default) yields `None` (the
/// default start, LAYOUT.md).
pub fn align_from(raw: u32) -> Align {
    match align_code(raw) {
        0 => Align::Start,
        1 => Align::Center,
        2 => Align::End,
        _ => Align::Fill,
    }
}

/// Per-edge padding, in logical pixels (rounded, non-negative).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub struct Edges {
    pub top: i32,
    pub right: i32,
    pub bottom: i32,
    pub left: i32,
}

/// Compute a node's padding margins. Precedence (spec PRIMITIVES.md §stack
/// layout model): per-edge `PADDING_TOP/RIGHT/BOTTOM/LEFT` > uniform `PADDING`
/// > `CONTENT_MARGINS` (the stack's content inset is the lowest-precedence base,
/// matching the HTML renderer where `PADDING` is emitted after `CONTENT_MARGINS`
/// and wins the cascade).
///
/// Padding is **styling** (a DSL modifier), so it applies to any node, not just
/// stacks.
pub fn padding_from(node: &HostNode) -> Edges {
    let base = f32_prop(node, property_id::CONTENT_MARGINS)
        .map(round_nonneg)
        .unwrap_or(0);
    let uniform = f32_prop(node, property_id::PADDING)
        .map(round_nonneg)
        .unwrap_or(base);
    Edges {
        top: f32_prop(node, property_id::PADDING_TOP)
            .map(round_nonneg)
            .unwrap_or(uniform),
        right: f32_prop(node, property_id::PADDING_RIGHT)
            .map(round_nonneg)
            .unwrap_or(uniform),
        bottom: f32_prop(node, property_id::PADDING_BOTTOM)
            .map(round_nonneg)
            .unwrap_or(uniform),
        left: f32_prop(node, property_id::PADDING_LEFT)
            .map(round_nonneg)
            .unwrap_or(uniform),
    }
}

fn f32_prop(node: &HostNode, prop: u16) -> Option<f32> {
    node.properties.get(&prop).map(|bits| f32::from_bits(*bits))
}

fn round_nonneg(f: f32) -> i32 {
    f.max(0.0).round() as i32
}

/// The column count of a vertical grid (`GRID`/`LAZY_VGRID`) from its
/// `GRID_COLUMNS` constructor property (the cell-axis count). `FILL` (-1.0), a
/// non-positive value, or absence means auto-fit.
pub fn grid_columns(node: &HostNode) -> Option<u32> {
    let w = f32_prop(node, property_id::GRID_COLUMNS)?;
    if w <= 0.0 {
        return None;
    }
    Some(w.round() as u32)
}

/// The row count of a grid from its `GRID_ROWS` constructor property — the
/// fixed track of a `LAZY_HGRID` (and a static `GRID`'s row count).
pub fn grid_rows(node: &HostNode) -> Option<u32> {
    let r = f32_prop(node, property_id::GRID_ROWS)?;
    if r <= 0.0 {
        return None;
    }
    Some(r.round() as u32)
}

/// Whether a grid is horizontal (`LAZY_HGRID`) — its fixed track is the rows
/// axis and cells flow column-major.
pub fn is_hgrid(component_type: u16) -> bool {
    component_type == component_type::LAZY_HGRID
}

/// A grid's fixed track count: `WIDTH`-style columns for vertical grids
/// (`GRID`/`LAZY_VGRID`), `HEIGHT`-style rows for a horizontal `LAZY_HGRID`.
/// `None` (auto-fit) collapses to a single track.
pub fn grid_track(node: &HostNode) -> Option<u32> {
    if is_hgrid(node.component_type) {
        grid_rows(node)
    } else {
        grid_columns(node)
    }
}

/// Cell position for a grid child index. Vertical grids fill **row-major**
/// (`(row = index / track, col = index % track)`); a horizontal `LAZY_HGRID`
/// fills **column-major** (`(row = index % track, col = index / track)`).
pub fn grid_cell_position(index: u32, track: Option<u32>, horizontal: bool) -> (u32, u32) {
    let n = track.filter(|c| *c > 0).unwrap_or(1);
    if horizontal {
        (index % n, index / n)
    } else {
        (index / n, index % n)
    }
}

/// The grid's flattened `(cell, row, col)` placements (spec §GridRow): a
/// `GRID_ROW`'s children are one row's cells (always a new row starting at
/// column 0); bare cells auto-flow, advancing after `GRID_COLUMNS` — or the
/// widest row — columns. A short row leaves its trailing columns empty. A
/// horizontal `LAZY_HGRID` flattens `GRID_ROW` children too but places
/// column-major over `GRID_ROWS` tracks.
pub fn grid_cells(node: &HostNode, tree: &RenderTree) -> Vec<(u32, u32, u32)> {
    let horizontal = is_hgrid(node.component_type);
    let explicit = node
        .children
        .iter()
        .any(|&c| tree.node(c).is_some_and(|n| n.component_type == component_type::GRID_ROW));
    if !horizontal && explicit {
        // Widest-row columns when `GRID_COLUMNS` is absent (auto-fit).
        let track = grid_track(node).map(|t| t.max(1)).unwrap_or_else(|| {
            let mut width = 0u32;
            let mut run = 0u32;
            for &child in &node.children {
                if tree.node(child).is_some_and(|n| n.component_type == component_type::GRID_ROW) {
                    width = width.max(tree.node(child).map(|n| n.children.len() as u32).unwrap_or(0));
                    run = 0;
                } else {
                    run += 1;
                    width = width.max(run);
                }
            }
            width.max(1)
        });
        let mut cells = Vec::new();
        let mut row = 0u32;
        let mut col = 0u32;
        for &child in &node.children {
            let Some(cn) = tree.node(child) else { continue };
            if cn.component_type == component_type::GRID_ROW {
                if col > 0 {
                    row += 1;
                }
                col = 0;
                for &cell in &cn.children {
                    cells.push((cell, row, col));
                    col += 1;
                }
                row += 1;
                col = 0;
            } else {
                if col >= track {
                    row += 1;
                    col = 0;
                }
                cells.push((child, row, col));
                col += 1;
            }
        }
        return cells;
    }
    // Index-based placement (row-major, or column-major for an hgrid), with
    // `GRID_ROW` children flattened into their cells.
    let flat: Vec<u32> = node
        .children
        .iter()
        .flat_map(|&c| {
            if tree.node(c).is_some_and(|n| n.component_type == component_type::GRID_ROW) {
                tree.node(c).map(|n| n.children.clone()).unwrap_or_default()
            } else {
                vec![c]
            }
        })
        .collect();
    flat.into_iter()
        .enumerate()
        .map(|(i, c)| {
            let (row, col) = grid_cell_position(i as u32, grid_track(node), horizontal);
            (c, row, col)
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    fn node(component_type: u16, props: &[(u16, u32)]) -> HostNode {
        HostNode {
            id: 1,
            component_type,
            text: None,
            parent: None,
            children: Vec::new(),
            properties: props.iter().copied().collect(),
            strings: HashMap::new(),
            token_refs: Default::default(),
        }
    }

    fn f32_bits(v: f32) -> u32 {
        v.to_bits()
    }

    #[test]
    fn orientation_maps_vstack_and_hstack() {
        assert_eq!(
            stack_orientation(component_type::VSTACK),
            Some(Orientation::Vertical)
        );
        assert_eq!(
            stack_orientation(component_type::HSTACK),
            Some(Orientation::Horizontal)
        );
        // Lazy stacks render eagerly (no GTK windowing) — same orientation.
        assert_eq!(
            stack_orientation(component_type::LAZY_VSTACK),
            Some(Orientation::Vertical)
        );
        assert_eq!(
            stack_orientation(component_type::LAZY_HSTACK),
            Some(Orientation::Horizontal)
        );
        assert_eq!(stack_orientation(component_type::TEXT), None);
        assert_eq!(stack_orientation(component_type::BUTTON), None);
    }

    #[test]
    fn spacing_defaults_to_zero_and_rounds() {
        let n = node(component_type::VSTACK, &[]);
        assert_eq!(stack_layout(&n).unwrap().spacing, 0);

        let n = node(component_type::HSTACK, &[(property_id::SPACING, f32_bits(4.0))]);
        assert_eq!(stack_layout(&n).unwrap().spacing, 4);

        let n = node(component_type::VSTACK, &[(property_id::SPACING, f32_bits(4.6))]);
        assert_eq!(stack_layout(&n).unwrap().spacing, 5);

        // Negative spacing clamps to zero.
        let n = node(component_type::VSTACK, &[(property_id::SPACING, f32_bits(-8.0))]);
        assert_eq!(stack_layout(&n).unwrap().spacing, 0);
    }

    #[test]
    fn alignment_maps_each_enum_value() {
        for (idx, expected) in [
            (0.0, Align::Start),
            (1.0, Align::Center),
            (2.0, Align::End),
            (3.0, Align::Fill),
        ] {
            let n = node(
                component_type::VSTACK,
                &[(property_id::ALIGNMENT, f32_bits(idx))],
            );
            assert_eq!(stack_layout(&n).unwrap().child_align, expected);
        }
    }

    #[test]
    fn alignment_defaults_to_start() {
        let n = node(component_type::HSTACK, &[]);
        assert_eq!(stack_layout(&n).unwrap().child_align, Align::Start);
    }

    #[test]
    fn align_from_accepts_raw_enum_encoding() {
        // A raw low-byte ENUM value (not f32 bits) must still map correctly.
        assert_eq!(align_from(0), Align::Start);
        assert_eq!(align_from(1), Align::Center);
        assert_eq!(align_from(2), Align::End);
        assert_eq!(align_from(3), Align::Fill);
        // Out-of-range falls back to Fill.
        assert_eq!(align_from(99), Align::Fill);
    }

    #[test]
    fn stack_align_option_drops_fill_and_absent() {
        // Absent ALIGNMENT → None (hug default, LAYOUT.md).
        assert_eq!(stack_align_option(&node(component_type::VSTACK, &[])), None);
        // Explicit Start/Center/End → Some.
        assert_eq!(
            stack_align_option(&node(component_type::VSTACK, &[(property_id::ALIGNMENT, f32_bits(0.0))])),
            Some(Align::Start)
        );
        assert_eq!(
            stack_align_option(&node(component_type::VSTACK, &[(property_id::ALIGNMENT, f32_bits(2.0))])),
            Some(Align::End)
        );
        // Fill (3) means default hug positioning → None.
        assert_eq!(
            stack_align_option(&node(component_type::VSTACK, &[(property_id::ALIGNMENT, f32_bits(3.0))])),
            None
        );
    }

        #[test]
    fn main_axis_align_fill_and_greedy_stretch_else_start() {
        let v = Orientation::Vertical;
        // No frame → hug at the start (leftover goes to FILL children only).
        assert_eq!(
            main_axis_align(&node(component_type::TEXT, &[]), v, &RenderTree::default()),
            Align::Start
        );
        // Fixed height keeps its size → positioned at the start.
        assert_eq!(
            main_axis_align(
                &node(component_type::TEXT, &[(property_id::HEIGHT, f32_bits(44.0))]),
                v,
                &RenderTree::default(),
            ),
            Align::Start
        );
        // FILL height stretches.
        assert_eq!(
            main_axis_align(
                &node(component_type::TEXT, &[(property_id::HEIGHT, f32_bits(-1.0))]),
                v,
                &RenderTree::default(),
            ),
            Align::Fill
        );
        // SPACER is greedy on the main axis.
        assert_eq!(
            main_axis_align(&node(component_type::SPACER, &[]), v, &RenderTree::default()),
            Align::Fill
        );
    }

    #[test]
    fn cross_axis_align_fill_and_greedy_stretch_else_position() {
        let v = Orientation::Vertical; // cross axis = horizontal (WIDTH)
        // A fixed-width cover in a vertical stack keeps its size, positioned
        // at the default Leading (C2: 220 in a 280 column stays 220).
        assert_eq!(
            cross_axis_align(
                &node(component_type::IMAGE, &[(property_id::WIDTH, f32_bits(220.0))]),
                v,
                None,
                &RenderTree::default(),
            ),
            Align::Start
        );
        // Explicit Center alignment positions it, still not stretched.
        assert_eq!(
            cross_axis_align(
                &node(component_type::IMAGE, &[(property_id::WIDTH, f32_bits(220.0))]),
                v,
                Some(Align::Center),
                &RenderTree::default(),
            ),
            Align::Center
        );
        // FILL width stretches (C3).
        assert_eq!(
            cross_axis_align(
                &node(component_type::TEXT, &[(property_id::WIDTH, f32_bits(-1.0))]),
                v,
                None,
                &RenderTree::default(),
            ),
            Align::Fill
        );
        // A hug (absent) child is never stretched (C5).
        assert_eq!(
            cross_axis_align(&node(component_type::TEXT, &[]), v, None, &RenderTree::default()),
            Align::Start
        );
        // DIVIDER is greedy on the cross axis.
        assert_eq!(
            cross_axis_align(&node(component_type::DIVIDER, &[]), v, None, &RenderTree::default()),
            Align::Fill
        );
    }

    #[test]
    fn fill_propagates_through_a_hug_stack() {
        let v = Orientation::Vertical; // main = height, cross = width
        // A Hug VStack whose child is FILL-height is itself FILL on the main
        // axis (SwiftUI/Compose parity: `VStack { Spacer() }` fills).
        let mut stack = node(component_type::VSTACK, &[]);
        stack.children.push(2);
        let mut tree = RenderTree::default();
        tree.nodes.insert(2, node(component_type::TEXT, &[(property_id::HEIGHT, f32_bits(-1.0))]));
        assert_eq!(
            main_axis_align(&stack, v, &tree),
            Align::Fill,
            "a FILL child propagates main-axis fill"
        );
        // A Fixed child does not propagate (a Fixed box bounds its subtree).
        tree.nodes.insert(2, node(component_type::TEXT, &[(property_id::HEIGHT, f32_bits(44.0))]));
        assert_eq!(main_axis_align(&stack, v, &tree), Align::Start);
        // A FILL-width child propagates the CROSS axis.
        tree.nodes.insert(2, node(component_type::TEXT, &[(property_id::WIDTH, f32_bits(-1.0))]));
        assert_eq!(
            cross_axis_align(&stack, v, None, &tree),
            Align::Fill,
            "a FILL-width child makes a Hug VStack full-width"
        );
        // A nested Hug stack with a FILL child propagates through the ancestor.
        let mut outer = node(component_type::VSTACK, &[]);
        outer.children.push(2);
        let mut outer_tree = RenderTree::default();
        outer_tree.nodes.insert(
            2,
            node(
                component_type::VSTACK,
                &[(property_id::WIDTH, f32_bits(-1.0))],
            ),
        );
        assert_eq!(
            cross_axis_align(&outer, v, None, &outer_tree),
            Align::Fill,
            "a FILL child nested in a Hug sub-stack propagates to the outer stack"
        );
        // A Hug stack with only Hug children does not propagate.
        let mut hug_tree = RenderTree::default();
        hug_tree.nodes.insert(2, node(component_type::TEXT, &[]));
        assert_eq!(main_axis_align(&stack, v, &hug_tree), Align::Start);
        assert_eq!(cross_axis_align(&stack, v, None, &hug_tree), Align::Start);
    }

    #[test]
    fn spacing_clamps_and_rounds_for_any_node() {
        // Absent → 0.
        assert_eq!(spacing(&node(component_type::VSTACK, &[])), 0);
        // Rounded from f32 bits; negative clamps to zero.
        assert_eq!(
            spacing(&node(component_type::HSTACK, &[(property_id::SPACING, f32_bits(12.4))])),
            12
        );
        assert_eq!(
            spacing(&node(component_type::HSTACK, &[(property_id::SPACING, f32_bits(-8.0))])),
            0
        );
        // Works for non-stack components too (a composite control's flattened
        // label carries the stack's SPACING on the control node).
        assert_eq!(
            spacing(&node(component_type::BUTTON, &[(property_id::SPACING, f32_bits(12.0))])),
            12
        );
    }

    #[test]
    fn truncation_maps_to_ellipsize_position() {
        assert_eq!(ellipsize_from(0), pango::EllipsizeMode::Start); // Head
        assert_eq!(ellipsize_from(1), pango::EllipsizeMode::Middle); // Middle
        assert_eq!(ellipsize_from(2), pango::EllipsizeMode::End); // Tail
        assert_eq!(ellipsize_from(99), pango::EllipsizeMode::End); // out-of-range → Tail
    }

    #[test]
    fn grid_cell_align_fill_stretches_else_positions() {
        let h = Axis::Horizontal;
        // A non-FILL cell is positioned by the grid's ALIGNMENT on that axis
        // (default 0 = start).
        assert_eq!(grid_cell_align(&node(component_type::TEXT, &[]), h, 0), Align::Start);
        // 2D code 3 (topCenter): horizontal = center, vertical = start.
        assert_eq!(grid_cell_align(&node(component_type::TEXT, &[]), h, 3), Align::Center);
        assert_eq!(
            grid_cell_align(&node(component_type::TEXT, &[]), Axis::Vertical, 3),
            Align::Start
        );
        assert_eq!(
            grid_cell_align(&node(component_type::TEXT, &[(property_id::WIDTH, f32_bits(-1.0))]), h, 3),
            Align::Fill
        );
        assert_eq!(grid_cell_align(&node(component_type::COLOR, &[]), h, 3), Align::Fill);
    }

    #[test]
    fn align_h_v_split_2d_codes() {
        assert_eq!(align_h(0), Align::Start);
        assert_eq!(align_v(0), Align::Start);
        assert_eq!(align_h(1), Align::Center);
        assert_eq!(align_v(1), Align::Center);
        assert_eq!(align_h(2), Align::End);
        assert_eq!(align_v(2), Align::End);
        // topCenter = 3 → horizontal center, vertical start.
        assert_eq!(align_h(3), Align::Center);
        assert_eq!(align_v(3), Align::Start);
        // bottomLeading = 8 → horizontal start, vertical end.
        assert_eq!(align_h(8), Align::Start);
        assert_eq!(align_v(8), Align::End);
        // topTrailing = 7 → horizontal end, vertical start.
        assert_eq!(align_h(7), Align::End);
        assert_eq!(align_v(7), Align::Start);
    }

    #[test]
    fn cross_axis_is_horizontal_only_for_vertical_stack() {
        assert!(cross_axis_is_horizontal(Orientation::Vertical));
        assert!(!cross_axis_is_horizontal(Orientation::Horizontal));
    }

    #[test]
    fn padding_defaults_to_zero() {
        let n = node(component_type::TEXT, &[]);
        assert_eq!(padding_from(&n), Edges::default());
    }

    #[test]
    fn padding_uniform_sets_all_edges() {
        let n = node(component_type::VSTACK, &[(property_id::PADDING, f32_bits(8.0))]);
        assert_eq!(padding_from(&n), Edges { top: 8, right: 8, bottom: 8, left: 8 });
    }

    #[test]
    fn padding_per_edge_overrides_uniform() {
        let n = node(
            component_type::TEXT,
            &[
                (property_id::PADDING, f32_bits(8.0)),
                (property_id::PADDING_TOP, f32_bits(1.0)),
                (property_id::PADDING_LEFT, f32_bits(2.0)),
            ],
        );
        assert_eq!(padding_from(&n), Edges { top: 1, right: 8, bottom: 8, left: 2 });
    }

    #[test]
    fn content_margins_are_the_lowest_precedence_base() {
        // CONTENT_MARGINS alone is the uniform inset.
        let n = node(component_type::VSTACK, &[(property_id::CONTENT_MARGINS, f32_bits(16.0))]);
        assert_eq!(padding_from(&n), Edges { top: 16, right: 16, bottom: 16, left: 16 });
        // PADDING overrides CONTENT_MARGINS (spec PRIMITIVES.md §stack layout
        // model: PADDING_* > PADDING > CONTENT_MARGINS).
        let n = node(
            component_type::VSTACK,
            &[
                (property_id::CONTENT_MARGINS, f32_bits(16.0)),
                (property_id::PADDING, f32_bits(8.0)),
            ],
        );
        assert_eq!(padding_from(&n), Edges { top: 8, right: 8, bottom: 8, left: 8 });
        // Per-edge PADDING_* wins over both.
        let n = node(
            component_type::VSTACK,
            &[
                (property_id::CONTENT_MARGINS, f32_bits(16.0)),
                (property_id::PADDING, f32_bits(8.0)),
                (property_id::PADDING_TOP, f32_bits(1.0)),
            ],
        );
        assert_eq!(padding_from(&n), Edges { top: 1, right: 8, bottom: 8, left: 8 });
    }

    #[test]
    fn padding_applies_to_non_stack_nodes() {
        // Padding is styling, so a text node with PADDING produces margins.
        let n = node(component_type::TEXT, &[(property_id::PADDING, f32_bits(16.0))]);
        assert_eq!(padding_from(&n), Edges { top: 16, right: 16, bottom: 16, left: 16 });
    }

    #[test]
    fn padding_clamps_negative_and_rounds() {
        let n = node(component_type::VSTACK, &[(property_id::PADDING, f32_bits(-4.0))]);
        assert_eq!(padding_from(&n), Edges::default());

        let n = node(component_type::VSTACK, &[(property_id::PADDING, f32_bits(4.4))]);
        assert_eq!(padding_from(&n).top, 4);
    }

    #[test]
    fn grid_columns_from_grid_columns_property() {
        let n = node(component_type::GRID, &[(property_id::GRID_COLUMNS, f32_bits(2.0))]);
        assert_eq!(grid_columns(&n), Some(2));

        let n = node(component_type::GRID, &[]);
        assert_eq!(grid_columns(&n), None);

        // FILL (-1.0) means auto-fit.
        let n = node(component_type::GRID, &[(property_id::GRID_COLUMNS, f32_bits(-1.0))]);
        assert_eq!(grid_columns(&n), None);
    }

    #[test]
    fn grid_track_reads_columns_or_rows_by_orientation() {
        // A vertical grid's fixed track is GRID_COLUMNS.
        let v = node(component_type::LAZY_VGRID, &[(property_id::GRID_COLUMNS, f32_bits(3.0))]);
        assert_eq!(grid_track(&v), Some(3));
        // A horizontal grid's fixed track is GRID_ROWS (GRID_COLUMNS is ignored).
        let h = node(
            component_type::LAZY_HGRID,
            &[(property_id::GRID_COLUMNS, f32_bits(3.0)), (property_id::GRID_ROWS, f32_bits(4.0))],
        );
        assert_eq!(grid_track(&h), Some(4));
    }

    #[test]
    fn grid_cell_position_is_row_major_or_column_major() {
        // Vertical grids fill row-major.
        assert_eq!(grid_cell_position(0, Some(2), false), (0, 0));
        assert_eq!(grid_cell_position(1, Some(2), false), (0, 1));
        assert_eq!(grid_cell_position(2, Some(2), false), (1, 0));
        assert_eq!(grid_cell_position(3, Some(2), false), (1, 1));
        // Auto-fit collapses to a single column.
        assert_eq!(grid_cell_position(3, None, false), (3, 0));
        // Zero columns is treated as auto.
        assert_eq!(grid_cell_position(2, Some(0), false), (2, 0));
        // Horizontal grids (LAZY_HGRID) fill column-major over GRID_ROWS tracks.
        assert_eq!(grid_cell_position(0, Some(3), true), (0, 0));
        assert_eq!(grid_cell_position(1, Some(3), true), (1, 0));
        assert_eq!(grid_cell_position(3, Some(3), true), (0, 1));
        assert_eq!(grid_cell_position(4, Some(3), true), (1, 1));
    }

    #[test]
    fn grid_cells_flatten_explicit_rows() {
        let mk = |id: u32, component_type: u16, children: Vec<u32>| HostNode {
            id,
            component_type,
            text: None,
            parent: Some(1),
            children,
            properties: HashMap::new(),
            strings: HashMap::new(),
            token_refs: Default::default(),
        };
        let mut tree = RenderTree::default();
        // GRID with a 2-cell GRID_ROW, a short 1-cell GRID_ROW, and a bare cell.
        tree.nodes.insert(1, mk(1, component_type::GRID, vec![2, 5, 7]));
        tree.nodes.insert(2, mk(2, component_type::GRID_ROW, vec![3, 4]));
        tree.nodes.insert(3, mk(3, component_type::TEXT, vec![]));
        tree.nodes.insert(4, mk(4, component_type::TEXT, vec![]));
        tree.nodes.insert(5, mk(5, component_type::GRID_ROW, vec![6]));
        tree.nodes.insert(6, mk(6, component_type::TEXT, vec![]));
        tree.nodes.insert(7, mk(7, component_type::TEXT, vec![]));

        let grid = tree.node(1).unwrap();
        // Row 0 = [3, 4], row 1 = [6], bare 7 auto-flows to row 2 (widest row 2).
        assert_eq!(
            grid_cells(grid, &tree),
            vec![(3, 0, 0), (4, 0, 1), (6, 1, 0), (7, 2, 0)]
        );
    }

    #[test]
    fn grid_cells_auto_flow_bare_cells_under_columns() {
        let mk = |id: u32, component_type: u16, children: Vec<u32>, props: &[(u16, u32)]| HostNode {
            id,
            component_type,
            text: None,
            parent: Some(1),
            children,
            properties: props.iter().copied().collect(),
            strings: HashMap::new(),
            token_refs: Default::default(),
        };
        let mut tree = RenderTree::default();
        // GRID_COLUMNS=2, three bare cells → row 0 = [2,3], row 1 = [4].
        tree.nodes.insert(
            1,
            mk(1, component_type::GRID, vec![2, 3, 4], &[(property_id::GRID_COLUMNS, f32_bits(2.0))]),
        );
        tree.nodes.insert(2, mk(2, component_type::TEXT, vec![], &[]));
        tree.nodes.insert(3, mk(3, component_type::TEXT, vec![], &[]));
        tree.nodes.insert(4, mk(4, component_type::TEXT, vec![], &[]));

        let grid = tree.node(1).unwrap();
        assert_eq!(grid_cells(grid, &tree), vec![(2, 0, 0), (3, 0, 1), (4, 1, 0)]);
    }
}
