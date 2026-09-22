//! # pathland-view
//!
//! The Pathland SwiftUI-style view DSL. Core components (`VStack`, `HStack`,
//! `Text`) and chainable modifiers (`spacing`, `padding`, `font_size`, `color`,
//! `background`) build a retained **view tree** (`pathland_engine::Node`) that the
//! diff emitter in `pathland-core` turns into declarative `TREE`/`STYLE` opcodes.
//!
//! ## Building a tree
//!
//! Use the `vstack!`/`hstack!` macros (which box children for you) with the
//! free `text`/`spacer` functions, then chain modifiers and `build()`:
//!
//! ```
//! use pathland_view::{vstack, hstack, text, View, ViewExt};
//!
//! let node = vstack![
//!     text("a"),
//!     hstack![text("b")],
//! ]
//! .padding(16.0)
//! .build();
//! # let _ = node;
//! ```
//!
//! ## Decoupled modifiers
//!
//! Modifiers are **decoupled from views** (SwiftUI-style): any modifier applies
//! to any view via the blanket `View::modifier`, and application code composes
//! **custom modifiers** from the core ones by implementing `ViewModifier`:
//!
//! ```
//! use pathland_view::{View, ViewExt, ViewModifier, Node, Padding, Background, Color};
//!
//! struct Card;
//! impl ViewModifier for Card {
//!     fn apply(&self, node: &mut Node) {
//!         Padding(16.0).apply(node);
//!         Background(Color::argb(0xFF_EEEEEE)).apply(node);
//!     }
//! }
//!
//! // Works on any view:
//! let a = pathland_view::text("A").modifier(Card);
//! let b = pathland_view::vstack![].modifier(Card);
//! # let _ = (a, b);
//! ```
//!
//! Unsupported combinations are **allowed and ignored**: the modifier emits its
//! property and the renderer ignores what it cannot apply.
//!
//! ## No layout math
//!
//! The DSL describes structure + constraint properties only. It never computes
//! bounds; native renderers lay out their own elements.
//!
//! This crate is `no_std` + `alloc`.

#![no_std]
#![forbid(unsafe_op_in_unsafe_fn)]

#[cfg(test)]
extern crate std;

extern crate alloc;

use alloc::collections::BTreeMap;
use alloc::rc::Rc;
use alloc::string::String;
use alloc::vec::Vec;
use core::cell::RefCell;

/// Re-exported for `vstack!`/`hstack!` macro hygiene (`$crate::Box`).
pub use alloc::boxed::Box;
/// Re-exported for `vstack!`/`hstack!` macro hygiene (`$crate::vec!`).
pub use alloc::vec;

use pathland_core::property_id;

pub use pathland_core;
pub use pathland_engine::{
    assign_ids, collect_tap_handlers, component_type_id, AdaptiveTheme, Component, Engine, Gesture,
    Node, Theme,
};

mod recognizer;

pub use recognizer::TapRecognizer;

// ---------------------------------------------------------------------------
// Core component macros + free functions
// ---------------------------------------------------------------------------

/// Build a vertical stack from a comma-separated list of views.
///
/// Each child is boxed automatically, so children can mix `Text`, `HStack`,
/// modified views, or custom `View` implementations. Nested stacks work:
///
/// ```
/// use pathland_view::{vstack, text, View};
/// let node = vstack![text("a"), text("b")].build();
/// # let _ = node;
/// ```
#[macro_export]
macro_rules! vstack {
    ($($child:expr),* $(,)?) => {
        $crate::VStack::new().children($crate::vec![$($crate::Box::new($child)),*])
    };
}

/// Build a horizontal stack from a comma-separated list of views.
///
/// See [`vstack!`] for details.
#[macro_export]
macro_rules! hstack {
    ($($child:expr),* $(,)?) => {
        $crate::HStack::new().children($crate::vec![$($crate::Box::new($child)),*])
    };
}

/// A text view from a string literal (shorthand for [`Text::new`]).
pub fn text(content: &str) -> Text {
    Text::new(content)
}

/// A flexible spacer (shorthand for [`Spacer`]).
pub fn spacer() -> Spacer {
    Spacer
}

/// Cross-axis alignment values for stacks and the `Frame` compound modifier.
///
/// These are emitted as the `ALIGNMENT` (0x0002) property with an `ENUM` value
/// type (low byte of the property value).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Align {
    /// Align to the leading (start) edge.
    Leading,
    /// Center along the cross axis.
    Center,
    /// Align to the trailing (end) edge.
    Trailing,
    /// Fill the available cross-axis space.
    Fill,
}

impl Align {
    /// The protocol enum value for this alignment.
    pub fn value(self) -> u8 {
        match self {
            Align::Leading => 0,
            Align::Center => 1,
            Align::Trailing => 2,
            Align::Fill => 3,
        }
    }
}

// ---------------------------------------------------------------------------
// View protocol
// ---------------------------------------------------------------------------

/// A composable view. Implement for custom views; the core components and
/// `Modified` wrappers implement it already.
pub trait View {
    /// Build the retained node for this view.
    fn build(&self) -> Node;
}

/// A view decorator (SwiftUI `ViewModifier`): transforms a node's properties.
///
/// Core modifiers (`Spacing`, `Padding`, `FontSize`, `Color`, `Background`)
/// implement this; application code implements it to compose custom modifiers.
pub trait ViewModifier {
    /// Apply the modifier to `node` (mutating constraint properties).
    fn apply(&self, node: &mut Node);
}

/// A view wrapped by a modifier (SwiftUI `some View` nesting).
#[derive(Debug, Clone, PartialEq)]
pub struct Modified<V, M> {
    view: V,
    modifier: M,
}

