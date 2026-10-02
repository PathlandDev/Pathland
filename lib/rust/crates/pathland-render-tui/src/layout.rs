//! TUI layout: pure, headless-testable decisions that turn the retained tree
//! into terminal `Rect`s.
//!
//! Two passes (mirroring the GTK renderer's approach, but producing ratatui
//! geometry instead of widget properties):
//!
//! 1. **measure** — bottom-up natural cell size for each subtree;
//! 2. **arrange** — top-down `Rect` assignment, with `FILL`/greedy children
//!    splitting leftover on the main axis and cross-axis `ALIGNMENT` narrowing
//!    hug children within their cell.
//!
//! 1 protocol point = 1 terminal cell.

use std::collections::HashMap;

use pathland_core::{component_type, property_id, size};
use pathland_host::{HostNode, RenderTree};
use ratatui::layout::{Constraint, Direction, Layout, Rect};

/// Per-edge padding/margin, in cells.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub struct Edges {
    pub top: u16,
    pub right: u16,
    pub bottom: u16,
    pub left: u16,
}

impl Edges {
    fn horizontal(self) -> u16 {
        self.left + self.right
    }
    fn vertical(self) -> u16 {
        self.top + self.bottom
    }
}

/// Natural cell size of a subtree.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub struct Size {
    pub width: u16,
    pub height: u16,
}

/// Cross-axis position within a cell.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CrossPos {
    Start,
    Center,
    End,
}

fn f32_prop(node: &HostNode, prop: u16) -> Option<f32> {
    node.properties.get(&prop).copied().map(f32::from_bits)
}

/// Trim a float for display (e.g. `1` not `1.0`).
fn format_float(v: f32) -> String {
    let r = v.round();
    if (v - r).abs() < f32::EPSILON {
        format!("{}", r as i64)
    } else {
        format!("{v:.1}")
    }
}

/// A finite, positive `WIDTH`/`HEIGHT` (Fixed size hint) in cells.
fn fixed(node: &HostNode, prop: u16) -> Option<u16> {
    match f32_prop(node, prop) {
        Some(v) if v > 0.0 => Some(v.round() as u16),
        _ => None,
    }
}

/// A `FILL` (`size::FILL`) size hint on the given axis.
fn fills(node: &HostNode, prop: u16) -> bool {
    matches!(f32_prop(node, prop), Some(v) if v == size::FILL)
}

fn edge_value(node: &HostNode, per_edge: u16, uniform: u16, content_margins: u16) -> u16 {
    f32_prop(node, per_edge)
        .or_else(|| f32_prop(node, uniform))
        .or_else(|| f32_prop(node, content_margins))
        .unwrap_or(0.0)
        .round()
        .max(0.0) as u16
}

/// Per-edge padding (per-edge > uniform `PADDING` > `CONTENT_MARGINS`).
pub fn padding_edges(node: &HostNode) -> Edges {
    let uniform = property_id::PADDING;
    let cm = property_id::CONTENT_MARGINS;
    Edges {
        top: edge_value(node, property_id::PADDING_TOP, uniform, cm),
        right: edge_value(node, property_id::PADDING_RIGHT, uniform, cm),
        bottom: edge_value(node, property_id::PADDING_BOTTOM, uniform, cm),
        left: edge_value(node, property_id::PADDING_LEFT, uniform, cm),
    }
}

/// Stack `SPACING` in cells.
pub fn spacing(node: &HostNode) -> u16 {
    f32_prop(node, property_id::SPACING)
        .unwrap_or(0.0)
        .round()
        .max(0.0) as u16
}

/// Decode a 2D `ALIGNMENT` code from a stored property value, accepting the
/// f32-encoded form (`(code as f32).to_bits()`, the Rust/Java DSL emitters) or
/// a raw low-byte ENUM.
fn alignment_code(raw: u32) -> u8 {
    let f = f32::from_bits(raw);
    if (0.0..=8.0).contains(&f) && f.fract() == 0.0 {
        f as u8
    } else {
        (raw & 0xFF) as u8
    }
}

