//! The draw pass: retained tree → ratatui widgets.
//!
//! Each terminal frame redraws the whole UI from the retained
//! [`RenderTree`](pathland_host::RenderTree) into the areas computed by
//! [`crate::layout`]. The renderer keeps the per-frame `id → Rect` map for
//! hit-testing; it never retains application state (AGENTS.md Principle 1).

use std::collections::HashMap;

use pathland_core::{component_type, property_id};
use pathland_host::RenderTree;
use ratatui::layout::{Alignment as TuiAlign, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span, Text};
use ratatui::widgets::{Block, Fill, Gauge, LineGauge, Paragraph};
use ratatui::Frame;

use crate::layout;

/// The Ratatui (TUI) renderer.
pub struct TuiRenderer {
    tree: RenderTree,
    /// Per-frame geometry cache: node id → area drawn this frame (hit-testing).
    rects: HashMap<u32, Rect>,
    /// Frame counter (spinner animation).
    tick: u64,
}

impl TuiRenderer {
    pub fn new() -> Self {
        Self {
            tree: RenderTree::default(),
            rects: HashMap::new(),
            tick: 0,
        }
    }

    /// The retained tree (the renderer's rendered-output cache).
    pub fn tree(&self) -> &RenderTree {
        &self.tree
    }

    /// The area drawn for a node this frame (or the last frame).
    pub fn rect(&self, id: u32) -> Option<Rect> {
        self.rects.get(&id).copied()
    }

    /// Apply an opcode frame (a delta) to the retained tree.
    pub fn apply_frame(&mut self, frame: &pathland_core::Frame<'_>) {
        self.tree.apply_frame(frame);
    }

    /// Set the effective color scheme (re-resolves token-referencing props).
    pub fn set_scheme(&mut self, scheme: pathland_core::tokens::Scheme) {
        self.tree.set_scheme(scheme);
    }

    /// Advance the spinner animation frame.
    pub fn tick(&mut self) {
        self.tick = self.tick.wrapping_add(1);
    }

    /// Draw the whole tree into `area`.
    pub fn draw(&mut self, frame: &mut Frame, area: Rect) {
        self.rects.clear();
        if let Some(root) = self.tree.root() {
            self.rects = layout::arrange(&self.tree, root.id, area);
            self.draw_node(frame, root.id);
        }
    }

    fn draw_node(&self, frame: &mut Frame, id: u32) {
        let Some(node) = self.tree.node(id).cloned() else {
            return;
        };
        let Some(area) = self.rects.get(&id).copied() else {
            return;
        };
        match node.component_type {
            component_type::TEXT => self.draw_text(frame, &node, area),
            component_type::BUTTON => self.draw_button(frame, &node, area),
            component_type::DIVIDER => self.draw_divider(frame, &node, area),
            component_type::COLOR => self.draw_color(frame, &node, area),
            component_type::PROGRESS_VIEW => self.draw_progress(frame, &node, area),
            component_type::GAUGE => self.draw_gauge(frame, &node, area),
            _ => {}
        }
        // Recurse (stacks draw their children; overlay containers draw on top).
        let children = node.children.clone();
        for cid in children {
            self.draw_node(frame, cid);
        }
    }

    fn draw_text(&self, frame: &mut Frame, node: &pathland_host::HostNode, area: Rect) {
        let text = node.text.as_deref().unwrap_or("");
        let style = self.node_style(node);
        let mut lines: Vec<Line> = text
            .split('\n')
            .map(|l| Line::from(Span::styled(l.to_string(), style)))
            .collect();
        if let Some(limit) = node.u32_property(property_id::LINE_LIMIT, 0).checked_sub(1) {
            lines.truncate(limit as usize);
        }
        let alignment = tui_align(enum_code(node, property_id::TEXT_ALIGNMENT, 0));
        frame.render_widget(Paragraph::new(Text::from(lines)).alignment(alignment), area);
    }

    fn draw_button(&self, frame: &mut Frame, node: &pathland_host::HostNode, area: Rect) {
        let label = node.text.as_deref().unwrap_or("");
        let style = self.node_style(node);
        let border = if node.f32_property(property_id::BORDER_WIDTH, 0.0) > 0.0 {
            let border_color = node
                .properties
                .get(&property_id::BORDER_COLOR)
                .copied()
                .map(argb_to_color)
                .unwrap_or(Color::Gray);
            Block::bordered().border_style(Style::default().fg(border_color))
        } else {
            Block::default()
        };
        let para = Paragraph::new(Text::from(label.to_string()))
            .block(border)
            .style(style);
        frame.render_widget(para, area);
    }