impl<V: View, M: ViewModifier> View for Modified<V, M> {
    fn build(&self) -> Node {
        let mut node = self.view.build();
        self.modifier.apply(&mut node);
        node
    }
}

/// Blanket modifier methods on any `View`.
///
/// `.modifier(...)` applies a custom (or core) modifier; the sugar methods
/// (`spacing`, `padding`, `font_size`, `color`, `background`) are conveniences
/// that construct the corresponding core modifier. Modifiers apply
/// innermost-first, matching SwiftUI ordering.
pub trait ViewExt: View + Sized {
    /// Apply a modifier to this view.
    fn modifier<M: ViewModifier>(self, modifier: M) -> Modified<Self, M> {
        Modified { view: self, modifier }
    }

    /// Chain `.spacing(value)`.
    fn spacing(self, value: f32) -> Modified<Self, Spacing> {
        self.modifier(Spacing(value))
    }

    /// Chain `.padding(value)`.
    fn padding(self, value: f32) -> Modified<Self, Padding> {
        self.modifier(Padding(value))
    }

    /// Chain `.font_size(value)`.
    fn font_size(self, value: f32) -> Modified<Self, FontSize> {
        self.modifier(FontSize(value))
    }

    /// Chain `.foreground_style(color)` — the foreground color
    /// (a `COLOR` property). There is deliberately **no** `.color()` /
    /// `.foregroundColor()` modifier; foreground styling is
    /// `.foreground_style(_:)` (SwiftUI `.foregroundStyle`).
    fn foreground_style(self, color: Color) -> Modified<Self, ForegroundStyle> {
        self.modifier(ForegroundStyle(color))
    }

    /// Chain `.background(color)` — the background color.
    fn background(self, color: Color) -> Modified<Self, Background> {
        self.modifier(Background(color))
    }

    /// Chain `.border(color, width)`.
    fn border(self, color: Color, width: f32) -> Modified<Self, Border> {
        self.modifier(Border { color, width })
    }

    /// Chain `.tint(color)` — the accent/tint color (a `TINT` property).
    fn tint(self, color: Color) -> Modified<Self, Tint> {
        self.modifier(Tint(color))
    }

    /// Chain `.frame(width, height, alignment)` — a compound sizing modifier.
    ///
    /// `width`/`height` use the `size::FILL` (-1.0) and `size::HUG_CONTENT`
    /// (-2.0) sentinels, or `None` to leave that axis to the native renderer.
    /// A positive/negative infinite value (e.g. `f32::INFINITY`, SwiftUI
    /// `maxWidth: .infinity`) is normalized to `size::FILL`. When an alignment
    /// is provided it is emitted as the `ALIGNMENT` property.
    fn frame(
        self,
        width: Option<f32>,
        height: Option<f32>,
        alignment: Option<Align>,
    ) -> Modified<Self, Frame> {
        self.modifier(Frame {
            width,
            height,
            alignment,
        })
    }

    /// Chain `.pointer_events(mask)` — declare which raw pointer events this
    /// view wants the renderer to report (`EVENT_LISTENERS` bitmask). This is
    /// what makes any element (not just a button) emit raw events; combine
    /// `pathland_core::listener::*` bits, e.g.
    /// `POINTER_DOWN | POINTER_UP`.
    fn pointer_events(self, mask: u32) -> Modified<Self, PointerEvents> {
        self.modifier(PointerEvents(mask))
    }

    /// Chain `.on_tap_gesture(f)` — attach a tap gesture (SwiftUI
    /// `onTapGesture`) to any view. The callback `f` runs when a tap is
    /// recognized from the view's raw pointer events (down then up on the same
    /// target).
    fn on_tap_gesture<F: FnMut() + 'static>(self, f: F) -> Modified<Self, TapGesture> {
        self.modifier(TapGesture(Rc::new(RefCell::new(f))))
    }

    /// Chain `.opacity(value)` (0..1).
    fn opacity(self, value: f32) -> Modified<Self, Opacity> {
        self.modifier(Opacity(value))
    }

    /// Chain `.hidden()` — hide the view (`VISIBLE` = 0).
    fn hidden(self) -> Modified<Self, Hidden> {
        self.modifier(Hidden)
    }

    /// Chain `.corner_radius(value)`.
    fn corner_radius(self, value: f32) -> Modified<Self, CornerRadius> {
        self.modifier(CornerRadius(value))
    }

    /// Chain `.font_weight(value)` (100–900).
    fn font_weight(self, value: f32) -> Modified<Self, FontWeight> {
        self.modifier(FontWeight(value))
    }

    /// Chain `.line_limit(n)` (0 = unlimited).
    fn line_limit(self, n: u32) -> Modified<Self, LineLimit> {
        self.modifier(LineLimit(n))
    }

    /// Chain `.text_alignment(a)` (0=Leading, 1=Center, 2=Trailing).
    fn text_alignment(self, a: u8) -> Modified<Self, TextAlignment> {
        self.modifier(TextAlignment(a))
    }

    /// Chain `.truncation_mode(m)` (0=Head, 1=Middle, 2=Tail).
    fn truncation_mode(self, m: u8) -> Modified<Self, TruncationMode> {
        self.modifier(TruncationMode(m))
    }

    /// Chain `.offset(x, y)` — post-layout translation.
    fn offset(self, x: f32, y: f32) -> Modified<Self, Offset> {
        self.modifier(Offset { x, y })
    }

    /// Chain `.position(x, y)` — absolute placement within the parent.
    fn position(self, x: f32, y: f32) -> Modified<Self, Position> {
        self.modifier(Position { x, y })
    }

    /// Chain `.z_index(value)`.
    fn z_index(self, value: f32) -> Modified<Self, ZIndex> {
        self.modifier(ZIndex(value))
    }
}

