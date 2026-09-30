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
use crate::host::HostNode;
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

/// Whether a stack child stretches on `axis`: it is `FILL`-sized there, or it
/// is a layout-greedy primitive that fills that axis by nature.
fn stretches(node: &HostNode, axis: Axis, main_axis: bool) -> bool {
    matches!(axis_hint(node, axis), SizeHint::Fill) || greedy_fills(node, main_axis)
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

/// Main-axis alignment for a stack child: `FILL` children (and greedy
/// main-axis primitives like `SPACER`) stretch; everything else is positioned
/// at the start — leftover main-axis space goes to `FILL` children only
/// (LAYOUT.md).
pub fn main_axis_align(node: &HostNode, orientation: Orientation) -> Align {
    if stretches(node, Axis::of(orientation), true) {
        Align::Fill
    } else {
        Align::Start
    }
}

/// Cross-axis alignment for a stack child: `FILL` children (and greedy
/// cross-axis primitives like `DIVIDER`/`COLOR`/`SCROLLVIEW`) stretch;
/// everything else keeps its own size and is positioned by the stack's
/// explicit `ALIGNMENT` (`stack_align`), defaulting to `Start` — never
/// stretched (LAYOUT.md).
pub fn cross_axis_align(
    node: &HostNode,
    orientation: Orientation,
    stack_align: Option<Align>,
) -> Align {
    let cross = if orientation == Orientation::Vertical {
        Axis::Horizontal
    } else {
        Axis::Vertical
    };
    if stretches(node, cross, false) {
        Align::Fill
    } else {
        stack_align.unwrap_or(Align::Start)
    }
}

/// Per-axis alignment for a `GRID` cell (no stack direction): a `FILL`-sized
/// cell (or a greedy filler) stretches; otherwise the cell keeps its size and
/// is positioned at the start.
pub fn grid_cell_align(node: &HostNode, axis: Axis) -> Align {
    if matches!(axis_hint(node, axis), SizeHint::Fill)
        || matches!(
            node.component_type,
            component_type::SPACER | component_type::COLOR | component_type::SCROLLVIEW
        )
    {
        Align::Fill
    } else {
        Align::Start
    }
}

/// Compute the stack layout from a node's component type + properties.
///
/// Returns `None` for non-stack components. Defaults (absent properties):
/// spacing `0`, cross-axis alignment `Fill`.
pub fn stack_layout(node: &HostNode) -> Option<StackLayout> {
    let orientation = stack_orientation(node.component_type)?;
    let spacing = f32_prop(node, property_id::SPACING)
        .map(round_nonneg)
        .unwrap_or(0);
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
pub fn align_from(raw: u32) -> Align {
    let f = f32::from_bits(raw);
    let idx = if f.is_finite() && f.fract() == 0.0 && (0.0..=3.0).contains(&f) {
        f as u8
    } else {
        (raw & 0xFF) as u8
    };
    match idx {
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

impl Edges {
    /// True when every edge is zero.
    pub fn is_zero(&self) -> bool {
        self.top == 0 && self.right == 0 && self.bottom == 0 && self.left == 0
    }
}

/// Compute a node's padding margins. Uniform `PADDING` sets all four edges;
/// per-edge `PADDING_TOP/RIGHT/BOTTOM/LEFT` override individual edges.
///
/// Padding is **styling** (a DSL modifier), so it applies to any node, not just
/// stacks.
pub fn padding_from(node: &HostNode) -> Edges {
    let uniform = f32_prop(node, property_id::PADDING)
        .map(round_nonneg)
        .unwrap_or(0);
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

/// The column count of a `GRID` from its `WIDTH` property (the cell-axis
/// count). `FILL` (-1.0), a non-positive value, or absence means auto-fit.
pub fn grid_columns(node: &HostNode) -> Option<u32> {
    let w = f32_prop(node, property_id::WIDTH)?;
    if w <= 0.0 {
        return None;
    }
    Some(w.round() as u32)
}

/// Row-major cell position for a `GRID` child index.
///
/// `columns` is the `grid_columns` count; `None` (auto-fit) collapses to a
/// single column. The position is `(row, column)`.
pub fn grid_cell_position(index: u32, columns: Option<u32>) -> (u32, u32) {
    let cols = columns.filter(|c| *c > 0).unwrap_or(1);
    (index / cols, index % cols)
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
        assert_eq!(main_axis_align(&node(component_type::TEXT, &[]), v), Align::Start);
        // Fixed height keeps its size → positioned at the start.
        assert_eq!(
            main_axis_align(&node(component_type::TEXT, &[(property_id::HEIGHT, f32_bits(44.0))]), v),
            Align::Start
        );
        // FILL height stretches.
        assert_eq!(
            main_axis_align(&node(component_type::TEXT, &[(property_id::HEIGHT, f32_bits(-1.0))]), v),
            Align::Fill
        );
        // SPACER is greedy on the main axis.
        assert_eq!(main_axis_align(&node(component_type::SPACER, &[]), v), Align::Fill);
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
            ),
            Align::Start
        );
        // Explicit Center alignment positions it, still not stretched.
        assert_eq!(
            cross_axis_align(
                &node(component_type::IMAGE, &[(property_id::WIDTH, f32_bits(220.0))]),
                v,
                Some(Align::Center),
            ),
            Align::Center
        );
        // FILL width stretches (C3).
        assert_eq!(
            cross_axis_align(
                &node(component_type::TEXT, &[(property_id::WIDTH, f32_bits(-1.0))]),
                v,
                None,
            ),
            Align::Fill
        );
        // A hug (absent) child is never stretched (C5).
        assert_eq!(
            cross_axis_align(&node(component_type::TEXT, &[]), v, None),
            Align::Start
        );
        // DIVIDER is greedy on the cross axis.
        assert_eq!(
            cross_axis_align(&node(component_type::DIVIDER, &[]), v, None),
            Align::Fill
        );
    }

    #[test]
    fn grid_cell_align_fill_stretches_else_start() {
        let h = Axis::Horizontal;
        assert_eq!(grid_cell_align(&node(component_type::TEXT, &[]), h), Align::Start);
        assert_eq!(
            grid_cell_align(&node(component_type::TEXT, &[(property_id::WIDTH, f32_bits(-1.0))]), h),
            Align::Fill
        );
        assert_eq!(grid_cell_align(&node(component_type::COLOR, &[]), h), Align::Fill);
    }

    #[test]
    fn cross_axis_is_horizontal_only_for_vertical_stack() {
        assert!(cross_axis_is_horizontal(Orientation::Vertical));
        assert!(!cross_axis_is_horizontal(Orientation::Horizontal));
    }

    #[test]
    fn padding_defaults_to_zero() {
        let n = node(component_type::TEXT, &[]);
        assert!(padding_from(&n).is_zero());
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
    fn padding_applies_to_non_stack_nodes() {
        // Padding is styling, so a text node with PADDING produces margins.
        let n = node(component_type::TEXT, &[(property_id::PADDING, f32_bits(16.0))]);
        assert_eq!(padding_from(&n), Edges { top: 16, right: 16, bottom: 16, left: 16 });
    }

    #[test]
    fn padding_clamps_negative_and_rounds() {
        let n = node(component_type::VSTACK, &[(property_id::PADDING, f32_bits(-4.0))]);
        assert!(padding_from(&n).is_zero());

        let n = node(component_type::VSTACK, &[(property_id::PADDING, f32_bits(4.4))]);
        assert_eq!(padding_from(&n).top, 4);
    }

    #[test]
    fn grid_columns_from_width_property() {
        let n = node(component_type::GRID, &[(property_id::WIDTH, f32_bits(2.0))]);
        assert_eq!(grid_columns(&n), Some(2));

        let n = node(component_type::GRID, &[]);
        assert_eq!(grid_columns(&n), None);

        // FILL (-1.0) means auto-fit.
        let n = node(component_type::GRID, &[(property_id::WIDTH, f32_bits(-1.0))]);
        assert_eq!(grid_columns(&n), None);
    }

    #[test]
    fn grid_cell_position_is_row_major() {
        assert_eq!(grid_cell_position(0, Some(2)), (0, 0));
        assert_eq!(grid_cell_position(1, Some(2)), (0, 1));
        assert_eq!(grid_cell_position(2, Some(2)), (1, 0));
        assert_eq!(grid_cell_position(3, Some(2)), (1, 1));
        // Auto-fit collapses to a single column.
        assert_eq!(grid_cell_position(3, None), (3, 0));
        // Zero columns is treated as auto.
        assert_eq!(grid_cell_position(2, Some(0)), (2, 0));
    }
}