    fn draw_divider(&self, frame: &mut Frame, node: &pathland_host::HostNode, area: Rect) {
        let style = self.node_style(node);
        let line = "─".repeat(area.width as usize);
        frame.render_widget(Paragraph::new(Text::from(line)).style(style), area);
    }

    fn draw_color(&self, frame: &mut Frame, node: &pathland_host::HostNode, area: Rect) {
        let bg = node
            .properties
            .get(&property_id::COLOR)
            .or_else(|| node.properties.get(&property_id::BACKGROUND_COLOR))
            .copied()
            .map(argb_to_color)
            .unwrap_or(Color::Reset);
        frame.render_widget(Fill::new(" ").style(Style::default().bg(bg)), area);
    }

    fn draw_progress(&self, frame: &mut Frame, node: &pathland_host::HostNode, area: Rect) {
        let indeterminate =
            truthy(node, property_id::IS_INDETERMINATE) || !node.properties.contains_key(&property_id::PROGRESS);
        if indeterminate {
            let chars = ["⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"];
            let c = chars[(self.tick as usize) % chars.len()];
            frame.render_widget(Paragraph::new(Text::from(c)).style(self.node_style(node)), area);
        } else {
            let ratio = node.f32_property(property_id::PROGRESS, 0.0).clamp(0.0, 1.0) as f64;
            let gauge = Gauge::default()
                .ratio(ratio)
                .gauge_style(self.node_style(node));
            frame.render_widget(gauge, area);
        }
    }

    fn draw_gauge(&self, frame: &mut Frame, node: &pathland_host::HostNode, area: Rect) {
        let min = node.f32_property(property_id::MIN_VALUE, 0.0);
        let max = node.f32_property(property_id::MAX_VALUE, 1.0);
        let value = node.f32_property(property_id::VALUE, min);
        let ratio = if max > min {
            ((value - min) / (max - min)).clamp(0.0, 1.0)
        } else {
            0.0
        } as f64;
        let gauge = LineGauge::default().ratio(ratio).style(self.node_style(node));
        frame.render_widget(gauge, area);
    }

    /// The node's base style: colors + weight/style/underline/strikethrough.
    fn node_style(&self, node: &pathland_host::HostNode) -> Style {
        let mut s = Style::default();
        if let Some(argb) = node.properties.get(&property_id::COLOR).copied() {
            s = s.fg(argb_to_color(argb));
        }
        if let Some(argb) = node.properties.get(&property_id::BACKGROUND_COLOR).copied() {
            s = s.bg(argb_to_color(argb));
        }
        if node.f32_property(property_id::FONT_WEIGHT, 400.0) >= 600.0 {
            s = s.add_modifier(Modifier::BOLD);
        }
        if enum_code(node, property_id::FONT_STYLE, 0) == 1 {
            s = s.add_modifier(Modifier::ITALIC);
        }
        if truthy(node, property_id::UNDERLINE) {
            s = s.add_modifier(Modifier::UNDERLINED);
        }
        if truthy(node, property_id::STRIKETHROUGH) {
            s = s.add_modifier(Modifier::CROSSED_OUT);
        }
        s
    }
}

impl Default for TuiRenderer {
    fn default() -> Self {
        Self::new()
    }
}

/// An ENUM property code, accepting both the f32-encoded form
/// (`(code as f32).to_bits()`, the Rust/Java DSL emitters) and a raw low-byte.
fn enum_code(node: &pathland_host::HostNode, prop: u16, default: u8) -> u8 {
    let raw = node
        .properties
        .get(&prop)
        .copied()
        .unwrap_or(default as u32);
    let f = f32::from_bits(raw);
    if (0.0..=255.0).contains(&f) && f.fract() == 0.0 {
        f as u8
    } else {
        (raw & 0xFF) as u8
    }
}

/// A bool-ish property, accepting raw 0/1 and f32 0.0/1.0 encodings.
fn truthy(node: &pathland_host::HostNode, prop: u16) -> bool {
    match node.properties.get(&prop) {
        None => false,
        Some(raw) => {
            let f = f32::from_bits(*raw);
            if f == 0.0 || f == 1.0 {
                f == 1.0
            } else {
                *raw != 0
            }
        }
    }
}

/// `0xAARRGGBB` → a ratatui 24-bit color (the alpha byte is ignored).
fn argb_to_color(argb: u32) -> Color {
    Color::Rgb(
        ((argb >> 16) & 0xFF) as u8,
        ((argb >> 8) & 0xFF) as u8,
        (argb & 0xFF) as u8,
    )
}