impl<T: View> ViewExt for T {}

// ---------------------------------------------------------------------------
// Core modifiers
// ---------------------------------------------------------------------------

/// Stack main-axis gap.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Spacing(pub f32);

/// Uniform padding (a styling modifier, applied to any view).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Padding(pub f32);

/// Text font size in points.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct FontSize(pub f32);

/// An sRGB color or a **design-token reference**, with dual identity mirroring
/// SwiftUI:
///
/// - **View** — placing a `Color` in a layout tree draws a solid-color fill
///   that is **layout-greedy** (expands to the available space unless a
///   `.frame`/size modifier constrains it).
/// - **Data type** — a `Color` value is passed into style-taking modifiers
///   (`.foreground_style`, `.background`, `.border`, `.tint`).
///
/// A literal packs sRGB `0xAARRGGBB`; `Color::token("color.primary")` references
/// a design token the renderer resolves against the active scheme
/// (spec/TOKENS.md) — emitted as the `DESIGN_TOKEN` value type, never a literal.
///
/// There is deliberately **no** `.color()` / `.foregroundColor()` modifier.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Color {
    /// A literal sRGB color (`0xAARRGGBB`).
    Literal(u32),
    /// A reference to a design token path (e.g. `color.primary`, `dark.color.primary`).
    Token(&'static str),
}

impl Color {
    /// A literal color from a packed `0xAARRGGBB` value.
    pub const fn argb(argb: u32) -> Self {
        Color::Literal(argb)
    }

    /// A reference to a design token path (e.g. `color.primary`).
    pub const fn token(path: &'static str) -> Self {
        Color::Token(path)
    }
}

impl View for Color {
    fn build(&self) -> Node {
        let p = BTreeMap::new();
        let mut node = plain_node(Component::Color, Vec::new(), p);
        match self {
            Color::Literal(v) => {
                node.properties.insert(property_id::COLOR, *v);
            }
            Color::Token(path) => {
                node.token_properties.insert(property_id::COLOR, String::from(*path));
            }
        }
        node
    }
}

/// Foreground color modifier (SwiftUI `.foregroundStyle`). There is no
/// `.color()` / `.foregroundColor()` modifier.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ForegroundStyle(pub Color);

/// Background color (a `Color` value).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Background(pub Color);

/// Accent/tint color (a `TINT` property).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Tint(pub Color);

/// The compound `frame` sizing modifier (SwiftUI `frame`).
///
/// Emits `WIDTH`, `HEIGHT`, and optionally `ALIGNMENT` as ordinary
/// `SET_PROPERTY` values. `width`/`height` use the `size::FILL` (-1.0) and
/// `size::HUG_CONTENT` (-2.0) sentinels; `None` leaves that axis to the native
/// renderer. Infinite values (SwiftUI `maxWidth/maxHeight: .infinity`) are
/// normalized to `size::FILL` when applied.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Frame {
    pub width: Option<f32>,
    pub height: Option<f32>,
    pub alignment: Option<Align>,
}

/// Declares which raw pointer events a view wants reported (the `EVENT_LISTENERS`
/// u32 bitmask, e.g. `listener::POINTER_DOWN | listener::POINTER_UP`). Applies to
/// any view; the renderer attaches native input recognition for the requested
/// events.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct PointerEvents(pub u32);

/// A tap-gesture modifier (SwiftUI `onTapGesture`). Declares the pointer
/// down/up listeners and stores the app-side callback in the node's gesture
/// list.
#[derive(Clone)]
pub struct TapGesture(Rc<RefCell<dyn FnMut() + 'static>>);

impl PartialEq for TapGesture {
    fn eq(&self, other: &Self) -> bool {
        Rc::ptr_eq(&self.0, &other.0)
    }
}

impl core::fmt::Debug for TapGesture {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.write_str("TapGesture(..)")
    }
}

impl ViewModifier for Spacing {
    fn apply(&self, node: &mut Node) {
        node.properties.insert(property_id::SPACING, self.0.to_bits());
    }
}

impl ViewModifier for Padding {
    fn apply(&self, node: &mut Node) {
        node.properties.insert(property_id::PADDING, self.0.to_bits());
    }
}

impl ViewModifier for FontSize {
    fn apply(&self, node: &mut Node) {
        node.properties
            .insert(property_id::FONT_SIZE, self.0.to_bits());
    }
}

impl ViewModifier for ForegroundStyle {
    fn apply(&self, node: &mut Node) {
        apply_color(node, property_id::COLOR, self.0);
    }
}

impl ViewModifier for Background {
    fn apply(&self, node: &mut Node) {
        apply_color(node, property_id::BACKGROUND_COLOR, self.0);
    }
}

impl ViewModifier for Tint {
    fn apply(&self, node: &mut Node) {
        apply_color(node, property_id::TINT, self.0);
    }
}

impl ViewModifier for Frame {
    fn apply(&self, node: &mut Node) {
        if let Some(w) = self.width {
            node.properties
                .insert(property_id::WIDTH, fill_or(w).to_bits());
        }
        if let Some(h) = self.height {
            node.properties
                .insert(property_id::HEIGHT, fill_or(h).to_bits());
        }
        if let Some(a) = self.alignment {
            node.properties
                .insert(property_id::ALIGNMENT, (a.value() as f32).to_bits());
        }
    }
}

/// Normalize an infinite size hint (SwiftUI `maxWidth/maxHeight: .infinity`)
/// to the `size::FILL` sentinel; finite values pass through unchanged.
fn fill_or(v: f32) -> f32 {
    if v.is_infinite() {
        pathland_core::size::FILL
    } else {
        v
    }
}