/// Horizontal cross-axis position from a 2D `ALIGNMENT` code (0–8).
pub fn align_h(raw: u32) -> CrossPos {
    match alignment_code(raw) {
        1 | 3 | 4 => CrossPos::Center,
        2 | 6 | 7 => CrossPos::End,
        _ => CrossPos::Start,
    }
}

/// Vertical cross-axis position from a 2D `ALIGNMENT` code (0–8).
pub fn align_v(raw: u32) -> CrossPos {
    match alignment_code(raw) {
        1 | 5 | 6 => CrossPos::Center,
        2 | 4 | 8 => CrossPos::End,
        _ => CrossPos::Start,
    }
}

/// The cross-axis position a stack applies to its children.
fn cross_axis_position(stack: &HostNode, dir: Direction) -> CrossPos {
    let raw = stack
        .properties
        .get(&property_id::ALIGNMENT)
        .copied()
        .unwrap_or(0);
    match dir {
        Direction::Vertical => align_h(raw),
        Direction::Horizontal => align_v(raw),
    }
}

fn main_size(s: Size, dir: Direction) -> u16 {
    match dir {
        Direction::Vertical => s.height,
        Direction::Horizontal => s.width,
    }
}

/// A child is greedy on the stack's main axis (absorbs leftover space).
fn greedy_main(node: &HostNode, dir: Direction) -> bool {
    let prop = match dir {
        Direction::Vertical => property_id::HEIGHT,
        Direction::Horizontal => property_id::WIDTH,
    };
    if fills(node, prop) {
        return true;
    }
    matches!(
        node.component_type,
        component_type::SPACER | component_type::COLOR | component_type::SCROLLVIEW
    )
}

fn has_border(node: &HostNode) -> bool {
    f32_prop(node, property_id::BORDER_WIDTH).unwrap_or(0.0) > 0.0
}

fn text_size(text: &str) -> Size {
    use ratatui::text::Line;
    let mut height = 0u16;
    let mut width = 0u16;
    for raw in text.split('\n') {
        width = width.max(Line::from(raw.to_string()).width() as u16);
        height += 1;
    }
    if text.is_empty() {
        width = 0;
    }
    Size {
        width: width.max(1),
        height: height.max(1),
    }
}

/// Bottom-up natural cell size of the subtree rooted at `id`.
pub fn natural_size(tree: &RenderTree, id: u32) -> Size {
    let Some(node) = tree.node(id) else {
        return Size::default();
    };
    let mut s = match node.component_type {
        component_type::TEXT => text_size(node.text.as_deref().unwrap_or("")),
        component_type::BUTTON => {
            let t = text_size(node.text.as_deref().unwrap_or(""));
            let e = padding_edges(node);
            let border = if has_border(node) { 2 } else { 0 };
            Size {
                width: t.width + e.horizontal() + border,
                height: t.height + e.vertical() + border,
            }
        }
        component_type::VSTACK
        | component_type::LAZY_VSTACK
        | component_type::HSTACK
        | component_type::LAZY_HSTACK => stack_natural(node, tree, node.component_type),
        component_type::PROGRESS_VIEW | component_type::GAUGE => Size {
            width: fixed(node, property_id::WIDTH).unwrap_or(20),
            height: 1,
        },
        component_type::SLIDER => Size {
            width: fixed(node, property_id::WIDTH).unwrap_or(20),
            height: 1,
        },
        component_type::STEPPER => {
            let value = format_float(node.f32_property(property_id::VALUE, 0.0));
            Size {
                width: 3 + value.chars().count() as u16,
                height: 1,
            }
        }
        component_type::TOGGLE => {
            let label = node
                .string_property(property_id::LABEL)
                .or(node.text.as_deref())
                .unwrap_or("");
            let t = text_size(label);
            Size {
                width: 4 + t.width,
                height: 1,
            }
        }
        component_type::TEXT_FIELD | component_type::TEXT_EDITOR => {
            let content = node.text.as_deref().unwrap_or("");
            let t = text_size(content);
            let width = fixed(node, property_id::WIDTH)
                .unwrap_or(t.width.max(20).min(40));
            Size {
                width,
                height: if node.component_type == component_type::TEXT_EDITOR {
                    t.height.max(3)
                } else {
                    t.height.max(1)
                },
            }
        }
        _ => Size { width: 1, height: 1 },
    };
    if let Some(w) = fixed(node, property_id::WIDTH) {
        s.width = w;
    }
    if let Some(h) = fixed(node, property_id::HEIGHT) {
        s.height = h;
    }
    s
}