fn tui_align(code: u8) -> TuiAlign {
    match code {
        1 => TuiAlign::Center,
        2 => TuiAlign::Right,
        _ => TuiAlign::Left,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use pathland_host::HostNode;
    use ratatui::backend::TestBackend;
    use ratatui::Terminal;

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

    fn text_node(id: u32, text: &str) -> HostNode {
        HostNode {
            id,
            component_type: component_type::TEXT,
            text: Some(text.to_string()),
            parent: None,
            children: vec![],
            properties: Default::default(),
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

    fn draw(t: RenderTree, w: u16, h: u16) -> ratatui::buffer::Buffer {
        let mut renderer = TuiRenderer {
            tree: t,
            rects: HashMap::new(),
            tick: 0,
        };
        let backend = TestBackend::new(w, h);
        let mut terminal = Terminal::new(backend).unwrap();
        terminal
            .draw(|f| {
                let area = f.area();
                renderer.draw(f, area);
            })
            .unwrap();
        terminal.backend().buffer().clone()
    }

    #[test]
    fn renders_text_at_origin() {
        let buf = draw(tree(vec![
            node(1, component_type::VSTACK, vec![2], &[]),
            text_node(2, "ab"),
        ]), 20, 5);
        assert_eq!(buf.cell((0, 0)).unwrap().symbol(), "a");
        assert_eq!(buf.cell((1, 0)).unwrap().symbol(), "b");
    }

    #[test]
    fn renders_two_texts_with_spacing_gap() {
        let buf = draw(tree(vec![
            node(1, component_type::VSTACK, vec![2, 3], &[(property_id::SPACING, 2.0f32.to_bits())]),
            text_node(2, "ab"),
            text_node(3, "cd"),
        ]), 20, 5);
        assert_eq!(buf.cell((0, 0)).unwrap().symbol(), "a");
        assert_eq!(buf.cell((0, 3)).unwrap().symbol(), "c");
    }

    #[test]
    fn renders_hstack_side_by_side() {
        let buf = draw(tree(vec![
            node(1, component_type::HSTACK, vec![2, 3], &[]),
            text_node(2, "a"),
            text_node(3, "b"),
        ]), 20, 3);
        assert_eq!(buf.cell((0, 0)).unwrap().symbol(), "a");
        assert_eq!(buf.cell((1, 0)).unwrap().symbol(), "b");
    }

    #[test]
    fn renders_button_with_border() {
        let buf = draw(tree(vec![
            node(
                1,
                component_type::BUTTON,
                vec![],
                &[(property_id::BORDER_WIDTH, 1.0f32.to_bits())],
            )
            .with_text("Go"),
        ]), 10, 3);
        assert_eq!(buf.cell((0, 0)).unwrap().symbol(), "┌");
        assert_eq!(buf.cell((1, 1)).unwrap().symbol(), "G");
        assert_eq!(buf.cell((2, 1)).unwrap().symbol(), "o");
    }

    #[test]
    fn renders_fill_color_background() {
        let buf = draw(tree(vec![
            node(1, component_type::VSTACK, vec![2], &[]),
            node(2, component_type::COLOR, vec![], &[(property_id::COLOR, 0xFF_FF0000)]),
        ]), 5, 3);
        let style = buf.cell((0, 0)).unwrap().style();
        assert_eq!(style.bg, Some(Color::Rgb(0xFF, 0x00, 0x00)));
    }

    #[test]
    fn renders_gauge_from_value_bounds() {
        let buf = draw(tree(vec![
            node(1, component_type::GAUGE, vec![], &[
                (property_id::MIN_VALUE, 0.0f32.to_bits()),
                (property_id::MAX_VALUE, 100.0f32.to_bits()),
                (property_id::VALUE, 50.0f32.to_bits()),
            ]),
        ]), 20, 1);
        // LineGauge shows the ratio as a percentage label (" 50% ") + bar.
        assert_eq!(buf.cell((1, 0)).unwrap().symbol(), "5");
        assert_eq!(buf.cell((2, 0)).unwrap().symbol(), "0");
        assert_eq!(buf.cell((3, 0)).unwrap().symbol(), "%");
    }

    trait WithText {
        fn with_text(self, text: &str) -> Self;
    }
    impl WithText for HostNode {
        fn with_text(mut self, text: &str) -> Self {
            self.text = Some(text.to_string());
            self
        }
    }
}