impl ViewModifier for PointerEvents {
    fn apply(&self, node: &mut Node) {
        // OR into any existing mask so multiple modifiers compose.
        let existing = node
            .properties
            .get(&property_id::EVENT_LISTENERS)
            .copied()
            .unwrap_or(0);
        node.properties
            .insert(property_id::EVENT_LISTENERS, existing | self.0);
    }
}

impl ViewModifier for TapGesture {
    fn apply(&self, node: &mut Node) {
        // Declare the raw pointer listeners the renderer must attach, then store
        // the callback in the app-side gesture list.
        let existing = node
            .properties
            .get(&property_id::EVENT_LISTENERS)
            .copied()
            .unwrap_or(0);
        node.properties.insert(
            property_id::EVENT_LISTENERS,
            existing
                | pathland_core::listener::POINTER_DOWN
                | pathland_core::listener::POINTER_UP,
        );
        node.gestures.push(Gesture::Tap(self.0.clone()));
    }
}

// ---------------------------------------------------------------------------
// Extended modifiers
// ---------------------------------------------------------------------------

/// Opacity (0..1). `.opacity(_:)`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Opacity(pub f32);

/// Hidden (SwiftUI `.hidden()`); sets `VISIBLE` = 0.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Hidden;

/// Border (SwiftUI `.border(color:width:)`).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Border {
    pub color: Color,
    pub width: f32,
}

/// Corner radius (SwiftUI `.cornerRadius(_:)`).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct CornerRadius(pub f32);

/// Font weight (100–900). `.fontWeight(_:)`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct FontWeight(pub f32);

/// Line limit (0 = unlimited). `.lineLimit(_:)`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct LineLimit(pub u32);

/// Text alignment (0=Leading, 1=Center, 2=Trailing).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct TextAlignment(pub u8);

/// Truncation mode (0=Head, 1=Middle, 2=Tail).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct TruncationMode(pub u8);

/// Post-layout translation. `.offset(x:y:)`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Offset {
    pub x: f32,
    pub y: f32,
}

/// Absolute position within the parent. `.position(x:y:)`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Position {
    pub x: f32,
    pub y: f32,
}

/// Z-index. `.zIndex(_:)`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ZIndex(pub f32);

impl ViewModifier for Opacity {
    fn apply(&self, node: &mut Node) {
        node.properties.insert(property_id::OPACITY, self.0.to_bits());
    }
}

impl ViewModifier for Hidden {
    fn apply(&self, node: &mut Node) {
        node.properties.insert(property_id::VISIBLE, 0);
    }
}

impl ViewModifier for Border {
    fn apply(&self, node: &mut Node) {
        apply_color(node, property_id::BORDER_COLOR, self.color);
        node.properties.insert(property_id::BORDER_WIDTH, self.width.to_bits());
    }
}

impl ViewModifier for CornerRadius {
    fn apply(&self, node: &mut Node) {
        node.properties.insert(property_id::BORDER_RADIUS, self.0.to_bits());
    }
}

impl ViewModifier for FontWeight {
    fn apply(&self, node: &mut Node) {
        node.properties.insert(property_id::FONT_WEIGHT, self.0.to_bits());
    }
}

impl ViewModifier for LineLimit {
    fn apply(&self, node: &mut Node) {
        node.properties.insert(property_id::LINE_LIMIT, self.0);
    }
}

impl ViewModifier for TextAlignment {
    fn apply(&self, node: &mut Node) {
        node.properties.insert(property_id::TEXT_ALIGNMENT, (self.0 as f32).to_bits());
    }
}

impl ViewModifier for TruncationMode {
    fn apply(&self, node: &mut Node) {
        node.properties.insert(property_id::TRUNCATION_MODE, (self.0 as f32).to_bits());
    }
}

impl ViewModifier for Offset {
    fn apply(&self, node: &mut Node) {
        node.properties.insert(property_id::OFFSET_X, self.x.to_bits());
        node.properties.insert(property_id::OFFSET_Y, self.y.to_bits());
    }
}

impl ViewModifier for Position {
    fn apply(&self, node: &mut Node) {
        node.properties.insert(property_id::POSITION_X, self.x.to_bits());
        node.properties.insert(property_id::POSITION_Y, self.y.to_bits());
    }
}

impl ViewModifier for ZIndex {
    fn apply(&self, node: &mut Node) {
        node.properties.insert(property_id::Z_INDEX, self.0.to_bits());
    }
}

// ---------------------------------------------------------------------------
// Core components
// ---------------------------------------------------------------------------

/// A vertical stack.
pub struct VStack {
    children: Vec<Box<dyn View>>,
}

impl VStack {
    /// Create an empty vertical stack.
    pub fn new() -> Self {
        Self { children: Vec::new() }
    }

    /// Set the children.
    pub fn children(mut self, children: Vec<Box<dyn View>>) -> Self {
        self.children = children;
        self
    }
}

impl core::fmt::Debug for VStack {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("VStack").field("children", &self.children.len()).finish()
    }
}

impl Default for VStack {
    fn default() -> Self {
        Self::new()
    }
}

impl View for VStack {
    fn build(&self) -> Node {
        Node {
            id: 0,
            component: Component::VStack,
            children: self.children.iter().map(|c| c.build()).collect(),
            properties: BTreeMap::new(),
        token_properties: BTreeMap::new(),
            text_binding: None,
            property_bindings: BTreeMap::new(),
            gestures: Vec::new(),
        }
    }
}

/// A horizontal stack.
pub struct HStack {
    children: Vec<Box<dyn View>>,
}