fn stack_natural(node: &HostNode, tree: &RenderTree, component: u16) -> Size {
    let vertical = matches!(component, component_type::VSTACK | component_type::LAZY_VSTACK);
    let e = padding_edges(node);
    let sp = spacing(node);
    let mut main_total = 0u16;
    let mut cross_max = 0u16;
    let n = node.children.len() as u16;
    for cid in &node.children {
        let s = natural_size(tree, *cid);
        if vertical {
            main_total += s.height;
            cross_max = cross_max.max(s.width);
        } else {
            main_total += s.width;
            cross_max = cross_max.max(s.height);
        }
    }
    let gaps = sp.saturating_mul(n.saturating_sub(1));
    if vertical {
        Size {
            width: cross_max + e.horizontal(),
            height: main_total + gaps + e.vertical(),
        }
    } else {
        Size {
            width: main_total + gaps + e.horizontal(),
            height: cross_max + e.vertical(),
        }
    }
}

/// Shrink an area by per-edge padding (clamped).
fn inset(area: Rect, e: &Edges) -> Rect {
    Rect {
        x: area.x + e.left.min(area.width),
        y: area.y + e.top.min(area.height),
        width: area.width.saturating_sub(e.horizontal()),
        height: area.height.saturating_sub(e.vertical()),
    }
}

/// Assign a `Rect` to every node in the tree (top-down).
pub fn arrange(tree: &RenderTree, root: u32, area: Rect) -> HashMap<u32, Rect> {
    let mut out = HashMap::new();
    place(tree, root, area, &mut out);
    out
}

fn place(tree: &RenderTree, id: u32, area: Rect, out: &mut HashMap<u32, Rect>) {
    out.insert(id, area);
    let Some(node) = tree.node(id) else {
        return;
    };
    match node.component_type {
        component_type::VSTACK
        | component_type::LAZY_VSTACK
        | component_type::HSTACK
        | component_type::LAZY_HSTACK => {
            let dir = if matches!(
                node.component_type,
                component_type::VSTACK | component_type::LAZY_VSTACK
            ) {
                Direction::Vertical
            } else {
                Direction::Horizontal
            };
            place_stack(tree, node, area, dir, out);
        }
        component_type::BUTTON if !node.children.is_empty() => {
            // Composite body renders as a horizontal row inside the shell.
            place_stack(tree, node, area, Direction::Horizontal, out);
        }
        _ => {
            // Non-stack containers: children share the full inner area (an
            // overlay); each child is sized to its natural cells, clamped.
            let inner = inset(area, &padding_edges(node));
            for cid in &node.children {
                let s = natural_size(tree, *cid);
                let child_area = Rect {
                    x: inner.x,
                    y: inner.y,
                    width: s.width.min(inner.width),
                    height: s.height.min(inner.height),
                };
                place(tree, *cid, child_area, out);
            }
        }
    }
}

