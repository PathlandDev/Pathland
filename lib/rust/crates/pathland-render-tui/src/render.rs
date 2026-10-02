//! The draw pass: retained tree → ratatui widgets.
//!
//! Each terminal frame redraws the whole UI from the retained
//! [`RenderTree`](pathland_host::RenderTree) into the areas computed by
//! [`crate::layout`]. The renderer keeps the per-frame `id → Rect` map for
//! hit-testing; it never retains application state (AGENTS.md Principle 1).

use std::collections::HashMap;

use pathland_core::{component_type, listener, property_id, Event};
use pathland_host::RenderTree;
use ratatui::crossterm::event::{
    KeyCode, KeyEvent, KeyEventKind, KeyModifiers, MouseButton, MouseEvent, MouseEventKind,
};
use ratatui::layout::{Alignment as TuiAlign, Position, Rect};
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
    /// Node ids in draw order (later = on top), for hit-testing.
    draw_order: Vec<u32>,
    /// Frame counter (spinner animation).
    tick: u64,
    /// The focused control (keyboard/mouse focus).
    focus: Option<u32>,
    /// The node pressed with the mouse (for click routing on release).
    mouse_down: Option<u32>,
}

impl TuiRenderer {
    pub fn new() -> Self {
        Self {
            tree: RenderTree::default(),
            rects: HashMap::new(),
            draw_order: Vec::new(),
            tick: 0,
            focus: None,
            mouse_down: None,
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

    /// The focused control id.
    pub fn focus(&self) -> Option<u32> {
        self.focus
    }

    /// Set the focused control id (None clears it).
    pub fn set_focus(&mut self, id: Option<u32>) {
        self.focus = id;
    }

    /// True when the focused control is a text input (so a `q` types, not quits).
    pub fn has_text_focus(&self) -> bool {
        self.focus.map_or(false, |id| {
            self.tree.node(id).map_or(false, |n| is_text_input(n.component_type))
        })
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
        self.draw_order.clear();
        if let Some(root) = self.tree.root() {
            self.rects = layout::arrange(&self.tree, root.id, area);
            self.draw_node(frame, root.id);
        }
    }

    /// Map a crossterm terminal event to protocol [`Event`]s, updating focus.
    ///
    /// The returned events are written by the caller through the pump
    /// (`DriverTransport::send_input`), which also resolves `TEXT_CHANGED`
    /// strings into the event arena.
    pub fn on_input(&mut self, ev: &ratatui::crossterm::event::Event) -> Vec<Event> {
        match ev {
            ratatui::crossterm::event::Event::Mouse(m) => self.on_mouse(m),
            ratatui::crossterm::event::Event::Key(k) => self.on_key(k),
            _ => Vec::new(),
        }
    }

    // -- input ---------------------------------------------------------------

    fn on_mouse(&mut self, m: &MouseEvent) -> Vec<Event> {
        // crossterm mouse columns/rows are 1-based.
        let col = m.column.saturating_sub(1);
        let row = m.row.saturating_sub(1);
        match m.kind {
            MouseEventKind::Down(MouseButton::Left) => {
                let target = self.hit_test(col, row);
                self.mouse_down = target;
                if target.is_some() {
                    self.focus = target;
                }
                self.pointer_event(target, CMD_POINTER_DOWN, col, row)
            }
            MouseEventKind::Up(MouseButton::Left) => {
                let target = self.mouse_down.or_else(|| self.hit_test(col, row));
                self.mouse_down = None;
                self.pointer_event(target, CMD_POINTER_UP, col, row)
            }
            MouseEventKind::Moved | MouseEventKind::Drag(_) => {
                self.pointer_event(self.hit_test(col, row), CMD_POINTER_MOVE, col, row)
            }
            MouseEventKind::ScrollUp => self.wheel_event(self.hit_test(col, row), 0, -1),
            MouseEventKind::ScrollDown => self.wheel_event(self.hit_test(col, row), 0, 1),
            _ => Vec::new(),
        }
    }

    fn on_key(&mut self, k: &KeyEvent) -> Vec<Event> {
        if k.kind != KeyEventKind::Press {
            return Vec::new();
        }
        match k.code {
            KeyCode::Tab => {
                self.advance_focus();
                return Vec::new();
            }
            KeyCode::BackTab => {
                self.retreat_focus();
                return Vec::new();
            }
            _ => {}
        }
        let Some(fid) = self.focus else {
            return Vec::new();
        };
        let Some(node) = self.tree.node(fid).cloned() else {
            self.focus = None;
            return Vec::new();
        };
        match node.component_type {
            component_type::TEXT_FIELD | component_type::TEXT_EDITOR => self.text_key(fid, &node, k),
            component_type::BUTTON => match k.code {
                KeyCode::Enter | KeyCode::Char(' ') => self.activate(fid),
                _ => self.key_event(fid, k),
            },
            component_type::TOGGLE => match k.code {
                KeyCode::Enter | KeyCode::Char(' ') => self.toggle_value(fid, &node),
                _ => self.key_event(fid, k),
            },
            component_type::SLIDER => match k.code {
                KeyCode::Left | KeyCode::Down => self.adjust_value(fid, &node, -1),
                KeyCode::Right | KeyCode::Up => self.adjust_value(fid, &node, 1),
                _ => self.key_event(fid, k),
            },
            component_type::STEPPER => match k.code {
                KeyCode::Left | KeyCode::Down => self.adjust_value(fid, &node, -1),
                KeyCode::Right | KeyCode::Up => self.adjust_value(fid, &node, 1),
                KeyCode::Enter | KeyCode::Char(' ') => self.activate(fid),
                _ => self.key_event(fid, k),
            },
            _ => self.key_event(fid, k),
        }
    }

    /// The top-most node under a terminal cell (draw order, last drawn = on top).
    fn hit_test(&self, col: u16, row: u16) -> Option<u32> {
        let pos = Position::new(col, row);
        for id in self.draw_order.iter().rev() {
            if let Some(r) = self.rects.get(id) {
                if r.contains(pos) {
                    return Some(*id);
                }
            }
        }
        None
    }

    /// A node wants a raw input if it declares the listener bit, or (for
    /// buttons) the default pointer listeners.
    fn node_wants(&self, id: u32, bit: u32) -> bool {
        self.tree.node(id).map_or(false, |n| {
            let mask = n.u32_property(property_id::EVENT_LISTENERS, 0)
                | default_listeners(n.component_type);
            mask & bit != 0
        })
    }

    fn pointer_event(&self, id: Option<u32>, command: u8, col: u16, row: u16) -> Vec<Event> {
        let Some(id) = id else { return Vec::new() };
        let bit = match command {
            CMD_POINTER_DOWN => listener::POINTER_DOWN,
            CMD_POINTER_UP => listener::POINTER_UP,
            _ => listener::POINTER_MOVE,
        };
        if !self.node_wants(id, bit) {
            return Vec::new();
        }
        let ev = match command {
            CMD_POINTER_DOWN => Event::PointerDown { target: id, x: col as f32, y: row as f32, secondary: false },
            CMD_POINTER_UP => Event::PointerUp { target: id, x: col as f32, y: row as f32, secondary: false },
            _ => Event::PointerMove { target: id, x: col as f32, y: row as f32, hovering: true, leaving: false },
        };
        vec![ev]
    }

    fn wheel_event(&self, id: Option<u32>, dx: i32, dy: i32) -> Vec<Event> {
        let Some(id) = id else { return Vec::new() };
        if !self.node_wants(id, listener::WHEEL) {
            return Vec::new();
        }
        vec![Event::Wheel { target: id, delta_x: dx as f32, delta_y: dy as f32 }]
    }

    fn key_event(&self, id: u32, k: &KeyEvent) -> Vec<Event> {
        if !self.node_wants(id, listener::KEY_DOWN) {
            return Vec::new();
        }
        let code = key_code(k.code);
        let modifiers = key_modifiers(k.modifiers);
        vec![Event::KeyDown { target: id, key_code: code, modifiers, repeat: k.kind == KeyEventKind::Repeat }]
    }

    /// Activate a focused button by synthesizing a press+release (the app's
    /// tap recognizer turns it into a tap).
    fn activate(&self, id: u32) -> Vec<Event> {
        let Some(r) = self.rects.get(&id) else { return Vec::new() };
        if !self.node_wants(id, listener::POINTER) {
            return Vec::new();
        }
        let x = (r.x + r.width / 2) as f32;
        let y = (r.y + r.height / 2) as f32;
        vec![
            Event::PointerDown { target: id, x, y, secondary: false },
            Event::PointerUp { target: id, x, y, secondary: false },
        ]
    }

    /// Adjust a slider/stepper value (arrows) and report it as `VALUE_CHANGED`
    /// when the control carries a `BINDING_ID`.
    fn adjust_value(&self, id: u32, node: &pathland_host::HostNode, dir: i32) -> Vec<Event> {
        let min = node.f32_property(property_id::MIN_VALUE, 0.0);
        let max = node.f32_property(property_id::MAX_VALUE, 100.0);
        let step = node.f32_property(property_id::STEP_VALUE, 1.0).max(0.01);
        let current = node.f32_property(property_id::VALUE, min);
        let value = (current + dir as f32 * step).clamp(min, max);
        self.value_event(id, node, value)
    }

    fn toggle_value(&self, id: u32, node: &pathland_host::HostNode) -> Vec<Event> {
        let value = if node.checked() { 0.0 } else { 1.0 };
        self.value_event(id, node, value)
    }

    fn value_event(&self, id: u32, node: &pathland_host::HostNode, value: f32) -> Vec<Event> {
        if node.u32_property(property_id::BINDING_ID, 0) != 0 {
            vec![Event::ValueChanged { target: id, value }]
        } else {
            Vec::new()
        }
    }

    /// Typing into a focused text input → `TEXT_CHANGED` (BINDING_ID-gated);
    /// Enter → `SUBMIT` when the node listens for it.
    fn text_key(&self, id: u32, node: &pathland_host::HostNode, k: &KeyEvent) -> Vec<Event> {
        let current = node.text.clone().unwrap_or_default();
        let mut out = Vec::new();
        match k.code {
            KeyCode::Char(c) if !k.modifiers.contains(KeyModifiers::CONTROL) => {
                let mut value = current;
                value.push(c);
                if node.u32_property(property_id::BINDING_ID, 0) != 0 {
                    out.push(Event::TextChanged { target: id, value });
                }
            }
            KeyCode::Backspace => {
                let mut value = current;
                value.pop();
                if node.u32_property(property_id::BINDING_ID, 0) != 0 {
                    out.push(Event::TextChanged { target: id, value });
                }
            }
            KeyCode::Enter => {
                if self.node_wants(id, listener::SUBMIT) {
                    out.push(Event::Submit { target: id });
                }
            }
            _ => {}
        }
        out.extend(self.key_event(id, k));
        out
    }

    fn focusables(&self) -> Vec<u32> {
        let mut out = Vec::new();
        if let Some(root) = self.tree.root() {
            walk_focusables(&self.tree, root.id, &mut out);
        }
        out
    }

    fn advance_focus(&mut self) {
        let list = self.focusables();
        if list.is_empty() {
            self.focus = None;
            return;
        }
        let next = match self.focus {
            Some(cur) => list.iter().position(|id| *id == cur).map_or(0, |i| (i + 1) % list.len()),
            None => 0,
        };
        self.focus = Some(list[next]);
    }

    fn retreat_focus(&mut self) {
        let list = self.focusables();
        if list.is_empty() {
            self.focus = None;
            return;
        }
        let next = match self.focus {
            Some(cur) => list
                .iter()
                .position(|id| *id == cur)
                .map_or(0, |i| (i + list.len() - 1) % list.len()),
            None => 0,
        };
        self.focus = Some(list[next]);
    }

    fn draw_node(&mut self, frame: &mut Frame, id: u32) {
        self.draw_order.push(id);
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
            component_type::TOGGLE => self.draw_toggle(frame, &node, area),
            component_type::SLIDER => self.draw_slider(frame, &node, area),
            component_type::STEPPER => self.draw_stepper(frame, &node, area),
            component_type::TEXT_FIELD | component_type::TEXT_EDITOR => {
                self.draw_text_input(frame, &node, area);
            }
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

    /// A slider renders as a `LineGauge` at the value's ratio (min/max).
    fn draw_slider(&self, frame: &mut Frame, node: &pathland_host::HostNode, area: Rect) {
        self.draw_gauge(frame, node, area);
    }

    /// A toggle renders as `[x] label` / `[ ] label` (switch style: `[=]`).
    fn draw_toggle(&self, frame: &mut Frame, node: &pathland_host::HostNode, area: Rect) {
        let label = node
            .string_property(property_id::LABEL)
            .or(node.text.as_deref())
            .unwrap_or("");
        let boxed = if node.checked() { "[x]" } else { "[ ]" };
        let text = format!("{boxed} {label}");
        frame.render_widget(Paragraph::new(Text::from(text)).style(self.node_style(node)), area);
    }

    /// A stepper renders as `− value +` (value from `VALUE`).
    fn draw_stepper(&self, frame: &mut Frame, node: &pathland_host::HostNode, area: Rect) {
        let value = node.f32_property(property_id::VALUE, 0.0);
        let value = format_float(value);
        let text = format!("− {value} +");
        frame.render_widget(Paragraph::new(Text::from(text)).style(self.node_style(node)), area);
    }

    /// A text field/editor renders its current text (with a focus cursor).
    fn draw_text_input(&mut self, frame: &mut Frame, node: &pathland_host::HostNode, area: Rect) {
        let value = node.text.clone().unwrap_or_default();
        let style = self.node_style(node);
        let lines: Vec<Line> = value
            .split('\n')
            .map(|l| Line::from(Span::styled(l.to_string(), style)))
            .collect();
        frame.render_widget(Paragraph::new(Text::from(lines)), area);
        if self.focus == Some(node.id) {
            // Place the terminal cursor at the end of the input.
            let last = value.split('\n').last().unwrap_or("");
            let x = (area.x + Line::from(last.to_string()).width() as u16).min(area.right().saturating_sub(1));
            let y = (area.y + value.split('\n').count() as u16 - 1).min(area.bottom().saturating_sub(1));
            frame.set_cursor_position(Position::new(x, y));
        }
    }

    /// The node's base style: colors + weight/style/underline/strikethrough;
    /// the focused control is rendered reversed.
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
        if self.focus == Some(node.id) {
            s = s.add_modifier(Modifier::REVERSED);
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

const CMD_POINTER_DOWN: u8 = 1;
const CMD_POINTER_UP: u8 = 2;
const CMD_POINTER_MOVE: u8 = 3;

/// A component is focusable (keyboard + mouse target).
fn is_focusable(component: u16) -> bool {
    matches!(
        component,
        component_type::BUTTON
            | component_type::TOGGLE
            | component_type::SLIDER
            | component_type::STEPPER
            | component_type::TEXT_FIELD
            | component_type::TEXT_EDITOR
            | component_type::PICKER
            | component_type::MENU
            | component_type::DATE_PICKER
    )
}

fn is_text_input(component: u16) -> bool {
    matches!(component, component_type::TEXT_FIELD | component_type::TEXT_EDITOR)
}

fn default_listeners(component: u16) -> u32 {
    if component == component_type::BUTTON {
        listener::POINTER
    } else {
        0
    }
}

fn walk_focusables(tree: &RenderTree, id: u32, out: &mut Vec<u32>) {
    if let Some(n) = tree.node(id) {
        if is_focusable(n.component_type) {
            out.push(id);
        }
        let children = n.children.clone();
        for cid in children {
            walk_focusables(tree, cid, out);
        }
    }
}

/// A protocol key code for a crossterm key (renderer-defined mapping; printable
/// keys use their code point, special keys use the 0x01xx range).
fn key_code(code: KeyCode) -> u16 {
    match code {
        KeyCode::Char(c) => c as u16,
        KeyCode::Enter => 0x0D,
        KeyCode::Esc => 0x1B,
        KeyCode::Tab => 0x09,
        KeyCode::Backspace => 0x08,
        KeyCode::Delete => 0x7F,
        KeyCode::Home => 0x0105,
        KeyCode::End => 0x0106,
        KeyCode::PageUp => 0x0107,
        KeyCode::PageDown => 0x0108,
        KeyCode::Left => 0x0101,
        KeyCode::Right => 0x0102,
        KeyCode::Up => 0x0103,
        KeyCode::Down => 0x0104,
        _ => 0,
    }
}

/// Protocol modifiers byte from crossterm modifiers.
fn key_modifiers(m: KeyModifiers) -> u8 {
    let mut out = 0u8;
    if m.contains(KeyModifiers::SHIFT) {
        out |= 1;
    }
    if m.contains(KeyModifiers::CONTROL) {
        out |= 2;
    }
    if m.contains(KeyModifiers::ALT) {
        out |= 4;
    }
    if m.contains(KeyModifiers::META) {
        out |= 8;
    }
    out
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
            draw_order: Vec::new(),
            tick: 0,
            focus: None,
            mouse_down: None,
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

    fn draw_once(t: RenderTree, w: u16, h: u16) -> TuiRenderer {
        let mut renderer = TuiRenderer::new();
        renderer.tree = t;
        let backend = TestBackend::new(w, h);
        let mut terminal = Terminal::new(backend).unwrap();
        terminal
            .draw(|f| {
                let area = f.area();
                renderer.draw(f, area);
            })
            .unwrap();
        renderer
    }

    fn mouse(kind: MouseEventKind, col: u16, row: u16) -> ratatui::crossterm::event::Event {
        ratatui::crossterm::event::Event::Mouse(MouseEvent {
            kind,
            column: col,
            row,
            modifiers: KeyModifiers::NONE,
        })
    }

    #[test]
    fn click_routes_pointer_events_to_the_button() {
        let mut renderer = draw_once(
            tree(vec![
                node(1, component_type::VSTACK, vec![2], &[]),
                node(2, component_type::BUTTON, vec![], &[]).with_text("Go"),
            ]),
            20,
            5,
        );
        let down = renderer.on_input(&mouse(MouseEventKind::Down(MouseButton::Left), 1, 1));
        assert_eq!(
            down,
            vec![Event::PointerDown { target: 2, x: 0.0, y: 0.0, secondary: false }]
        );
        let up = renderer.on_input(&mouse(MouseEventKind::Up(MouseButton::Left), 1, 1));
        assert_eq!(
            up,
            vec![Event::PointerUp { target: 2, x: 0.0, y: 0.0, secondary: false }]
        );
    }

    #[test]
    fn tab_cycles_focus_between_buttons() {
        let mut renderer = draw_once(
            tree(vec![
                node(1, component_type::VSTACK, vec![2, 3], &[(property_id::SPACING, 1.0f32.to_bits())]),
                node(2, component_type::BUTTON, vec![], &[]).with_text("a"),
                node(3, component_type::BUTTON, vec![], &[]).with_text("b"),
            ]),
            20,
            5,
        );
        let key = |code: KeyCode| ratatui::crossterm::event::Event::Key(KeyEvent {
            code,
            modifiers: KeyModifiers::NONE,
            kind: KeyEventKind::Press,
            state: ratatui::crossterm::event::KeyEventState::NONE,
        });
        renderer.on_input(&key(KeyCode::Tab));
        assert_eq!(renderer.focus(), Some(2));
        renderer.on_input(&key(KeyCode::Tab));
        assert_eq!(renderer.focus(), Some(3));
    }

    #[test]
    fn slider_arrow_reports_value_changed() {
        let mut renderer = draw_once(
            tree(vec![
                node(
                    1,
                    component_type::SLIDER,
                    vec![],
                    &[
                        (property_id::MIN_VALUE, 0.0f32.to_bits()),
                        (property_id::MAX_VALUE, 100.0f32.to_bits()),
                        (property_id::VALUE, 50.0f32.to_bits()),
                        (property_id::STEP_VALUE, 1.0f32.to_bits()),
                        (property_id::BINDING_ID, 1),
                    ],
                ),
            ]),
            20,
            1,
        );
        renderer.set_focus(Some(1));
        let key = ratatui::crossterm::event::Event::Key(KeyEvent {
            code: KeyCode::Right,
            modifiers: KeyModifiers::NONE,
            kind: KeyEventKind::Press,
            state: ratatui::crossterm::event::KeyEventState::NONE,
        });
        assert_eq!(renderer.on_input(&key), vec![Event::ValueChanged { target: 1, value: 51.0 }]);
    }

    #[test]
    fn activate_focused_button_synthesizes_press_and_release() {
        let mut renderer = draw_once(
            tree(vec![
                node(1, component_type::VSTACK, vec![2], &[]),
                node(2, component_type::BUTTON, vec![], &[]).with_text("Go"),
            ]),
            20,
            5,
        );
        renderer.set_focus(Some(2));
        let key = ratatui::crossterm::event::Event::Key(KeyEvent {
            code: KeyCode::Enter,
            modifiers: KeyModifiers::NONE,
            kind: KeyEventKind::Press,
            state: ratatui::crossterm::event::KeyEventState::NONE,
        });
        let events = renderer.on_input(&key);
        assert_eq!(events.len(), 2);
        assert!(matches!(&events[0], Event::PointerDown { target: 2, .. }));
        assert!(matches!(&events[1], Event::PointerUp { target: 2, .. }));
    }
}