impl HStack {
    /// Create an empty horizontal stack.
    pub fn new() -> Self {
        Self { children: Vec::new() }
    }

    /// Set the children.
    pub fn children(mut self, children: Vec<Box<dyn View>>) -> Self {
        self.children = children;
        self
    }
}

impl core::fmt::Debug for HStack {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("HStack").field("children", &self.children.len()).finish()
    }
}

impl Default for HStack {
    fn default() -> Self {
        Self::new()
    }
}

impl View for HStack {
    fn build(&self) -> Node {
        Node {
            id: 0,
            component: Component::HStack,
            children: self.children.iter().map(|c| c.build()).collect(),
            properties: BTreeMap::new(),
        token_properties: BTreeMap::new(),
            text_binding: None,
            property_bindings: BTreeMap::new(),
            gestures: Vec::new(),
        }
    }
}

/// A text leaf.
#[derive(Debug, Clone, PartialEq)]
pub struct Text {
    text: String,
}

impl Text {
    /// Create a text node.
    pub fn new(text: &str) -> Self {
        Self { text: text.into() }
    }
}

impl View for Text {
    fn build(&self) -> Node {
        Node {
            id: 0,
            component: Component::Text {
                text: self.text.clone(),
            },
            children: Vec::new(),
            properties: BTreeMap::new(),
        token_properties: BTreeMap::new(),
            text_binding: None,
            property_bindings: BTreeMap::new(),
            gestures: Vec::new(),
        }
    }
}

/// A flexible spacer.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Spacer;

impl Spacer {
    /// Create a spacer.
    pub fn new() -> Self {
        Spacer
    }
}

impl Default for Spacer {
    fn default() -> Self {
        Spacer::new()
    }
}

impl View for Spacer {
    fn build(&self) -> Node {
        Node {
            id: 0,
            component: Component::Spacer,
            children: Vec::new(),
            properties: BTreeMap::new(),
        token_properties: BTreeMap::new(),
            text_binding: None,
            property_bindings: BTreeMap::new(),
            gestures: Vec::new(),
        }
    }
}

/// An interactive button.
#[derive(Debug, Clone, PartialEq)]
pub struct Button {
    label: String,
}

impl Button {
    /// Create a button with the given label.
    pub fn new(label: &str) -> Self {
        Self { label: label.into() }
    }
}

impl View for Button {
    fn build(&self) -> Node {
        Node {
            id: 0,
            component: Component::Button {
                label: self.label.clone(),
            },
            children: Vec::new(),
            properties: BTreeMap::new(),
        token_properties: BTreeMap::new(),
            text_binding: None,
            property_bindings: BTreeMap::new(),
            gestures: Vec::new(),
        }
    }
}

/// A button view (shorthand for [`Button::new`]).
pub fn button(label: &str) -> Button {
     Button::new(label)
}

// ---------------------------------------------------------------------------
// Extended components
// ---------------------------------------------------------------------------

/// Build a plain node (no text/gesture/bindings) for a component.
fn plain_node(component: Component, children: Vec<Node>, properties: BTreeMap<u16, u32>) -> Node {
    Node {
        id: 0,
        component,
        children,
        properties,
        token_properties: BTreeMap::new(),
        text_binding: None,
        property_bindings: BTreeMap::new(),
        gestures: Vec::new(),
    }
}

/// Apply a color-taking style property: a literal packs as the `COLOR` value
/// type; a `Color::token(..)` reference rides the `DESIGN_TOKEN` value type.
fn apply_color(node: &mut Node, prop: u16, color: Color) {
    match color {
        Color::Literal(v) => {
            node.properties.insert(prop, v);
        }
        Color::Token(path) => {
            node.token_properties.insert(prop, String::from(path));
        }
    }
}

/// An image view (`IMAGE_SOURCE` is a future STRING property).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Image;
impl View for Image {
    fn build(&self) -> Node {
        plain_node(Component::Image, Vec::new(), BTreeMap::new())
    }
}

/// A vector geometry (`SHAPE_KIND`).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Shape(pub u8);
impl View for Shape {
    fn build(&self) -> Node {
        let mut p = BTreeMap::new();
        p.insert(property_id::SHAPE_KIND, (self.0 as f32).to_bits());
        plain_node(Component::Shape, Vec::new(), p)
    }
}

/// A separator line.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Divider;
impl View for Divider {
    fn build(&self) -> Node {
        plain_node(Component::Divider, Vec::new(), BTreeMap::new())
    }
}

/// Determinate progress (fraction) or an activity indicator.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ProgressView(pub Option<f32>);
impl View for ProgressView {
    fn build(&self) -> Node {
        let mut p = BTreeMap::new();
        match self.0 {
            Some(v) => {
                p.insert(property_id::PROGRESS, v.to_bits());
            }
            None => {
                p.insert(property_id::IS_INDETERMINATE, 1);
            }
        }
        plain_node(Component::ProgressView, Vec::new(), p)
    }
}

/// A range meter.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Gauge {
    pub value: f32,
    pub min: f32,
    pub max: f32,
}
impl View for Gauge {
    fn build(&self) -> Node {
        let mut p = BTreeMap::new();
        p.insert(property_id::VALUE, self.value.to_bits());
        p.insert(property_id::MIN_VALUE, self.min.to_bits());
        p.insert(property_id::MAX_VALUE, self.max.to_bits());
        plain_node(Component::Gauge, Vec::new(), p)
    }
}

/// A boolean control with a `TOGGLE_STYLE` token (0=Switch, 1=Checkbox,
/// 2=Button).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Toggle(pub u8);
impl View for Toggle {
    fn build(&self) -> Node {
        let mut p = BTreeMap::new();
        p.insert(property_id::TOGGLE_STYLE, (self.0 as f32).to_bits());
        plain_node(Component::Toggle, Vec::new(), p)
    }
}