fn place_stack(
    tree: &RenderTree,
    node: &HostNode,
    area: Rect,
    dir: Direction,
    out: &mut HashMap<u32, Rect>,
) {
    let e = padding_edges(node);
    let inner = inset(area, &e);
    let sp = spacing(node);
    let children = node.children.clone();
    if children.is_empty() {
        return;
    }

    // Classify children: FILL/greedy on the main axis split leftover, the rest
    // take their natural main-axis size. Gaps are interleaved as spacer
    // segments so the child/segment pairing stays aligned.
    let mut segments: Vec<(Option<u32>, Constraint)> = Vec::with_capacity(children.len() * 2 - 1);
    for (i, cid) in children.iter().enumerate() {
        if i > 0 {
            segments.push((None, Constraint::Length(sp)));
        }
        let natural = natural_size(tree, *cid);
        let greedy = tree.node(*cid).map_or(false, |cn| greedy_main(cn, dir));
        if greedy {
            segments.push((Some(*cid), Constraint::Fill(1)));
        } else {
            segments.push((Some(*cid), Constraint::Length(main_size(natural, dir).max(1))));
        }
    }

    let constraints: Vec<Constraint> = segments.iter().map(|(_, c)| *c).collect();
    let cells = Layout::default()
        .direction(dir)
        .constraints(constraints)
        .split(inner);

    for ((cid, _), cell) in segments.iter().zip(cells.iter()) {
        let Some(cid) = cid else { continue };
        let child_area = align_cross(*cell, tree, *cid, dir, node);
        place(tree, *cid, child_area, out);
    }
}