/// A numeric range control.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Slider {
    pub value: f32,
    pub min: f32,
    pub max: f32,
}
impl View for Slider {
    fn build(&self) -> Node {
        let mut p = BTreeMap::new();
        p.insert(property_id::VALUE, self.value.to_bits());
        p.insert(property_id::MIN_VALUE, self.min.to_bits());
        p.insert(property_id::MAX_VALUE, self.max.to_bits());
        plain_node(Component::Slider, Vec::new(), p)
    }
}

/// A single-line text input.
#[derive(Debug, Clone, PartialEq)]
pub struct TextField {
    pub placeholder: String,
}
impl View for TextField {
    fn build(&self) -> Node {
        let mut p = BTreeMap::new();
        p.insert(property_id::PROMPT, self.placeholder.len() as u32);
        plain_node(Component::TextField, Vec::new(), p)
    }
}

/// A multi-line text input.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct TextEditor;
impl View for TextEditor {
    fn build(&self) -> Node {
        plain_node(Component::TextEditor, Vec::new(), BTreeMap::new())
    }
}

/// An increment/decrement control.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Stepper {
    pub value: f32,
    pub min: f32,
    pub max: f32,
}
impl View for Stepper {
    fn build(&self) -> Node {
        let mut p = BTreeMap::new();
        p.insert(property_id::VALUE, self.value.to_bits());
        p.insert(property_id::MIN_VALUE, self.min.to_bits());
        p.insert(property_id::MAX_VALUE, self.max.to_bits());
        plain_node(Component::Stepper, Vec::new(), p)
    }
}

/// A date & time picker.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct DatePicker;
impl View for DatePicker {
    fn build(&self) -> Node {
        plain_node(Component::DatePicker, Vec::new(), BTreeMap::new())
    }
}

/// A selection control (options are children).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Picker;
impl View for Picker {
    fn build(&self) -> Node {
        plain_node(Component::Picker, Vec::new(), BTreeMap::new())
    }
}

/// A contextual action trigger + popover (action items are children).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Menu;
impl View for Menu {
    fn build(&self) -> Node {
        plain_node(Component::Menu, Vec::new(), BTreeMap::new())
    }
}

/// A native color picker.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ColorPicker(pub u32);
impl View for ColorPicker {
    fn build(&self) -> Node {
        let mut p = BTreeMap::new();
        p.insert(property_id::COLOR_VALUE, self.0);
        plain_node(Component::ColorPicker, Vec::new(), p)
    }
}

/// An overlapping stack.
#[derive(Default)]
pub struct ZStack {
    children: Vec<Box<dyn View>>,
}

impl core::fmt::Debug for ZStack {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("ZStack").field("children", &self.children.len()).finish()
    }
}
impl ZStack {
    pub fn new() -> Self {
        Self { children: Vec::new() }
    }
    pub fn children(mut self, children: Vec<Box<dyn View>>) -> Self {
        self.children = children;
        self
    }
}
impl View for ZStack {
    fn build(&self) -> Node {
        let children = self.children.iter().map(|c| c.build()).collect();
        plain_node(Component::ZStack, children, BTreeMap::new())
    }
}

/// A static grid (cells are children).
#[derive(Default)]
pub struct Grid {
    children: Vec<Box<dyn View>>,
}

impl core::fmt::Debug for Grid {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("Grid").field("children", &self.children.len()).finish()
    }
}
impl Grid {
    pub fn new() -> Self {
        Self { children: Vec::new() }
    }
    pub fn children(mut self, children: Vec<Box<dyn View>>) -> Self {
        self.children = children;
        self
    }
}
impl View for Grid {
    fn build(&self) -> Node {
        let children = self.children.iter().map(|c| c.build()).collect();
        plain_node(Component::Grid, children, BTreeMap::new())
    }
}

/// A scrollable container (children are content).
#[derive(Default)]
pub struct ScrollView {
    children: Vec<Box<dyn View>>,
}

impl core::fmt::Debug for ScrollView {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("ScrollView").field("children", &self.children.len()).finish()
    }
}
impl ScrollView {
    pub fn new() -> Self {
        Self { children: Vec::new() }
    }
    pub fn children(mut self, children: Vec<Box<dyn View>>) -> Self {
        self.children = children;
        self
    }
}
impl View for ScrollView {
    fn build(&self) -> Node {
        let children = self.children.iter().map(|c| c.build()).collect();
        plain_node(Component::ScrollView, children, BTreeMap::new())
    }
}

/// A virtualized vertical grid.
#[derive(Default)]
pub struct LazyVGrid {
    children: Vec<Box<dyn View>>,
}

impl core::fmt::Debug for LazyVGrid {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("LazyVGrid").field("children", &self.children.len()).finish()
    }
}
impl LazyVGrid {
    pub fn new() -> Self {
        Self { children: Vec::new() }
    }
    pub fn children(mut self, children: Vec<Box<dyn View>>) -> Self {
        self.children = children;
        self
    }
}
impl View for LazyVGrid {
    fn build(&self) -> Node {
        let children = self.children.iter().map(|c| c.build()).collect();
        plain_node(Component::LazyVGrid, children, BTreeMap::new())
    }
}

/// A virtualized horizontal grid.
#[derive(Default)]
pub struct LazyHGrid {
    children: Vec<Box<dyn View>>,
}

impl core::fmt::Debug for LazyHGrid {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("LazyHGrid").field("children", &self.children.len()).finish()
    }
}
impl LazyHGrid {
    pub fn new() -> Self {
        Self { children: Vec::new() }
    }
    pub fn children(mut self, children: Vec<Box<dyn View>>) -> Self {
        self.children = children;
        self
    }
}
impl View for LazyHGrid {
    fn build(&self) -> Node {
        let children = self.children.iter().map(|c| c.build()).collect();
        plain_node(Component::LazyHGrid, children, BTreeMap::new())
    }
}

/// A virtualized vertical stack.
#[derive(Default)]
pub struct LazyVStack {
    children: Vec<Box<dyn View>>,
}

impl core::fmt::Debug for LazyVStack {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("LazyVStack").field("children", &self.children.len()).finish()
    }
}
impl LazyVStack {
    pub fn new() -> Self {
        Self { children: Vec::new() }
    }
    pub fn children(mut self, children: Vec<Box<dyn View>>) -> Self {
        self.children = children;
        self
    }
}
impl View for LazyVStack {
    fn build(&self) -> Node {
        let children = self.children.iter().map(|c| c.build()).collect();
        plain_node(Component::LazyVStack, children, BTreeMap::new())
    }
}

/// A virtualized horizontal stack.
#[derive(Default)]
pub struct LazyHStack {
    children: Vec<Box<dyn View>>,
}