/// Narrow a child's cell on the cross axis per the stack's `ALIGNMENT` (hug
/// children are positioned, FILL children keep the full cell).
fn align_cross(
    cell: Rect,
    tree: &RenderTree,
    cid: u32,
    dir: Direction,
    stack: &HostNode,
) -> Rect {
    let fills_cross = match tree.node(cid) {
        Some(cn) => match dir {
            Direction::Vertical => fills(cn, property_id::WIDTH),
            Direction::Horizontal => fills(cn, property_id::HEIGHT),
        },
        None => false,
    };
    if fills_cross {
        return cell;
    }
    let pos = cross_axis_position(stack, dir);
    let natural = natural_size(tree, cid);
    match dir {
        Direction::Vertical => {
            let w = natural.width.min(cell.width);
            let x = match pos {
                CrossPos::Start => cell.x,
                CrossPos::Center => cell.x + (cell.width - w) / 2,
                CrossPos::End => cell.x + cell.width - w,
            };
            Rect {
                x,
                y: cell.y,
                width: w,
                height: cell.height,
            }
        }
        Direction::Horizontal => {
            let h = natural.height.min(cell.height);
            let y = match pos {
                CrossPos::Start => cell.y,
                CrossPos::Center => cell.y + (cell.height - h) / 2,
                CrossPos::End => cell.y + cell.height - h,
            };
            Rect {
                x: cell.x,
                y,
                width: cell.width,
                height: h,
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use pathland_host::HostNode;

    fn node(id: u32, component_type: u16, children: Vec<u32>, props: &[(u16, u32)]) -> HostNode {
        HostNode {
            id,
            component_type,
            text: None,
            parent: None,
            children,
            properties: props.iter().copied().collect(),
            strings: Default::default(),
            token_refs: Default::default(),
        }
    }

    fn tree(nodes: Vec<HostNode>) -> RenderTree {
        let mut t = RenderTree::default();
        for n in &nodes {
            t.nodes.insert(n.id, n.clone());
        }
        // Wire parents so `RenderTree::root()` is deterministic (apply_frame
        // does this from TREE::INSERT_CHILD; the test helper must mirror it).
        for n in &nodes {
            for cid in &n.children {
                if let Some(c) = t.nodes.get_mut(cid) {
                    c.parent = Some(n.id);
                }
            }
        }
        t
    }

    fn rect(x: u16, y: u16, w: u16, h: u16) -> Rect {
        Rect::new(x, y, w, h)
    }

    #[test]
    fn vstack_stacks_children_vertically_with_spacing() {
        let t = tree(vec![
            node(1, component_type::VSTACK, vec![2, 3], &[]),
            node(2, component_type::TEXT, vec![], &[]),
            node(3, component_type::TEXT, vec![], &[]),
        ]);
        let rects = arrange(&t, 1, rect(0, 0, 20, 10));
        assert_eq!(rects[&2], rect(0, 0, 1, 1));
        assert_eq!(rects[&3], rect(0, 1, 1, 1));
    }

    #[test]
    fn vstack_spacing_gap_positions_second_child() {
        let t = tree(vec![
            node(
                1,
                component_type::VSTACK,
                vec![2, 3],
                &[(property_id::SPACING, 2.0f32.to_bits())],
            ),
            node(2, component_type::TEXT, vec![], &[]),
            node(3, component_type::TEXT, vec![], &[]),
        ]);
        let rects = arrange(&t, 1, rect(0, 0, 20, 10));
        assert_eq!(rects[&2], rect(0, 0, 1, 1));
        assert_eq!(rects[&3], rect(0, 3, 1, 1));
    }

    #[test]
    fn hstack_places_children_side_by_side() {
        let t = tree(vec![
            node(1, component_type::HSTACK, vec![2, 3], &[]),
            node(2, component_type::TEXT, vec![], &[]),
            node(3, component_type::TEXT, vec![], &[]),
        ]);
        let rects = arrange(&t, 1, rect(0, 0, 20, 5));
        assert_eq!(rects[&2], rect(0, 0, 1, 1));
        assert_eq!(rects[&3], rect(1, 0, 1, 1));
    }

    #[test]
    fn filler_absorbs_leftover_on_main_axis() {
        let t = tree(vec![
            node(1, component_type::HSTACK, vec![2, 3], &[]),
            node(2, component_type::TEXT, vec![], &[]),
            node(3, component_type::SPACER, vec![], &[]),
        ]);
        let rects = arrange(&t, 1, rect(0, 0, 10, 5));
        // Text takes its natural cell; the spacer absorbs the remaining 9 on
        // the main axis (both keep their natural cross height, top-aligned).
        assert_eq!(rects[&2], rect(0, 0, 1, 1));
        assert_eq!(rects[&3], rect(1, 0, 9, 1));
    }

    #[test]
    fn fill_size_child_stretches_on_main_axis() {
        let t = tree(vec![
            node(1, component_type::VSTACK, vec![2], &[]),
            node(
                2,
                component_type::TEXT,
                vec![],
                &[(property_id::HEIGHT, size::FILL.to_bits())],
            ),
        ]);
        let rects = arrange(&t, 1, rect(0, 0, 10, 10));
        // FILL on the main axis stretches the child; the cross axis keeps its
        // natural width (top/leading aligned).
        assert_eq!(rects[&2], rect(0, 0, 1, 10));
    }

    #[test]
    fn cross_axis_alignment_centers_hug_child() {
        let t = tree(vec![
            node(
                1,
                component_type::VSTACK,
                vec![2],
                &[(property_id::ALIGNMENT, 1.0f32.to_bits())],
            ),
            node(2, component_type::TEXT, vec![], &[]),
        ]);
        let rects = arrange(&t, 1, rect(0, 0, 20, 5));
        // Center on the cross (horizontal) axis: (20 - 1)/2 = 9.
        assert_eq!(rects[&2], rect(9, 0, 1, 1));
    }

    #[test]
    fn padding_shrinks_the_content_area() {
        let t = tree(vec![
            node(
                1,
                component_type::VSTACK,
                vec![2],
                &[(property_id::PADDING, 1.0f32.to_bits())],
            ),
            node(2, component_type::TEXT, vec![], &[]),
        ]);
        let rects = arrange(&t, 1, rect(0, 0, 20, 10));
        assert_eq!(rects[&2], rect(1, 1, 1, 1));
    }
}