impl core::fmt::Debug for LazyHStack {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("LazyHStack").field("children", &self.children.len()).finish()
    }
}
impl LazyHStack {
    pub fn new() -> Self {
        Self { children: Vec::new() }
    }
    pub fn children(mut self, children: Vec<Box<dyn View>>) -> Self {
        self.children = children;
        self
    }
}
impl View for LazyHStack {
    fn build(&self) -> Node {
        let children = self.children.iter().map(|c| c.build()).collect();
        plain_node(Component::LazyHStack, children, BTreeMap::new())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn text_builds_plain_node() {
        let node = Text::new("ab").build();
        assert_eq!(node.id, 0);
        assert_eq!(node.component, Component::Text { text: "ab".into() });
        assert!(node.properties.is_empty());
        assert!(node.children.is_empty());
    }

    #[test]
    fn modifiers_chain_and_apply_to_node() {
        let node = Text::new("hi")
            .padding(16.0)
            .foreground_style(Color::argb(0xFF_0000FF))
            .background(Color::argb(0xFF_EEEEEE))
            .build();
        assert_eq!(
            node.properties.get(&property_id::PADDING),
            Some(&16.0f32.to_bits())
        );
        assert_eq!(
            node.properties.get(&property_id::COLOR),
            Some(&0xFF_0000FF)
        );
        assert_eq!(
            node.properties.get(&property_id::BACKGROUND_COLOR),
            Some(&0xFF_EEEEEE)
        );
    }

    #[test]
    fn color_token_refs_ride_design_token_properties() {
        let node = Text::new("hi")
            .foreground_style(Color::token("color.primary"))
            .background(Color::token("dark.color.surface"))
            .border(Color::token("color.border"), 1.0)
            .build();
        assert!(node.properties.get(&property_id::COLOR).is_none(), "no literal");
        assert_eq!(
            node.token_properties.get(&property_id::COLOR).map(String::as_str),
            Some("color.primary")
        );
        assert_eq!(
            node.token_properties
                .get(&property_id::BACKGROUND_COLOR)
                .map(String::as_str),
            Some("dark.color.surface")
        );
        assert_eq!(
            node.token_properties.get(&property_id::BORDER_COLOR).map(String::as_str),
            Some("color.border")
        );
        assert_eq!(
            node.properties.get(&property_id::BORDER_WIDTH),
            Some(&1.0f32.to_bits())
        );
    }

    #[test]
    fn color_view_with_token_builds_token_property() {
        let node = Color::token("color.primary").build();
        assert_eq!(node.component, Component::Color);
        assert_eq!(
            node.token_properties.get(&property_id::COLOR).map(String::as_str),
            Some("color.primary")
        );
    }

    #[test]
    fn vstack_children_build_to_node_children() {
        let node = vstack![text("a"), text("b")]
            .spacing(4.0)
            .padding(8.0)
            .build();
        assert_eq!(node.component, Component::VStack);
        assert_eq!(node.children.len(), 2);
        assert_eq!(
            node.properties.get(&property_id::SPACING),
            Some(&4.0f32.to_bits())
        );
        assert_eq!(
            node.properties.get(&property_id::PADDING),
            Some(&8.0f32.to_bits())
        );
    }

    #[test]
    fn nested_stacks_build_via_macros() {
        let node = vstack![text("a"), hstack![text("b")]].build();
        assert_eq!(node.component, Component::VStack);
        assert_eq!(node.children.len(), 2);
        assert_eq!(node.children[0].component, Component::Text { text: "a".into() });
        assert_eq!(node.children[1].component, Component::HStack);
        assert_eq!(node.children[1].children.len(), 1);
    }

    #[test]
    fn macros_accept_empty_and_trailing_comma() {
        let empty = vstack![].build();
        assert!(empty.children.is_empty());
        let trailing = hstack![text("x"),].build();
        assert_eq!(trailing.children.len(), 1);
    }

    #[test]
    fn any_modifier_applies_to_any_view() {
        // .font_size on a stack is "allowed and ignored" — property is emitted.
        let stack = vstack![].font_size(24.0).build();
        assert_eq!(
            stack.properties.get(&property_id::FONT_SIZE),
            Some(&24.0f32.to_bits())
        );
    }

    #[test]
    fn custom_modifier_composes_core_modifiers() {
        struct Card;
        impl ViewModifier for Card {
            fn apply(&self, node: &mut Node) {
                Padding(16.0).apply(node);
                Background(Color::argb(0xFF_EEEEEE)).apply(node);
                ForegroundStyle(Color::argb(0xFF_000000)).apply(node);
            }
        }

        let text = text("A").modifier(Card).build();
        assert_eq!(
            text.properties.get(&property_id::PADDING),
            Some(&16.0f32.to_bits())
        );
        assert_eq!(
            text.properties.get(&property_id::BACKGROUND_COLOR),
            Some(&0xFF_EEEEEE)
        );

        let stack = vstack![].modifier(Card).build();
        assert_eq!(
            stack.properties.get(&property_id::BACKGROUND_COLOR),
            Some(&0xFF_EEEEEE)
        );
    }

    #[test]
    fn modifiers_apply_innermost_first() {
        let node = Text::new("x").padding(4.0).padding(8.0).build();
        // Innermost wins: first Padding(4), then Padding(8) overwrites.
        assert_eq!(
            node.properties.get(&property_id::PADDING),
            Some(&8.0f32.to_bits())
        );
    }

    #[test]
    fn assign_ids_is_pre_order_sequential() {
        let mut root = vstack![text("a"), hstack![text("b")]].build();
        assign_ids(&mut root, &mut 1);
        assert_eq!(root.id, 1);
        assert_eq!(root.children[0].id, 2);
        assert_eq!(root.children[1].id, 3);
        assert_eq!(root.children[1].children[0].id, 4);
    }

    #[test]
    fn frame_emits_width_height_alignment_as_properties() {
        use pathland_core::size;
        let node = text("x")
            .frame(Some(size::FILL), Some(24.0), Some(Align::Center))
            .build();
        assert_eq!(
            node.properties.get(&property_id::WIDTH),
            Some(&size::FILL.to_bits())
        );
        assert_eq!(
            node.properties.get(&property_id::HEIGHT),
            Some(&24.0f32.to_bits())
        );
        assert_eq!(
            node.properties.get(&property_id::ALIGNMENT),
            Some(&(Align::Center.value() as f32).to_bits())
        );
    }

    #[test]
    fn frame_without_axis_omits_that_property() {
        let node = text("x").frame(Some(100.0), None, None).build();
        assert_eq!(
            node.properties.get(&property_id::WIDTH),
            Some(&100.0f32.to_bits())
        );
        assert!(!node.properties.contains_key(&property_id::HEIGHT));
        assert!(!node.properties.contains_key(&property_id::ALIGNMENT));
    }

    #[test]
    fn frame_infinite_hint_normalizes_to_fill() {
        use pathland_core::size;
        // SwiftUI `frame(maxWidth: .infinity, maxHeight: .infinity)`.
        let node = text("x")
            .frame(Some(f32::INFINITY), Some(f32::INFINITY), Some(Align::Leading))
            .build();
        assert_eq!(
            node.properties.get(&property_id::WIDTH),
            Some(&size::FILL.to_bits())
        );
        assert_eq!(
            node.properties.get(&property_id::HEIGHT),
            Some(&size::FILL.to_bits())
        );
        assert_eq!(
            node.properties.get(&property_id::ALIGNMENT),
            Some(&(Align::Leading.value() as f32).to_bits())
        );
        // Negative infinity behaves the same; finite values are untouched.
        let node = text("x")
            .frame(Some(f32::NEG_INFINITY), Some(24.0), None)
            .build();
        assert_eq!(
            node.properties.get(&property_id::WIDTH),
            Some(&size::FILL.to_bits())
        );
        assert_eq!(
            node.properties.get(&property_id::HEIGHT),
            Some(&24.0f32.to_bits())
        );
    }

    #[test]
    fn pointer_events_modifier_sets_listener_mask() {
        let mask = pathland_core::listener::POINTER_DOWN | pathland_core::listener::POINTER_UP;
        let node = text("x").pointer_events(mask).build();
        assert_eq!(
            node.properties.get(&property_id::EVENT_LISTENERS),
            Some(&mask)
        );
    }

    #[test]
    fn on_tap_gesture_declares_listeners_and_stores_callback() {
        let node = text("x").on_tap_gesture(|| {}).build();
        assert_eq!(
            node.properties.get(&property_id::EVENT_LISTENERS),
            Some(&(pathland_core::listener::POINTER_DOWN | pathland_core::listener::POINTER_UP))
        );
        assert_eq!(node.gestures.len(), 1);
    }

    #[test]
    fn on_tap_gesture_works_on_any_view() {
        assert_eq!(vstack![].on_tap_gesture(|| {}).build().gestures.len(), 1);
        assert_eq!(text("a").on_tap_gesture(|| {}).build().gestures.len(), 1);
        assert_eq!(hstack![].on_tap_gesture(|| {}).build().gestures.len(), 1);
    }

    #[test]
    fn tap_gesture_ors_into_existing_listener_mask() {
        let node = text("x")
            .pointer_events(pathland_core::listener::POINTER_MOVE)
            .on_tap_gesture(|| {})
            .build();
        assert_eq!(
            node.properties.get(&property_id::EVENT_LISTENERS),
            Some(&(pathland_core::listener::POINTER_MOVE
                | pathland_core::listener::POINTER_DOWN
                | pathland_core::listener::POINTER_UP))
        );
    }

    #[test]
    fn collect_tap_handlers_maps_ids_to_callbacks() {
        let mut root = vstack![text("a").on_tap_gesture(|| {}), text("b")].build();
        assign_ids(&mut root, &mut 1);
        let mut map = BTreeMap::new();
        collect_tap_handlers(&root, &mut map);
        // root=1, "a"=2 (has tap), "b"=3 (no tap).
        assert!(map.contains_key(&2));
        assert!(!map.contains_key(&1));
        assert!(!map.contains_key(&3));
    }
}
