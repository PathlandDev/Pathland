//! # pathland-view
//!
//! The Pathland SwiftUI-style view DSL. Core components (`VStack`, `HStack`,
//! `Text`) and decoupled modifiers build a retained **view tree**
//! (`pathland_engine::Node`) that the diff emitter in `pathland-core` turns into
//! declarative `TREE`/`PARAMETER` opcodes.
//!
//! ## The three operations
//!
//! Every concrete view exposes the same three operations, usable at creation or
//! chained, in **any order** (spec/DSL.md §2):
//!
//! - **values** — `Type::with(|c| …)` (static) / `view.with(|c| …)` (chained),
//!   where `c` is the view's fluent [`Configurable::Config`].
//! - **modifiers** — `Type::modifiers((A, B, …))` / `view.modifiers((A, B, …))`,
//!   where the modifiers are passed as a **tuple** (or a single modifier).
//! - **children** — `Type::children(vec![…])` / `view.children(vec![…])` on
//!   every content-bearing view (and the `vstack!`/`hstack!` macros).
//!
//! ```
//! use pathland_view::{vstack, text, View, ViewExt, Children, Padding, FontSize, Align};
//!
//! let node = VStack::with(|v| { v.spacing(8.0).alignment(Align::Center); })
//!     .children(vec![Box::new(text("a")), Box::new(text("b"))])
//!     .modifiers((Padding(16.0), FontSize(12.0)))
//!     .build();
//! # let _ = node;
//! # use pathland_view::VStack;
//! ```
//!
//! ## Decoupled modifiers
//!
//! Modifiers are **decoupled from views** (SwiftUI-style): any modifier applies
//! to any view through `modifiers(...)`, and application code composes
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
//! let a = pathland_view::text("A").modifiers(Card);
//! let b = pathland_view::vstack![].modifiers(Card);
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
    assign_ids, collect_input_handlers, collect_tap_handlers, component_type_id, AdaptiveTheme,
    Component, Engine, Gesture, InputHandlers, IntoSignalId, Node, Signal, SignalId, SignalValue,
    SignalValueKind, Theme, WritableSignal,
};

mod recognizer;

pub use recognizer::TapRecognizer;

pub mod state;

// ---------------------------------------------------------------------------
// Protocol traits
// ---------------------------------------------------------------------------

/// A composable view. Implement for custom views; the core components and
/// [`Modified`] wrappers implement it already.
pub trait View {
    /// Build the retained node for this view under the ambient [`Environment`].
    ///
    /// The environment carries subtree-scoped values (the style for a button,
    /// …). Java implements this scope with a thread-local; `no_std` Rust threads
    /// it explicitly through this method.
    fn build_env(&self, env: &mut Environment) -> Node;

    /// Build the retained node with a fresh, empty environment.
    fn build(&self) -> Node {
        self.build_env(&mut Environment::default())
    }
}

/// A view decorator (SwiftUI `ViewModifier`): transforms a node's properties.
///
/// Core modifiers (`Padding`, `FontSize`, `ForegroundStyle`, `Background`, …)
/// implement this; application code implements it to compose custom modifiers.
pub trait ViewModifier {
    /// Apply the modifier to `node` (mutating constraint properties).
    fn apply(&self, node: &mut Node);
}

/// A list of modifiers passed to [`ViewExt::modifiers`]. Implemented for a
/// single modifier and for tuples of modifiers (up to 12), so call sites read
/// `.modifiers(Padding(16.0))` or `.modifiers((Padding(16.0), FontSize(12.0)))`.
pub trait ModifierList {
    /// Box the modifiers into a uniform list (applied innermost-first).
    fn into_modifiers(self) -> Vec<Box<dyn ViewModifier>>;
}

impl<M: ViewModifier + 'static> ModifierList for M {
    fn into_modifiers(self) -> Vec<Box<dyn ViewModifier>> {
        vec![Box::new(self)]
    }
}

macro_rules! impl_modifier_list {
    ($($name:ident),+) => {
        impl<$($name: ViewModifier + 'static),+> ModifierList for ($($name,)+) {
            #[allow(non_snake_case, clippy::vec_init_then_push)]
            fn into_modifiers(self) -> Vec<Box<dyn ViewModifier>> {
                let ($($name,)+) = self;
                let mut list: Vec<Box<dyn ViewModifier>> = Vec::new();
                $( list.push(Box::new($name)); )+
                list
            }
        }
    };
}

impl_modifier_list!(M0);
impl_modifier_list!(M0, M1);
impl_modifier_list!(M0, M1, M2);
impl_modifier_list!(M0, M1, M2, M3);
impl_modifier_list!(M0, M1, M2, M3, M4);
impl_modifier_list!(M0, M1, M2, M3, M4, M5);
impl_modifier_list!(M0, M1, M2, M3, M4, M5, M6);
impl_modifier_list!(M0, M1, M2, M3, M4, M5, M6, M7);
impl_modifier_list!(M0, M1, M2, M3, M4, M5, M6, M7, M8);
impl_modifier_list!(M0, M1, M2, M3, M4, M5, M6, M7, M8, M9);
impl_modifier_list!(M0, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10);
impl_modifier_list!(M0, M1, M2, M3, M4, M5, M6, M7, M8, M9, M10, M11);

/// A view whose **values** (structural/layout properties, a control's bound
/// value) are configured through a fluent [`Config`](Configurable::Config).
///
/// `view.with(|c| …)` applies the configurator to an existing view; the static
/// entry `Type::with(|c| …)` (generated per component) creates one.
pub trait Configurable: View + Sized {
    /// This view's config type.
    type Config: Default;
    /// The current config.
    fn config(&self) -> &Self::Config;
    /// The mutable config (the `with` closure receives this).
    fn config_mut(&mut self) -> &mut Self::Config;

    /// Configure this view's values (`with(...)`).
    ///
    /// The closure receives the mutable config; fluent setters return
    /// `&mut Config` and are discarded, so the common form is
    /// `.with(|c| { c.spacing(8.0); })`.
    fn with(mut self, configure: impl FnOnce(&mut Self::Config)) -> Self {
        configure(self.config_mut());
        self
    }
}

/// A **content-bearing** view: a container (stack/grid/scroll) or a control
/// whose content is supplied as children.
pub trait Children: View + Sized {
    /// Replace this view's children.
    fn set_children(&mut self, children: Vec<Box<dyn View>>);

    /// Supply this view's content ({@code children(...)}).
    fn children(mut self, children: Vec<Box<dyn View>>) -> Self {
        self.set_children(children);
        self
    }
}

/// Blanket authoring extensions on any [`View`].
pub trait ViewExt: View + Sized {
    /// Apply one or more modifiers (innermost-first). The list is a single
    /// modifier or a tuple: `.modifiers(Padding(16.0))`,
    /// `.modifiers((Padding(16.0), FontSize(12.0)))`.
    fn modifiers<L: ModifierList>(self, modifiers: L) -> Modified<Self> {
        Modified {
            view: self,
            mods: modifiers.into_modifiers(),
        }
    }

    /// Scope a [`ButtonStyle`] down this subtree (the active style for any
    /// `Button` whose own content it supplies).
    fn button_style(self, style: impl ButtonStyle + 'static) -> WithButtonStyle<Self> {
        WithButtonStyle {
            view: self,
            style: Rc::new(style),
        }
    }
}

impl<T: View> ViewExt for T {}

// ---------------------------------------------------------------------------
// Environment + styles
// ---------------------------------------------------------------------------

/// The subtree-scoped environment — the one inheritance mechanism.
///
/// Java implements this scope with a thread-local over the synchronous render
/// pass; `no_std` Rust threads it explicitly through [`View::build_env`]. A
/// style-setting wrapper saves/restores the slot around building its subtree.
#[derive(Default)]
pub struct Environment {
    button_style: Option<Rc<dyn ButtonStyle>>,
}

impl Environment {
    /// An empty environment.
    pub fn new() -> Self {
        Self::default()
    }

    /// The active button style (defaults to [`PlainButtonStyle`]).
    pub fn button_style(&self) -> Rc<dyn ButtonStyle> {
        self.button_style
            .clone()
            .unwrap_or_else(|| Rc::new(PlainButtonStyle))
    }
}

/// A button style: supplies a button's **content** (the control owns the native
/// component and the interaction). `None` = the control's native content.
pub trait ButtonStyle {
    /// Build the styled content for `config`, or `None` for the native path.
    fn make_body(&self, config: &ButtonConfig) -> Option<Box<dyn View>>;
}

/// The default style: the button's own label (native path, no content view).
pub struct PlainButtonStyle;

impl ButtonStyle for PlainButtonStyle {
    fn make_body(&self, _config: &ButtonConfig) -> Option<Box<dyn View>> {
        None
    }
}

/// A bordered button style: the label with padding + a border.
pub struct BorderedButtonStyle;

impl ButtonStyle for BorderedButtonStyle {
    fn make_body(&self, config: &ButtonConfig) -> Option<Box<dyn View>> {
        let label = Text::with(|t| {
            t.text(config.label.clone());
        })
        .modifiers((
            Padding(8.0),
            Border::new(Color::argb(0xFF_888888), 1.0),
        ));
        Some(Box::new(label))
    }
}

/// A view wrapped by a scoped [`ButtonStyle`] (applied via
/// [`ViewExt::button_style`]).
pub struct WithButtonStyle<V> {
    view: V,
    style: Rc<dyn ButtonStyle>,
}

impl<V: View> View for WithButtonStyle<V> {
    fn build_env(&self, env: &mut Environment) -> Node {
        let previous = env.button_style.take();
        env.button_style = Some(self.style.clone());
        let node = self.view.build_env(env);
        env.button_style = previous;
        node
    }
}

impl<V: Configurable> Configurable for WithButtonStyle<V> {
    type Config = V::Config;
    fn config(&self) -> &Self::Config {
        self.view.config()
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        self.view.config_mut()
    }
}

/// A view wrapped by a list of modifiers (applied innermost-first at `build`).
pub struct Modified<V> {
    view: V,
    mods: Vec<Box<dyn ViewModifier>>,
}

impl<V: View> View for Modified<V> {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = self.view.build_env(_env);
        for modifier in &self.mods {
            modifier.apply(&mut node);
        }
        node
    }
}

impl<V: core::fmt::Debug> core::fmt::Debug for Modified<V> {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("Modified")
            .field("view", &self.view)
            .field("mods", &self.mods.len())
            .finish()
    }
}

impl<V: Configurable> Configurable for Modified<V> {
    type Config = V::Config;
    fn config(&self) -> &Self::Config {
        self.view.config()
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        self.view.config_mut()
    }
}

impl<V: Children> Children for Modified<V> {
    fn set_children(&mut self, children: Vec<Box<dyn View>>) {
        self.view.set_children(children);
    }
}

/// Generate the static `with` / `modifiers` entries for a component. Requires
/// `Default + Configurable`.
macro_rules! view_statics {
    ($t:ty) => {
        impl $t {
            /// Create a default view and configure its values.
            pub fn with(configure: impl FnOnce(&mut <$t as Configurable>::Config)) -> Self {
                let mut view = <$t>::default();
                configure(view.config_mut());
                view
            }

            /// Create a default view, then apply modifiers.
            pub fn modifiers<L: ModifierList>(modifiers: L) -> Modified<Self> {
                ViewExt::modifiers(<$t>::default(), modifiers)
            }
        }
    };
}

/// Generate the static `children` entry for a content-bearing component.
/// Requires `Default + Children`.
macro_rules! children_static {
    ($t:ty) => {
        impl $t {
            /// Create a default view, then supply its children.
            pub fn children(children: Vec<Box<dyn View>>) -> Self {
                let mut view = <$t>::default();
                view.set_children(children);
                view
            }
        }
    };
}

/// A `Debug` impl for a container that prints its child count (children are
/// `Box<dyn View>` and are not `Debug`/`Clone`).
macro_rules! container_debug {
    ($t:ty) => {
        impl core::fmt::Debug for $t {
            fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
                f.debug_struct(stringify!($t))
                    .field("children", &self.children.len())
                    .finish()
            }
        }
    };
}

// ---------------------------------------------------------------------------
// Shared enums
// ---------------------------------------------------------------------------

/// Cross-axis alignment values for stacks and the `Frame` compound modifier.
///
/// These are emitted as the `ALIGNMENT` (0x0002) property with an `ENUM` value
/// type (low byte of the property value). Alignment is **position-only**
/// (codes 0–2; the protocol's 2D codes 3–8 are the other positions, see
/// spec/PRIMITIVES.md §ZStack): stretching is a child's `FILL` size, never an
/// alignment.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum Align {
    /// Align to the leading (start) edge.
    #[default]
    Leading,
    /// Center along the cross axis.
    Center,
    /// Align to the trailing (end) edge.
    Trailing,
}

impl Align {
    /// The protocol enum value for this alignment.
    pub fn value(self) -> u8 {
        match self {
            Align::Leading => 0,
            Align::Center => 1,
            Align::Trailing => 2,
        }
    }
}

/// Shape geometry for a `SHAPE` node (the `SHAPE_KIND` enum).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum ShapeKind {
    Circle = 0,
    #[default]
    Rectangle = 1,
    RoundedRectangle = 2,
    Capsule = 3,
    Ellipse = 4,
    Path = 5,
}

impl ShapeKind {
    /// The protocol enum value for this shape.
    pub fn value(self) -> u8 {
        self as u8
    }
}

// ---------------------------------------------------------------------------
// Modifiers
// ---------------------------------------------------------------------------

/// A config value that is either a **static** value or a **signal binding**
/// (spec DSL.md §2): a static value emits as a plain property; a signal becomes a
/// node-level binding that re-emits only that property when it changes. This is
/// the Rust realization of "every value member accepts a raw value or a signal".
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Reactive<T> {
    /// A raw, non-reactive value.
    Static(T),
    /// A property bound to a signal.
    Signal(SignalId),
}

/// Convert a raw value or a signal into a [`Reactive`] config value. Implemented
/// for `T` (static) and for `Signal<T>`/`WritableSignal<T>` (binding); the former
/// `Bound` modifier folds into this.
pub trait IntoReactive<T> {
    /// Convert into a [`Reactive`].
    fn into_reactive(self) -> Reactive<T>;
}

impl<T> IntoReactive<T> for T {
    fn into_reactive(self) -> Reactive<T> {
        Reactive::Static(self)
    }
}

impl<T> IntoReactive<T> for Signal<T> {
    fn into_reactive(self) -> Reactive<T> {
        Reactive::Signal(self.id())
    }
}

impl<T> IntoReactive<T> for WritableSignal<T> {
    fn into_reactive(self) -> Reactive<T> {
        Reactive::Signal(self.id())
    }
}

/// Apply a reactive f32 property (static value or signal binding).
fn reactive_f32(node: &mut Node, prop: u16, r: &Reactive<f32>) {
    match r {
        Reactive::Static(v) => {
            node.properties.insert(prop, v.to_bits());
        }
        Reactive::Signal(id) => {
            node.property_bindings.insert(prop, *id);
        }
    }
}

/// Apply a reactive enum property (a `u8` code stored as `F32`).
fn reactive_enum(node: &mut Node, prop: u16, r: &Reactive<u8>) {
    match r {
        Reactive::Static(v) => {
            node.properties.insert(prop, (*v as f32).to_bits());
        }
        Reactive::Signal(id) => {
            node.property_bindings.insert(prop, *id);
        }
    }
}

/// Apply a reactive u32 property.
fn reactive_u32(node: &mut Node, prop: u16, r: &Reactive<u32>) {
    match r {
        Reactive::Static(v) => {
            node.properties.insert(prop, *v);
        }
        Reactive::Signal(id) => {
            node.property_bindings.insert(prop, *id);
        }
    }
}

/// Apply a reactive color property (literal/token static, or signal binding).
fn reactive_color(node: &mut Node, prop: u16, r: &Reactive<Color>) {
    match r {
        Reactive::Static(c) => apply_color(node, prop, *c),
        Reactive::Signal(id) => {
            node.property_bindings.insert(prop, *id);
        }
    }
}

/// Uniform padding (a styling modifier, applied to any view). `Padding(16.0)` is
/// static; `Padding(signal)` binds `PADDING` to a signal.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Padding {
    value: Reactive<f32>,
}

/// A uniform padding value (raw or signal).
#[allow(non_snake_case)]
pub fn Padding(value: impl IntoReactive<f32>) -> Padding {
    Padding {
        value: value.into_reactive(),
    }
}

/// Text font size in points. `FontSize(28.0)` static, `FontSize(signal)` bound.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct FontSize {
    value: Reactive<f32>,
}

/// A font size value (raw or signal).
#[allow(non_snake_case)]
pub fn FontSize(value: impl IntoReactive<f32>) -> FontSize {
    FontSize {
        value: value.into_reactive(),
    }
}

/// Predefined typography from the design system (SwiftUI `Font.TextStyle`),
/// carried by the `TEXT_STYLE` property. The renderer owns the concrete
/// size/weight (spec/MODIFIERS.md §2). A heading style (`LargeTitle`…
/// `Headline`) implies a heading element; the rest render as plain text.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum TextStyle {
    LargeTitle = 0,
    Title = 1,
    Title2 = 2,
    Title3 = 3,
    Headline = 4,
    Subheadline = 5,
    Body = 6,
    Callout = 7,
    Footnote = 8,
    Caption = 9,
    Caption2 = 10,
}

impl TextStyle {
    /// The protocol enum code.
    pub const fn code(self) -> u8 {
        self as u8
    }
}

/// A font design axis (SwiftUI `Font.Design`), carried by `FONT_DESIGN`. The
/// value is itself a [`ViewModifier`] (`.modifiers(FontDesign::Serif)`).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum FontDesign {
    Default = 0,
    Serif = 1,
    Rounded = 2,
    Monospaced = 3,
}

impl FontDesign {
    /// The protocol enum code.
    pub const fn code(self) -> u8 {
        self as u8
    }
}

impl ViewModifier for FontDesign {
    fn apply(&self, node: &mut Node) {
        node.properties
            .insert(property_id::FONT_DESIGN, (self.code() as f32).to_bits());
    }
}

/// A font specification (SwiftUI `Font`): a predefined typography, a custom
/// family + size, or a system size/weight/design. The value is itself a
/// [`ViewModifier`] (`.modifiers(Font::headline())`); raw modifiers
/// (`FontSize`, `FontWeight`, `FontFamily`, `FontDesign`) layer on top.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct Font {
    style: Option<TextStyle>,
    family: Option<&'static str>,
    size: Option<f32>,
    weight: Option<f32>,
    design: Option<FontDesign>,
}

impl Font {
    const fn styled(style: TextStyle) -> Self {
        Self {
            style: Some(style),
            family: None,
            size: None,
            weight: None,
            design: None,
        }
    }

    pub const fn large_title() -> Self {
        Self::styled(TextStyle::LargeTitle)
    }
    pub const fn title() -> Self {
        Self::styled(TextStyle::Title)
    }
    pub const fn title2() -> Self {
        Self::styled(TextStyle::Title2)
    }
    pub const fn title3() -> Self {
        Self::styled(TextStyle::Title3)
    }
    pub const fn headline() -> Self {
        Self::styled(TextStyle::Headline)
    }
    pub const fn subheadline() -> Self {
        Self::styled(TextStyle::Subheadline)
    }
    pub const fn body() -> Self {
        Self::styled(TextStyle::Body)
    }
    pub const fn callout() -> Self {
        Self::styled(TextStyle::Callout)
    }
    pub const fn footnote() -> Self {
        Self::styled(TextStyle::Footnote)
    }
    pub const fn caption() -> Self {
        Self::styled(TextStyle::Caption)
    }
    pub const fn caption2() -> Self {
        Self::styled(TextStyle::Caption2)
    }

    /// A custom font family + size (SwiftUI `.font(.custom(name:size:))`).
    pub const fn custom(family: &'static str, size: f32) -> Self {
        Self {
            style: None,
            family: Some(family),
            size: Some(size),
            weight: None,
            design: None,
        }
    }

    /// A system font with an exact size.
    pub const fn system(size: f32) -> Self {
        Self {
            style: None,
            family: None,
            size: Some(size),
            weight: None,
            design: None,
        }
    }

    /// A system font with an exact size + weight.
    pub const fn system_weight(size: f32, weight: f32) -> Self {
        Self {
            style: None,
            family: None,
            size: Some(size),
            weight: Some(weight),
            design: None,
        }
    }

    /// A fully-custom typography: family + size + weight + design.
    pub const fn custom_full(
        family: &'static str,
        size: f32,
        weight: f32,
        design: FontDesign,
    ) -> Self {
        Self {
            style: None,
            family: Some(family),
            size: Some(size),
            weight: Some(weight),
            design: Some(design),
        }
    }
}

impl ViewModifier for Font {
    fn apply(&self, node: &mut Node) {
        if let Some(style) = self.style {
            node.properties
                .insert(property_id::TEXT_STYLE, (style.code() as f32).to_bits());
        }
        if let Some(family) = self.family {
            node.string_properties
                .insert(property_id::FONT_FAMILY, String::from(family));
        }
        if let Some(size) = self.size {
            node.properties
                .insert(property_id::FONT_SIZE, size.to_bits());
        }
        if let Some(weight) = self.weight {
            node.properties
                .insert(property_id::FONT_WEIGHT, weight.to_bits());
        }
        if let Some(design) = self.design {
            node.properties
                .insert(property_id::FONT_DESIGN, (design.code() as f32).to_bits());
        }
    }
}

/// A custom font family (`FONT_FAMILY`, a `STRING` property).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct FontFamily(pub &'static str);

impl ViewModifier for FontFamily {
    fn apply(&self, node: &mut Node) {
        node.string_properties
            .insert(property_id::FONT_FAMILY, String::from(self.0));
    }
}

/// An sRGB color or a **design-token reference**, with dual identity mirroring
/// SwiftUI:
///
/// - **View** — placing a `Color` in a layout tree draws a solid-color fill
///   that is **layout-greedy** (expands to the available space unless a
///   `.frame`/size modifier constrains it).
/// - **Data type** — a `Color` value is passed into style-taking modifiers
///   (`ForegroundStyle`, `Background`, `Border`, `Tint`).
///
/// A literal packs sRGB `0xAARRGGBB`; `Color::token("color.primary")` references
/// a design token the renderer resolves against the active scheme
/// (spec/TOKENS.md) — emitted as the `DESIGN_TOKEN` value type, never a literal.
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
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(Component::Color, Vec::new(), BTreeMap::new());
        match self {
            Color::Literal(v) => {
                node.properties.insert(property_id::COLOR, *v);
            }
            Color::Token(path) => {
                node.token_properties
                    .insert(property_id::COLOR, String::from(*path));
            }
        }
        node
    }
}

/// Foreground color modifier (SwiftUI `.foregroundStyle`). `ForegroundStyle(color)`
/// static, `ForegroundStyle(signal)` bound.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ForegroundStyle {
    color: Reactive<Color>,
}

/// A foreground color (raw or signal).
#[allow(non_snake_case)]
pub fn ForegroundStyle(color: impl IntoReactive<Color>) -> ForegroundStyle {
    ForegroundStyle {
        color: color.into_reactive(),
    }
}

/// Background color (a `Color` value). `Background(color)` static, bound likewise.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Background {
    color: Reactive<Color>,
}

/// A background color (raw or signal).
#[allow(non_snake_case)]
pub fn Background(color: impl IntoReactive<Color>) -> Background {
    Background {
        color: color.into_reactive(),
    }
}

/// Accent/tint color (a `TINT` property). Raw or signal.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Tint {
    color: Reactive<Color>,
}

/// A tint color (raw or signal).
#[allow(non_snake_case)]
pub fn Tint(color: impl IntoReactive<Color>) -> Tint {
    Tint {
        color: color.into_reactive(),
    }
}

/// The compound `frame` sizing modifier (SwiftUI `frame`).
///
/// Emits `WIDTH`, `HEIGHT`, and optionally `ALIGNMENT` as ordinary
/// `SET_PROPERTY` values. `width`/`height` use the `size::FILL` (-1.0) and
/// `size::HUG_CONTENT` (-2.0) sentinels; `None` leaves that axis to the native
/// renderer. Infinite values (SwiftUI `maxWidth/maxHeight: .infinity`) are
/// normalized to `size::FILL` when applied.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct Frame {
    pub width: Option<f32>,
    pub height: Option<f32>,
    pub alignment: Option<Align>,
}

impl Frame {
    /// A frame from optional width/height/alignment.
    pub const fn new(width: Option<f32>, height: Option<f32>, alignment: Option<Align>) -> Self {
        Self {
            width,
            height,
            alignment,
        }
    }

    /// A fixed `width` x `height` frame with no alignment.
    pub const fn size(width: f32, height: f32) -> Self {
        Self::new(Some(width), Some(height), None)
    }

    /// A fixed `width` with no height hint.
    pub const fn width(width: f32) -> Self {
        Self::new(Some(width), None, None)
    }

    /// A fixed `height` with no width hint.
    pub const fn height(height: f32) -> Self {
        Self::new(None, Some(height), None)
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

/// Declares which raw pointer events a view wants reported (the `EVENT_LISTENERS`
/// u32 bitmask, e.g. `listener::POINTER_DOWN | listener::POINTER_UP`).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct PointerEvents(pub u32);

/// A tap-gesture modifier (SwiftUI `onTapGesture`). Declares the pointer
/// down/up listeners and stores the app-side callback in the node's gesture
/// list.
#[derive(Clone)]
pub struct TapGesture(Rc<RefCell<dyn FnMut() + 'static>>);

impl TapGesture {
    /// A tap gesture invoking `handler` when a tap is recognized.
    pub fn new(handler: impl FnMut() + 'static) -> Self {
        TapGesture(Rc::new(RefCell::new(handler)))
    }
}

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

impl ViewModifier for Padding {
    fn apply(&self, node: &mut Node) {
        reactive_f32(node, property_id::PADDING, &self.value);
    }
}

impl ViewModifier for FontSize {
    fn apply(&self, node: &mut Node) {
        reactive_f32(node, property_id::FONT_SIZE, &self.value);
    }
}

impl ViewModifier for ForegroundStyle {
    fn apply(&self, node: &mut Node) {
        reactive_color(node, property_id::COLOR, &self.color);
    }
}

impl ViewModifier for Background {
    fn apply(&self, node: &mut Node) {
        reactive_color(node, property_id::BACKGROUND_COLOR, &self.color);
    }
}

impl ViewModifier for Tint {
    fn apply(&self, node: &mut Node) {
        reactive_color(node, property_id::TINT, &self.color);
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
            existing | pathland_core::listener::POINTER_DOWN | pathland_core::listener::POINTER_UP,
        );
        node.gestures.push(Gesture::Tap(self.0.clone()));
    }
}

/// Opacity (0..1). `.opacity(_:)`. Raw or signal.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Opacity {
    value: Reactive<f32>,
}

/// An opacity value (raw or signal).
#[allow(non_snake_case)]
pub fn Opacity(value: impl IntoReactive<f32>) -> Opacity {
    Opacity {
        value: value.into_reactive(),
    }
}

/// Hidden (SwiftUI `.hidden()`); sets `VISIBLE` = 0.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct Hidden;

/// Border (SwiftUI `.border(color:width:)`), raw or signal.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Border {
    color: Reactive<Color>,
    width: Reactive<f32>,
}

impl Border {
    /// A border from a color and width (each raw or signal).
    pub fn new(color: impl IntoReactive<Color>, width: impl IntoReactive<f32>) -> Self {
        Self {
            color: color.into_reactive(),
            width: width.into_reactive(),
        }
    }
}

/// Corner radius (SwiftUI `.cornerRadius(_:)`). Raw or signal.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct CornerRadius {
    value: Reactive<f32>,
}

/// A corner radius value (raw or signal).
#[allow(non_snake_case)]
pub fn CornerRadius(value: impl IntoReactive<f32>) -> CornerRadius {
    CornerRadius {
        value: value.into_reactive(),
    }
}

/// Font weight (100–900). `.fontWeight(_:)`. Raw or signal.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct FontWeight {
    value: Reactive<f32>,
}

/// A font weight value (raw or signal).
#[allow(non_snake_case)]
pub fn FontWeight(value: impl IntoReactive<f32>) -> FontWeight {
    FontWeight {
        value: value.into_reactive(),
    }
}

/// Line limit (0 = unlimited). `.lineLimit(_:)`. Raw or signal.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct LineLimit {
    value: Reactive<u32>,
}

/// A line limit value (raw or signal).
#[allow(non_snake_case)]
pub fn LineLimit(value: impl IntoReactive<u32>) -> LineLimit {
    LineLimit {
        value: value.into_reactive(),
    }
}

/// Text alignment (0=Leading, 1=Center, 2=Trailing). Raw or signal.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct TextAlignment {
    value: Reactive<u8>,
}

/// A text alignment value (raw or signal).
#[allow(non_snake_case)]
pub fn TextAlignment(value: impl IntoReactive<u8>) -> TextAlignment {
    TextAlignment {
        value: value.into_reactive(),
    }
}

/// Truncation mode (0=Head, 1=Middle, 2=Tail). Raw or signal.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct TruncationMode {
    value: Reactive<u8>,
}

/// A truncation mode value (raw or signal).
#[allow(non_snake_case)]
pub fn TruncationMode(value: impl IntoReactive<u8>) -> TruncationMode {
    TruncationMode {
        value: value.into_reactive(),
    }
}

/// Post-layout translation. `.offset(x:y:)`. Raw or signal.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Offset {
    x: Reactive<f32>,
    y: Reactive<f32>,
}

impl Offset {
    /// An offset from x and y (each raw or signal).
    pub fn new(x: impl IntoReactive<f32>, y: impl IntoReactive<f32>) -> Self {
        Self {
            x: x.into_reactive(),
            y: y.into_reactive(),
        }
    }
}

/// Absolute position within the parent. `.position(x:y:)`. Raw or signal.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Position {
    x: Reactive<f32>,
    y: Reactive<f32>,
}

impl Position {
    /// A position from x and y (each raw or signal).
    pub fn new(x: impl IntoReactive<f32>, y: impl IntoReactive<f32>) -> Self {
        Self {
            x: x.into_reactive(),
            y: y.into_reactive(),
        }
    }
}

/// Z-index. `.zIndex(_:)`. Raw or signal.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ZIndex {
    value: Reactive<f32>,
}

/// A z-index value (raw or signal).
#[allow(non_snake_case)]
pub fn ZIndex(value: impl IntoReactive<f32>) -> ZIndex {
    ZIndex {
        value: value.into_reactive(),
    }
}

impl ViewModifier for Opacity {
    fn apply(&self, node: &mut Node) {
        reactive_f32(node, property_id::OPACITY, &self.value);
    }
}

impl ViewModifier for Hidden {
    fn apply(&self, node: &mut Node) {
        node.properties.insert(property_id::VISIBLE, 0);
    }
}

impl ViewModifier for Border {
    fn apply(&self, node: &mut Node) {
        reactive_color(node, property_id::BORDER_COLOR, &self.color);
        reactive_f32(node, property_id::BORDER_WIDTH, &self.width);
    }
}

impl ViewModifier for CornerRadius {
    fn apply(&self, node: &mut Node) {
        reactive_f32(node, property_id::BORDER_RADIUS, &self.value);
    }
}

impl ViewModifier for FontWeight {
    fn apply(&self, node: &mut Node) {
        reactive_f32(node, property_id::FONT_WEIGHT, &self.value);
    }
}

impl ViewModifier for LineLimit {
    fn apply(&self, node: &mut Node) {
        reactive_u32(node, property_id::LINE_LIMIT, &self.value);
    }
}

impl ViewModifier for TextAlignment {
    fn apply(&self, node: &mut Node) {
        reactive_enum(node, property_id::TEXT_ALIGNMENT, &self.value);
    }
}

impl ViewModifier for TruncationMode {
    fn apply(&self, node: &mut Node) {
        reactive_enum(node, property_id::TRUNCATION_MODE, &self.value);
    }
}

impl ViewModifier for Offset {
    fn apply(&self, node: &mut Node) {
        reactive_f32(node, property_id::OFFSET_X, &self.x);
        reactive_f32(node, property_id::OFFSET_Y, &self.y);
    }
}

impl ViewModifier for Position {
    fn apply(&self, node: &mut Node) {
        reactive_f32(node, property_id::POSITION_X, &self.x);
        reactive_f32(node, property_id::POSITION_Y, &self.y);
    }
}

impl ViewModifier for ZIndex {
    fn apply(&self, node: &mut Node) {
        reactive_f32(node, property_id::Z_INDEX, &self.value);
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// Build a plain node (no text/gesture/bindings) for a component.
fn plain_node(component: Component, children: Vec<Node>, properties: BTreeMap<u16, u32>) -> Node {
    Node {
        id: 0,
        component,
        children,
        properties,
        string_properties: BTreeMap::new(),
        list_properties: BTreeMap::new(),
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

fn build_children(children: &[Box<dyn View>], env: &mut Environment) -> Vec<Node> {
    children.iter().map(|c| c.build_env(env)).collect()
}

// --- two-way binding sinks (app-side; never serialized) --------------------

fn text_sink(signal: WritableSignal<String>) -> pathland_engine::TextInputHandler {
    Rc::new(RefCell::new(move |value: &str| {
        signal.set(String::from(value));
    }))
}

fn value_sink_f32(signal: WritableSignal<f32>) -> pathland_engine::ValueInputHandler {
    Rc::new(RefCell::new(move |value: f32| {
        signal.set(value);
    }))
}

fn value_sink_bool(signal: WritableSignal<bool>) -> pathland_engine::ValueInputHandler {
    Rc::new(RefCell::new(move |value: f32| {
        signal.set(value > 0.0);
    }))
}

/// Mark a node as value/text/date bound (the renderer's event gate).
fn mark_binding(node: &mut Node) {
    node.properties.insert(property_id::BINDING_ID, 1);
}

/// Bind a text control's value to a signal (two-way).
fn bind_text(node: &mut Node, signal: WritableSignal<String>) {
    node.text_binding = Some(signal.id());
    mark_binding(node);
    node.gestures.push(Gesture::TextInput(text_sink(signal)));
}

/// Bind an f32-valued property (`VALUE`) to a signal (two-way).
fn bind_value_f32(node: &mut Node, prop: u16, signal: WritableSignal<f32>) {
    let initial = signal.get().unwrap_or(0.0);
    node.properties.insert(prop, initial.to_bits());
    node.property_bindings.insert(prop, signal.id());
    mark_binding(node);
    node.gestures.push(Gesture::ValueInput(value_sink_f32(signal)));
}

/// Bind a bool-valued property (`SELECTED`) to a signal (two-way).
fn bind_value_bool(node: &mut Node, prop: u16, signal: WritableSignal<bool>) {
    let initial = signal.get().unwrap_or(false);
    node.properties.insert(prop, if initial { 1 } else { 0 });
    node.property_bindings.insert(prop, signal.id());
    mark_binding(node);
    node.gestures.push(Gesture::ValueInput(value_sink_bool(signal)));
}

// ---------------------------------------------------------------------------
// Primitives
// ---------------------------------------------------------------------------

/// A text leaf.
#[derive(Debug, Clone, PartialEq, Default)]
pub struct Text {
    config: TextConfig,
}

/// [`Text`] values.
#[derive(Debug, Clone, PartialEq, Default)]
pub struct TextConfig {
    /// The static text content.
    pub text: String,
    /// A signal the text binds to (overrides `text` when set).
    pub binding: Option<SignalId>,
}

impl TextConfig {
    /// Set the static text content (clears any signal binding).
    pub fn text(&mut self, text: impl Into<String>) -> &mut Self {
        self.text = text.into();
        self.binding = None;
        self
    }

    /// Bind the text to a signal (re-emits `SET_TEXT` when it changes).
    pub fn text_signal(&mut self, signal: impl IntoSignalId) -> &mut Self {
        self.binding = Some(signal.into_signal_id());
        self
    }
}

impl Text {
    /// A text node with the given content.
    pub fn new(text: &str) -> Self {
        let mut config = TextConfig::default();
        config.text(text);
        Self { config }
    }
}

impl Configurable for Text {
    type Config = TextConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl View for Text {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(
            Component::Text {
                text: self.config.text.clone(),
            },
            Vec::new(),
            BTreeMap::new(),
        );
        node.text_binding = self.config.binding;
        node
    }
}

view_statics!(Text);

/// An image view.
#[derive(Debug, Clone, PartialEq, Default)]
pub struct Image {
    config: ImageConfig,
}

/// [`Image`] values.
#[derive(Debug, Clone, PartialEq, Default)]
pub struct ImageConfig {
    /// The image source (a path or name; emits `IMAGE_SOURCE`).
    pub source: String,
}

impl ImageConfig {
    /// Set the image source.
    pub fn source(&mut self, source: impl Into<String>) -> &mut Self {
        self.source = source.into();
        self
    }
}

impl Image {
    /// An image from a source path/name.
    pub fn new(source: &str) -> Self {
        let mut config = ImageConfig::default();
        config.source(source);
        Self { config }
    }
}

impl Configurable for Image {
    type Config = ImageConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl View for Image {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(Component::Image, Vec::new(), BTreeMap::new());
        if !self.config.source.is_empty() {
            node.string_properties
                .insert(property_id::IMAGE_SOURCE, self.config.source.clone());
        }
        node
    }
}

view_statics!(Image);

/// A free `Image` constructor (shorthand for [`Image::new`]).
pub fn image(source: &str) -> Image {
    Image::new(source)
}

/// The canonical icon vocabulary for [`Icon`] (mirrors
/// `pathland_core::constants::icon`, spec/ICONS.md). Each renderer maps a name
/// to its native icon set.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum IconName {
    Home,
    Settings,
    Search,
    Menu,
    Close,
    Cloud,
    ChevronLeft,
    ChevronRight,
    ChevronUp,
    ChevronDown,
    ArrowLeft,
    ArrowRight,
    Add,
    Remove,
    Check,
    Edit,
    Delete,
    Save,
    Share,
    Download,
    Upload,
    Refresh,
    Play,
    Pause,
    Stop,
    SkipBack,
    SkipForward,
    Volume,
    VolumeMute,
    Shuffle,
    Repeat,
    Info,
    Warning,
    Error,
    Success,
    Heart,
    Star,
    User,
    Users,
    Lock,
    Logout,
    Bell,
    Folder,
    File,
    Image,
    Music,
    Grid,
    List,
    Filter,
}

impl IconName {
    /// The canonical `ICON_NAME` value.
    pub const fn canonical(self) -> &'static str {
        match self {
            IconName::Home => pathland_core::icon::HOME,
            IconName::Settings => pathland_core::icon::SETTINGS,
            IconName::Search => pathland_core::icon::SEARCH,
            IconName::Menu => pathland_core::icon::MENU,
            IconName::Close => pathland_core::icon::CLOSE,
            IconName::Cloud => pathland_core::icon::CLOUD,
            IconName::ChevronLeft => pathland_core::icon::CHEVRON_LEFT,
            IconName::ChevronRight => pathland_core::icon::CHEVRON_RIGHT,
            IconName::ChevronUp => pathland_core::icon::CHEVRON_UP,
            IconName::ChevronDown => pathland_core::icon::CHEVRON_DOWN,
            IconName::ArrowLeft => pathland_core::icon::ARROW_LEFT,
            IconName::ArrowRight => pathland_core::icon::ARROW_RIGHT,
            IconName::Add => pathland_core::icon::ADD,
            IconName::Remove => pathland_core::icon::REMOVE,
            IconName::Check => pathland_core::icon::CHECK,
            IconName::Edit => pathland_core::icon::EDIT,
            IconName::Delete => pathland_core::icon::DELETE,
            IconName::Save => pathland_core::icon::SAVE,
            IconName::Share => pathland_core::icon::SHARE,
            IconName::Download => pathland_core::icon::DOWNLOAD,
            IconName::Upload => pathland_core::icon::UPLOAD,
            IconName::Refresh => pathland_core::icon::REFRESH,
            IconName::Play => pathland_core::icon::PLAY,
            IconName::Pause => pathland_core::icon::PAUSE,
            IconName::Stop => pathland_core::icon::STOP,
            IconName::SkipBack => pathland_core::icon::SKIP_BACK,
            IconName::SkipForward => pathland_core::icon::SKIP_FORWARD,
            IconName::Volume => pathland_core::icon::VOLUME,
            IconName::VolumeMute => pathland_core::icon::VOLUME_MUTE,
            IconName::Shuffle => pathland_core::icon::SHUFFLE,
            IconName::Repeat => pathland_core::icon::REPEAT,
            IconName::Info => pathland_core::icon::INFO,
            IconName::Warning => pathland_core::icon::WARNING,
            IconName::Error => pathland_core::icon::ERROR,
            IconName::Success => pathland_core::icon::SUCCESS,
            IconName::Heart => pathland_core::icon::HEART,
            IconName::Star => pathland_core::icon::STAR,
            IconName::User => pathland_core::icon::USER,
            IconName::Users => pathland_core::icon::USERS,
            IconName::Lock => pathland_core::icon::LOCK,
            IconName::Logout => pathland_core::icon::LOGOUT,
            IconName::Bell => pathland_core::icon::BELL,
            IconName::Folder => pathland_core::icon::FOLDER,
            IconName::File => pathland_core::icon::FILE,
            IconName::Image => pathland_core::icon::IMAGE,
            IconName::Music => pathland_core::icon::MUSIC,
            IconName::Grid => pathland_core::icon::GRID,
            IconName::List => pathland_core::icon::LIST,
            IconName::Filter => pathland_core::icon::FILTER,
        }
    }
}

/// A semantic icon (the `ICON` primitive).
#[derive(Debug, Clone, PartialEq, Default)]
pub struct Icon {
    config: IconConfig,
}

/// [`Icon`] values.
#[derive(Debug, Clone, PartialEq, Default)]
pub struct IconConfig {
    /// The canonical `ICON_NAME`.
    pub name: &'static str,
}

impl IconConfig {
    /// Set the icon name.
    pub fn name(&mut self, name: IconName) -> &mut Self {
        self.name = name.canonical();
        self
    }
}

impl Icon {
    /// A semantic icon from the canonical catalog.
    pub fn new(name: IconName) -> Self {
        let mut config = IconConfig::default();
        config.name(name);
        Self { config }
    }
}

impl Configurable for Icon {
    type Config = IconConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl View for Icon {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(Component::Icon, Vec::new(), BTreeMap::new());
        if !self.config.name.is_empty() {
            node.string_properties
                .insert(property_id::ICON_NAME, String::from(self.config.name));
        }
        node
    }
}

view_statics!(Icon);

/// A semantic icon (shorthand for [`Icon::new`]).
pub fn icon(name: IconName) -> Icon {
    Icon::new(name)
}

/// A vector geometry of a given [`ShapeKind`].
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct Shape(pub ShapeKind);

impl Shape {
    /// A shape of the given kind.
    pub const fn new(kind: ShapeKind) -> Self {
        Shape(kind)
    }
}

impl View for Shape {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut p = BTreeMap::new();
        p.insert(property_id::SHAPE_KIND, (self.0.value() as f32).to_bits());
        plain_node(Component::Shape, Vec::new(), p)
    }
}

/// A named rectangle shape.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct Rectangle;
/// A named circle shape.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct Circle;
/// A named capsule shape.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct Capsule;
/// A named ellipse shape.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct Ellipse;

impl View for Rectangle {
    fn build_env(&self, _env: &mut Environment) -> Node {
        Shape(ShapeKind::Rectangle).build_env(_env)
    }
}
impl View for Circle {
    fn build_env(&self, _env: &mut Environment) -> Node {
        Shape(ShapeKind::Circle).build_env(_env)
    }
}
impl View for Capsule {
    fn build_env(&self, _env: &mut Environment) -> Node {
        Shape(ShapeKind::Capsule).build_env(_env)
    }
}
impl View for Ellipse {
    fn build_env(&self, _env: &mut Environment) -> Node {
        Shape(ShapeKind::Ellipse).build_env(_env)
    }
}

/// A separator line.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct Divider;

impl View for Divider {
    fn build_env(&self, _env: &mut Environment) -> Node {
        plain_node(Component::Divider, Vec::new(), BTreeMap::new())
    }
}

/// A flexible spacer (shorthand for [`Spacer`]).
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct Spacer;

impl View for Spacer {
    fn build_env(&self, _env: &mut Environment) -> Node {
        plain_node(Component::Spacer, Vec::new(), BTreeMap::new())
    }
}

/// A flexible spacer (shorthand for [`Spacer`]).
pub fn spacer() -> Spacer {
    Spacer
}

/// Determinate progress (fraction) or an activity indicator.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct ProgressView {
    config: ProgressViewConfig,
}

/// [`ProgressView`] values.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct ProgressViewConfig {
    /// `Some(fraction)` for determinate progress; `None` = indeterminate. Raw or signal.
    pub value: Option<Reactive<f32>>,
}

impl ProgressViewConfig {
    /// Set the determinate fraction (0..1), raw or signal.
    pub fn value(&mut self, value: impl IntoReactive<f32>) -> &mut Self {
        self.value = Some(value.into_reactive());
        self
    }
}

impl View for ProgressView {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(Component::ProgressView, Vec::new(), BTreeMap::new());
        match &self.config.value {
            Some(v) => reactive_f32(&mut node, property_id::PROGRESS, v),
            None => {
                node.properties.insert(property_id::IS_INDETERMINATE, 1);
            }
        }
        node
    }
}

impl Configurable for ProgressView {
    type Config = ProgressViewConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

view_statics!(ProgressView);

/// A range meter.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct Gauge {
    config: GaugeConfig,
}

/// [`Gauge`] values.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct GaugeConfig {
    /// The current value (raw or signal).
    pub value: Reactive<f32>,
    /// The range minimum (raw or signal).
    pub min: Reactive<f32>,
    /// The range maximum (raw or signal).
    pub max: Reactive<f32>,
}

impl Default for GaugeConfig {
    fn default() -> Self {
        Self {
            value: Reactive::Static(0.0),
            min: Reactive::Static(0.0),
            max: Reactive::Static(0.0),
        }
    }
}

impl GaugeConfig {
    pub fn value(&mut self, value: impl IntoReactive<f32>) -> &mut Self {
        self.value = value.into_reactive();
        self
    }
    pub fn min(&mut self, min: impl IntoReactive<f32>) -> &mut Self {
        self.min = min.into_reactive();
        self
    }
    pub fn max(&mut self, max: impl IntoReactive<f32>) -> &mut Self {
        self.max = max.into_reactive();
        self
    }
}

impl View for Gauge {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(Component::Gauge, Vec::new(), BTreeMap::new());
        reactive_f32(&mut node, property_id::VALUE, &self.config.value);
        reactive_f32(&mut node, property_id::MIN_VALUE, &self.config.min);
        reactive_f32(&mut node, property_id::MAX_VALUE, &self.config.max);
        node
    }
}

impl Configurable for Gauge {
    type Config = GaugeConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

view_statics!(Gauge);

// ---------------------------------------------------------------------------
// Layout containers
// ---------------------------------------------------------------------------

container_debug!(VStack);
/// A vertical stack.
#[derive(Default)]
pub struct VStack {
    config: StackConfig,
    children: Vec<Box<dyn View>>,
}

/// Stack values (`VStack`/`HStack`/`LazyVStack`/`LazyHStack`).
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct StackConfig {
    /// Cross-axis alignment.
    pub alignment: Option<Align>,
    /// Main-axis gap (raw or signal).
    pub spacing: Option<Reactive<f32>>,
}

impl StackConfig {
    /// Set the cross-axis alignment.
    pub fn alignment(&mut self, alignment: Align) -> &mut Self {
        self.alignment = Some(alignment);
        self
    }
    /// Set the main-axis gap (raw or signal; a signal re-emits only `SPACING`).
    pub fn spacing(&mut self, spacing: impl IntoReactive<f32>) -> &mut Self {
        self.spacing = Some(spacing.into_reactive());
        self
    }
}

impl VStack {
    /// An empty vertical stack.
    pub fn new() -> Self {
        Self::default()
    }
}

impl Configurable for VStack {
    type Config = StackConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl Children for VStack {
    fn set_children(&mut self, children: Vec<Box<dyn View>>) {
        self.children = children;
    }
}

impl View for VStack {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut p = BTreeMap::new();
        if let Some(a) = self.config.alignment {
            p.insert(property_id::ALIGNMENT, (a.value() as f32).to_bits());
        }
        let mut node = plain_node(Component::VStack, build_children(&self.children, _env), p);
        if let Some(s) = self.config.spacing {
            reactive_f32(&mut node, property_id::SPACING, &s);
        }
        node
    }
}

view_statics!(VStack);
children_static!(VStack);

container_debug!(HStack);
/// A horizontal stack.
#[derive(Default)]
pub struct HStack {
    config: StackConfig,
    children: Vec<Box<dyn View>>,
}

impl HStack {
    /// An empty horizontal stack.
    pub fn new() -> Self {
        Self::default()
    }
}

impl Configurable for HStack {
    type Config = StackConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl Children for HStack {
    fn set_children(&mut self, children: Vec<Box<dyn View>>) {
        self.children = children;
    }
}

impl View for HStack {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut p = BTreeMap::new();
        if let Some(a) = self.config.alignment {
            p.insert(property_id::ALIGNMENT, (a.value() as f32).to_bits());
        }
        let mut node = plain_node(Component::HStack, build_children(&self.children, _env), p);
        if let Some(s) = self.config.spacing {
            reactive_f32(&mut node, property_id::SPACING, &s);
        }
        node
    }
}

view_statics!(HStack);
children_static!(HStack);

container_debug!(ZStack);
/// An overlapping stack.
#[derive(Default)]
pub struct ZStack {
    config: StackConfig,
    children: Vec<Box<dyn View>>,
}

impl ZStack {
    /// An empty overlapping stack.
    pub fn new() -> Self {
        Self::default()
    }
}

impl Configurable for ZStack {
    type Config = StackConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl Children for ZStack {
    fn set_children(&mut self, children: Vec<Box<dyn View>>) {
        self.children = children;
    }
}

impl View for ZStack {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut p = BTreeMap::new();
        if let Some(a) = self.config.alignment {
            p.insert(property_id::ALIGNMENT, (a.value() as f32).to_bits());
        }
        plain_node(Component::ZStack, build_children(&self.children, _env), p)
    }
}

view_statics!(ZStack);
children_static!(ZStack);

container_debug!(Grid);
/// A static grid.
#[derive(Default)]
pub struct Grid {
    config: GridConfig,
    children: Vec<Box<dyn View>>,
}

/// Grid values (`Grid`/`LazyVGrid`/`LazyHGrid`).
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct GridConfig {
    /// Fixed column count (`GRID_COLUMNS`, raw or signal).
    pub columns: Option<Reactive<f32>>,
    /// Fixed row count (`GRID_ROWS`, raw or signal).
    pub rows: Option<Reactive<f32>>,
    /// Cross-axis alignment.
    pub alignment: Option<Align>,
    /// Main-axis gap (raw or signal).
    pub spacing: Option<Reactive<f32>>,
}

impl GridConfig {
    pub fn columns(&mut self, columns: impl IntoReactive<f32>) -> &mut Self {
        self.columns = Some(columns.into_reactive());
        self
    }
    pub fn rows(&mut self, rows: impl IntoReactive<f32>) -> &mut Self {
        self.rows = Some(rows.into_reactive());
        self
    }
    pub fn alignment(&mut self, alignment: Align) -> &mut Self {
        self.alignment = Some(alignment);
        self
    }
    pub fn spacing(&mut self, spacing: impl IntoReactive<f32>) -> &mut Self {
        self.spacing = Some(spacing.into_reactive());
        self
    }
}

fn grid_apply(config: &GridConfig, node: &mut Node) {
    if let Some(c) = config.columns {
        reactive_f32(node, property_id::GRID_COLUMNS, &c);
    }
    if let Some(r) = config.rows {
        reactive_f32(node, property_id::GRID_ROWS, &r);
    }
    if let Some(a) = config.alignment {
        node.properties
            .insert(property_id::ALIGNMENT, (a.value() as f32).to_bits());
    }
    if let Some(s) = config.spacing {
        reactive_f32(node, property_id::SPACING, &s);
    }
}

impl Grid {
    /// An empty grid.
    pub fn new() -> Self {
        Self::default()
    }
}

impl Configurable for Grid {
    type Config = GridConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl Children for Grid {
    fn set_children(&mut self, children: Vec<Box<dyn View>>) {
        self.children = children;
    }
}

impl View for Grid {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(
            Component::Grid,
            build_children(&self.children, _env),
            BTreeMap::new(),
        );
        grid_apply(&self.config, &mut node);
        node
    }
}

view_statics!(Grid);
children_static!(Grid);

container_debug!(ScrollView);
/// A scrollable container.
#[derive(Default)]
pub struct ScrollView {
    children: Vec<Box<dyn View>>,
}

impl ScrollView {
    /// An empty scroll view.
    pub fn new() -> Self {
        Self::default()
    }
}

impl Children for ScrollView {
    fn set_children(&mut self, children: Vec<Box<dyn View>>) {
        self.children = children;
    }
}

impl View for ScrollView {
    fn build_env(&self, _env: &mut Environment) -> Node {
        plain_node(
            Component::ScrollView,
            build_children(&self.children, _env),
            BTreeMap::new(),
        )
    }
}

children_static!(ScrollView);

container_debug!(LazyVGrid);
/// A virtualized vertical grid.
#[derive(Default)]
pub struct LazyVGrid {
    config: GridConfig,
    children: Vec<Box<dyn View>>,
}

impl LazyVGrid {
    /// An empty lazy vertical grid.
    pub fn new() -> Self {
        Self::default()
    }
}

impl Configurable for LazyVGrid {
    type Config = GridConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl Children for LazyVGrid {
    fn set_children(&mut self, children: Vec<Box<dyn View>>) {
        self.children = children;
    }
}

impl View for LazyVGrid {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(
            Component::LazyVGrid,
            build_children(&self.children, _env),
            BTreeMap::new(),
        );
        grid_apply(&self.config, &mut node);
        node
    }
}

view_statics!(LazyVGrid);
children_static!(LazyVGrid);

container_debug!(LazyHGrid);
/// A virtualized horizontal grid.
#[derive(Default)]
pub struct LazyHGrid {
    config: GridConfig,
    children: Vec<Box<dyn View>>,
}

impl LazyHGrid {
    /// An empty lazy horizontal grid.
    pub fn new() -> Self {
        Self::default()
    }
}

impl Configurable for LazyHGrid {
    type Config = GridConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl Children for LazyHGrid {
    fn set_children(&mut self, children: Vec<Box<dyn View>>) {
        self.children = children;
    }
}

impl View for LazyHGrid {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(
            Component::LazyHGrid,
            build_children(&self.children, _env),
            BTreeMap::new(),
        );
        grid_apply(&self.config, &mut node);
        node
    }
}

view_statics!(LazyHGrid);
children_static!(LazyHGrid);

container_debug!(LazyVStack);
/// A virtualized vertical stack.
#[derive(Default)]
pub struct LazyVStack {
    config: StackConfig,
    children: Vec<Box<dyn View>>,
}

impl LazyVStack {
    /// An empty lazy vertical stack.
    pub fn new() -> Self {
        Self::default()
    }
}

impl Configurable for LazyVStack {
    type Config = StackConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl Children for LazyVStack {
    fn set_children(&mut self, children: Vec<Box<dyn View>>) {
        self.children = children;
    }
}

impl View for LazyVStack {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut p = BTreeMap::new();
        if let Some(a) = self.config.alignment {
            p.insert(property_id::ALIGNMENT, (a.value() as f32).to_bits());
        }
        let mut node = plain_node(Component::LazyVStack, build_children(&self.children, _env), p);
        if let Some(s) = self.config.spacing {
            reactive_f32(&mut node, property_id::SPACING, &s);
        }
        node
    }
}

view_statics!(LazyVStack);
children_static!(LazyVStack);

container_debug!(LazyHStack);
/// A virtualized horizontal stack.
#[derive(Default)]
pub struct LazyHStack {
    config: StackConfig,
    children: Vec<Box<dyn View>>,
}

impl LazyHStack {
    /// An empty lazy horizontal stack.
    pub fn new() -> Self {
        Self::default()
    }
}

impl Configurable for LazyHStack {
    type Config = StackConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl Children for LazyHStack {
    fn set_children(&mut self, children: Vec<Box<dyn View>>) {
        self.children = children;
    }
}

impl View for LazyHStack {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut p = BTreeMap::new();
        if let Some(a) = self.config.alignment {
            p.insert(property_id::ALIGNMENT, (a.value() as f32).to_bits());
        }
        let mut node = plain_node(Component::LazyHStack, build_children(&self.children, _env), p);
        if let Some(s) = self.config.spacing {
            reactive_f32(&mut node, property_id::SPACING, &s);
        }
        node
    }
}

view_statics!(LazyHStack);
children_static!(LazyHStack);

// ---------------------------------------------------------------------------
// Semantic controls
// ---------------------------------------------------------------------------

/// An interactive button.
#[derive(Default)]
pub struct Button {
    config: ButtonConfig,
}

/// [`Button`] values.
#[derive(Default)]
pub struct ButtonConfig {
    /// The button label.
    pub label: String,
    /// The action fired on tap.
    pub action: Option<Rc<RefCell<dyn FnMut() + 'static>>>,
}

impl ButtonConfig {
    /// Set the button label.
    pub fn label(&mut self, label: impl Into<String>) -> &mut Self {
        self.label = label.into();
        self
    }

    /// Set the action fired on tap.
    pub fn action(&mut self, action: impl FnMut() + 'static) -> &mut Self {
        self.action = Some(Rc::new(RefCell::new(action)));
        self
    }
}

impl Button {
    /// A button with the given label.
    pub fn new(label: &str) -> Self {
        let mut config = ButtonConfig::default();
        config.label(label);
        Self { config }
    }
}

impl Configurable for Button {
    type Config = ButtonConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl View for Button {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(
            Component::Button {
                label: self.config.label.clone(),
            },
            Vec::new(),
            BTreeMap::new(),
        );
        // A button action is a tap gesture on the button node (whole button
        // tappable); the renderer reports the raw pointer events.
        if let Some(action) = &self.config.action {
            let existing = node
                .properties
                .get(&property_id::EVENT_LISTENERS)
                .copied()
                .unwrap_or(0);
            node.properties.insert(
                property_id::EVENT_LISTENERS,
                existing | pathland_core::listener::POINTER_DOWN | pathland_core::listener::POINTER_UP,
            );
            node.gestures.push(Gesture::Tap(action.clone()));
        }
        // The active style supplies the button's content (Composite Override
        // Mode); the default style keeps the native path (no content child).
        let style = _env.button_style();
        if let Some(content) = style.make_body(&self.config) {
            node.children.push(content.build_env(_env));
        }
        node
    }
}

view_statics!(Button);

/// A button view (shorthand for [`Button::new`]).
pub fn button(label: &str) -> Button {
    Button::new(label)
}

/// A single-line text input.
#[derive(Debug, Clone, Default)]
pub struct TextField {
    config: TextFieldConfig,
}

/// [`TextField`] values.
#[derive(Debug, Clone, Default)]
pub struct TextFieldConfig {
    /// The placeholder (emits `PROMPT`).
    pub placeholder: String,
    /// A two-way text binding (writes back on `TEXT_CHANGED`).
    pub binding: Option<WritableSignal<String>>,
}

impl TextFieldConfig {
    /// Set the placeholder.
    pub fn placeholder(&mut self, placeholder: impl Into<String>) -> &mut Self {
        self.placeholder = placeholder.into();
        self
    }

    /// Bind the field's value to a signal (two-way).
    pub fn text(&mut self, signal: WritableSignal<String>) -> &mut Self {
        self.binding = Some(signal);
        self
    }
}

impl TextField {
    /// A text field with the given placeholder.
    pub fn new(placeholder: &str) -> Self {
        let mut config = TextFieldConfig::default();
        config.placeholder(placeholder);
        Self { config }
    }
}

impl Configurable for TextField {
    type Config = TextFieldConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl View for TextField {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(Component::TextField, Vec::new(), BTreeMap::new());
        if !self.config.placeholder.is_empty() {
            node.string_properties
                .insert(property_id::PROMPT, self.config.placeholder.clone());
        }
        if let Some(signal) = &self.config.binding {
            bind_text(&mut node, signal.clone());
        }
        node
    }
}

view_statics!(TextField);

/// A multi-line text input.
#[derive(Debug, Clone, Default)]
pub struct TextEditor {
    config: TextEditorConfig,
}

/// [`TextEditor`] values.
#[derive(Debug, Clone, Default)]
pub struct TextEditorConfig {
    /// A two-way text binding (writes back on `TEXT_CHANGED`).
    pub binding: Option<WritableSignal<String>>,
}

impl TextEditorConfig {
    /// Bind the editor's value to a signal (two-way).
    pub fn text(&mut self, signal: WritableSignal<String>) -> &mut Self {
        self.binding = Some(signal);
        self
    }
}

impl Configurable for TextEditor {
    type Config = TextEditorConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl View for TextEditor {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(Component::TextEditor, Vec::new(), BTreeMap::new());
        if let Some(signal) = &self.config.binding {
            bind_text(&mut node, signal.clone());
        }
        node
    }
}

view_statics!(TextEditor);

/// A boolean control with a `TOGGLE_STYLE` token (0=Switch, 1=Checkbox, 2=Button).
#[derive(Debug, Clone, Default)]
pub struct Toggle {
    config: ToggleConfig,
}

/// [`Toggle`] values.
#[derive(Debug, Clone, Default)]
pub struct ToggleConfig {
    /// The `TOGGLE_STYLE` token (0=Switch, 1=Checkbox, 2=Button).
    pub style: u8,
    /// A two-way boolean binding (writes back on `VALUE_CHANGED`).
    pub binding: Option<WritableSignal<bool>>,
}

impl ToggleConfig {
    /// Set the toggle style token.
    pub fn style(&mut self, style: u8) -> &mut Self {
        self.style = style;
        self
    }
    /// Bind the checked state to a signal (two-way).
    pub fn is_on(&mut self, signal: WritableSignal<bool>) -> &mut Self {
        self.binding = Some(signal);
        self
    }
}

impl View for Toggle {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut p = BTreeMap::new();
        p.insert(
            property_id::TOGGLE_STYLE,
            (self.config.style as f32).to_bits(),
        );
        let mut node = plain_node(Component::Toggle, Vec::new(), p);
        if let Some(signal) = &self.config.binding {
            bind_value_bool(&mut node, property_id::SELECTED, signal.clone());
        }
        node
    }
}

impl Configurable for Toggle {
    type Config = ToggleConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

view_statics!(Toggle);

/// A numeric range control.
#[derive(Debug, Clone, Default)]
pub struct Slider {
    config: RangeConfig,
}

/// A numeric range (`Slider`/`Stepper`): value + min + max (+ optional binding).
#[derive(Debug, Clone, Default)]
pub struct RangeConfig {
    /// The current value.
    pub value: f32,
    /// The range minimum.
    pub min: f32,
    /// The range maximum.
    pub max: f32,
    /// A two-way value binding (writes back on `VALUE_CHANGED`).
    pub binding: Option<WritableSignal<f32>>,
}

impl RangeConfig {
    pub fn value(&mut self, value: f32) -> &mut Self {
        self.value = value;
        self
    }
    pub fn min(&mut self, min: f32) -> &mut Self {
        self.min = min;
        self
    }
    pub fn max(&mut self, max: f32) -> &mut Self {
        self.max = max;
        self
    }
    /// Bind the value to a signal (two-way); its current value is the initial.
    pub fn bind(&mut self, signal: WritableSignal<f32>) -> &mut Self {
        self.value = signal.get().unwrap_or(0.0);
        self.binding = Some(signal);
        self
    }
}

fn range_props(config: &RangeConfig) -> BTreeMap<u16, u32> {
    let mut p = BTreeMap::new();
    p.insert(property_id::VALUE, config.value.to_bits());
    p.insert(property_id::MIN_VALUE, config.min.to_bits());
    p.insert(property_id::MAX_VALUE, config.max.to_bits());
    p
}

fn build_range(component: Component, config: &RangeConfig) -> Node {
    let mut node = plain_node(component, Vec::new(), range_props(config));
    if let Some(signal) = &config.binding {
        bind_value_f32(&mut node, property_id::VALUE, signal.clone());
    }
    node
}

impl View for Slider {
    fn build_env(&self, _env: &mut Environment) -> Node {
        build_range(Component::Slider, &self.config)
    }
}

impl Configurable for Slider {
    type Config = RangeConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

view_statics!(Slider);

/// An increment/decrement control.
#[derive(Debug, Clone, Default)]
pub struct Stepper {
    config: RangeConfig,
}

impl View for Stepper {
    fn build_env(&self, _env: &mut Environment) -> Node {
        build_range(Component::Stepper, &self.config)
    }
}

impl Configurable for Stepper {
    type Config = RangeConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

view_statics!(Stepper);

/// A date & time picker.
#[derive(Debug, Clone, Default)]
pub struct DatePicker {
    config: DatePickerConfig,
}

/// [`DatePicker`] values.
#[derive(Debug, Clone, Default)]
pub struct DatePickerConfig {
    /// The `DATE_PICKER_MODE` token (date/time/dateAndTime).
    pub mode: u8,
}

impl DatePickerConfig {
    /// Set the `DATE_PICKER_MODE` token.
    pub fn mode(&mut self, mode: u8) -> &mut Self {
        self.mode = mode;
        self
    }
}

impl Configurable for DatePicker {
    type Config = DatePickerConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

impl View for DatePicker {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(Component::DatePicker, Vec::new(), BTreeMap::new());
        if self.config.mode != 0 {
            node.properties.insert(
                property_id::DATE_PICKER_MODE,
                (self.config.mode as f32).to_bits(),
            );
        }
        node
    }
}

view_statics!(DatePicker);

container_debug!(Picker);
/// A selection control (options are children).
#[derive(Default)]
pub struct Picker {
    children: Vec<Box<dyn View>>,
    binding: Option<WritableSignal<f32>>,
}

impl Picker {
    /// An empty picker.
    pub fn new() -> Self {
        Self::default()
    }
    /// Bind the selected index (as `f32`) to a signal (two-way).
    pub fn bind(mut self, signal: WritableSignal<f32>) -> Self {
        self.binding = Some(signal);
        self
    }
}

impl Children for Picker {
    fn set_children(&mut self, children: Vec<Box<dyn View>>) {
        self.children = children;
    }
}

impl View for Picker {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut node = plain_node(
            Component::Picker,
            build_children(&self.children, _env),
            BTreeMap::new(),
        );
        if let Some(signal) = &self.binding {
            bind_value_f32(&mut node, property_id::VALUE, signal.clone());
        }
        node
    }
}

children_static!(Picker);

container_debug!(Menu);
/// A contextual action trigger + popover (action items are children).
#[derive(Default)]
pub struct Menu {
    children: Vec<Box<dyn View>>,
}

impl Menu {
    /// An empty menu.
    pub fn new() -> Self {
        Self::default()
    }
}

impl Children for Menu {
    fn set_children(&mut self, children: Vec<Box<dyn View>>) {
        self.children = children;
    }
}

impl View for Menu {
    fn build_env(&self, _env: &mut Environment) -> Node {
        plain_node(
            Component::Menu,
            build_children(&self.children, _env),
            BTreeMap::new(),
        )
    }
}

children_static!(Menu);

/// A native color picker.
#[derive(Debug, Clone, Default)]
pub struct ColorPicker {
    config: ColorPickerConfig,
}

/// [`ColorPicker`] values.
#[derive(Debug, Clone, Default)]
pub struct ColorPickerConfig {
    /// The packed `0xAARRGGBB` value.
    pub value: u32,
}

impl ColorPickerConfig {
    /// Set the packed color value.
    pub fn value(&mut self, value: u32) -> &mut Self {
        self.value = value;
        self
    }
}

impl View for ColorPicker {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut p = BTreeMap::new();
        p.insert(property_id::COLOR_VALUE, self.config.value);
        plain_node(Component::ColorPicker, Vec::new(), p)
    }
}

impl Configurable for ColorPicker {
    type Config = ColorPickerConfig;
    fn config(&self) -> &Self::Config {
        &self.config
    }
    fn config_mut(&mut self) -> &mut Self::Config {
        &mut self.config
    }
}

view_statics!(ColorPicker);

// ---------------------------------------------------------------------------
// Structural reactivity + navigation
// ---------------------------------------------------------------------------

/// A **structural container**: builds `then` or `else` from a boolean selector
/// signal. Structural reactivity is the Rust DSL's rebuild model — the host
/// re-builds the tree and re-emits, and the engine reconciles the change into
/// `TREE` deltas (identical structure emits zero opcodes).
pub struct Conditional {
    selector: Signal<bool>,
    then_branch: Box<dyn View>,
    else_branch: Box<dyn View>,
}

impl Conditional {
    /// `if selector { then } else { else }`.
    pub fn when(
        selector: Signal<bool>,
        then_branch: impl View + 'static,
        else_branch: impl View + 'static,
    ) -> Self {
        Self {
            selector,
            then_branch: Box::new(then_branch),
            else_branch: Box::new(else_branch),
        }
    }
}

impl View for Conditional {
    fn build_env(&self, env: &mut Environment) -> Node {
        if self.selector.get().unwrap_or(false) {
            self.then_branch.build_env(env)
        } else {
            self.else_branch.build_env(env)
        }
    }
}

/// Path parameters captured from a route pattern (`/users/:id`).
#[derive(Debug, Clone, Default, PartialEq)]
pub struct Params {
    values: BTreeMap<String, String>,
}

impl Params {
    /// A parameter by name.
    pub fn get(&self, key: &str) -> Option<&str> {
        self.values.get(key).map(String::as_str)
    }

    /// An `i64` parameter.
    pub fn int_value(&self, key: &str) -> Option<i64> {
        self.get(key).and_then(|s| s.parse().ok())
    }
}

/// A destination factory.
type RouteFactory = Rc<dyn Fn(&Params) -> Box<dyn View>>;

/// Maps path patterns to destination factories.
#[derive(Default)]
pub struct RouteTable {
    routes: Vec<(String, RouteFactory)>,
    fallback: Option<RouteFactory>,
}

impl RouteTable {
    /// An empty route table.
    pub fn new() -> Self {
        Self::default()
    }

    /// Add a route (`/users/:id`).
    pub fn route(
        mut self,
        pattern: &str,
        factory: impl Fn(&Params) -> Box<dyn View> + 'static,
    ) -> Self {
        self.routes.push((String::from(pattern), Rc::new(factory)));
        self
    }

    /// Set the fallback (404) destination.
    pub fn fallback(mut self, factory: impl Fn(&Params) -> Box<dyn View> + 'static) -> Self {
        self.fallback = Some(Rc::new(factory));
        self
    }

    /// Build the destination for `path` (the fallback if nothing matches).
    pub fn build(&self, path: &str) -> Box<dyn View> {
        for (pattern, factory) in &self.routes {
            if let Some(params) = match_path(pattern, path) {
                return factory(&params);
            }
        }
        if let Some(fallback) = &self.fallback {
            return fallback(&Params::default());
        }
        Box::new(Spacer)
    }
}

/// Match a `path` against a `/a/:b` pattern, capturing params.
fn match_path(pattern: &str, path: &str) -> Option<Params> {
    let pat: Vec<&str> = pattern.trim_matches('/').split('/').collect();
    let got: Vec<&str> = path.trim_matches('/').split('/').collect();
    if pat.len() != got.len() {
        return None;
    }
    let mut params = Params::default();
    for (p, g) in pat.iter().zip(got.iter()) {
        if let Some(name) = p.strip_prefix(':') {
            params.values.insert(String::from(name), String::from(*g));
        } else if p != g {
            return None;
        }
    }
    Some(params)
}

/// The navigation operation a link performs.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NavOp {
    /// Direct selection (no back-stack entry).
    Navigate,
    /// Drill-down (push the current path).
    Push,
    /// Replace the current path (no back-stack change).
    Replace,
}

/// App-owned navigation state: the current-path signal plus a back-stack.
pub struct Router {
    path: WritableSignal<String>,
    back: Rc<RefCell<Vec<String>>>,
}

impl Router {
    /// A router over a current-path signal.
    pub fn new(path: WritableSignal<String>) -> Self {
        Self {
            path,
            back: Rc::new(RefCell::new(Vec::new())),
        }
    }

    /// The current path.
    pub fn path(&self) -> String {
        self.path.get().unwrap_or_default()
    }

    /// The back-stack depth.
    pub fn depth(&self) -> u32 {
        self.back.borrow().len() as u32
    }

    /// Select `to` (no back-stack entry).
    pub fn navigate(&self, to: impl Into<String>) {
        self.path.set(to.into());
    }

    /// Drill down to `to`, pushing the current path.
    pub fn push(&self, to: impl Into<String>) {
        self.back.borrow_mut().push(self.path());
        self.path.set(to.into());
    }

    /// Replace the current path (no back-stack change).
    pub fn replace(&self, to: impl Into<String>) {
        self.path.set(to.into());
    }

    /// Go back one step (pop the back-stack).
    pub fn pop(&self) {
        if let Some(previous) = self.back.borrow_mut().pop() {
            self.path.set(previous);
        }
    }

    /// The current-path signal (for host URL sync).
    pub fn signal(&self) -> WritableSignal<String> {
        self.path.clone()
    }
}

/// A navigation slot: builds the current destination and carries `ROUTE` /
/// `NAV_DEPTH` / `NAV_CHROME` on its container node. A slot carrying `ROUTE`
/// is the structural trigger a renderer may promote onto native navigation.
pub struct NavigationContainer {
    router: Rc<Router>,
    table: RouteTable,
    chrome: u8,
}

impl NavigationContainer {
    /// A navigation slot over `router` with `table`.
    pub fn new(router: Rc<Router>, table: RouteTable) -> Self {
        Self {
            router,
            table,
            chrome: 0,
        }
    }

    /// Set the `NAV_CHROME` mode token (0 = platform default, 1 = custom).
    pub fn chrome(mut self, chrome: u8) -> Self {
        self.chrome = chrome;
        self
    }
}

impl View for NavigationContainer {
    fn build_env(&self, env: &mut Environment) -> Node {
        let path = self.router.path();
        let destination = self.table.build(&path).build_env(env);
        let mut node = plain_node(Component::VStack, vec![destination], BTreeMap::new());
        node.string_properties.insert(property_id::ROUTE, path);
        node.properties
            .insert(property_id::NAV_DEPTH, self.router.depth());
        node.properties
            .insert(property_id::NAV_CHROME, (self.chrome as f32).to_bits());
        node
    }
}

/// A button that changes the route through a router.
pub struct NavigationLink {
    label: String,
    router: Rc<Router>,
    to: String,
    op: NavOp,
}

impl NavigationLink {
    /// A push link (`label` → `to`).
    pub fn of(label: &str, router: Rc<Router>, to: &str) -> Self {
        Self {
            label: String::from(label),
            router,
            to: String::from(to),
            op: NavOp::Push,
        }
    }

    /// A link with an explicit operation.
    pub fn with_op(label: &str, router: Rc<Router>, to: &str, op: NavOp) -> Self {
        Self {
            label: String::from(label),
            router,
            to: String::from(to),
            op,
        }
    }
}

impl View for NavigationLink {
    fn build_env(&self, env: &mut Environment) -> Node {
        let router = self.router.clone();
        let to = self.to.clone();
        let op = self.op;
        Button::with(|b| {
            b.label(self.label.clone());
            b.action(move || match op {
                NavOp::Navigate => router.navigate(to.clone()),
                NavOp::Push => router.push(to.clone()),
                NavOp::Replace => router.replace(to.clone()),
            });
        })
        .build_env(env)
    }
}

// ---------------------------------------------------------------------------
// Core component macros + free functions
// ---------------------------------------------------------------------------

/// Build a vertical stack from a comma-separated list of views.
///
/// Each child is boxed automatically, so children can mix `Text`, `HStack`,
/// modified views, or custom `View` implementations.
///
/// ```
/// use pathland_view::{vstack, text, View};
/// let node = vstack![text("a"), text("b")].build();
/// # let _ = node;
/// ```
#[macro_export]
macro_rules! vstack {
    ($($child:expr),* $(,)?) => {{
        #[allow(unused_imports)]
        use $crate::Children as _;
        $crate::VStack::new().children($crate::vec![$($crate::Box::new($child)),*])
    }};
}

/// Build a horizontal stack from a comma-separated list of views.
///
/// See [`vstack!`] for details.
#[macro_export]
macro_rules! hstack {
    ($($child:expr),* $(,)?) => {{
        #[allow(unused_imports)]
        use $crate::Children as _;
        $crate::HStack::new().children($crate::vec![$($crate::Box::new($child)),*])
    }};
}

/// A text view from a string literal (shorthand for [`Text::new`]).
pub fn text(content: &str) -> Text {
    Text::new(content)
}

// ---------------------------------------------------------------------------
// SizeThatFits (fit slot)
// ---------------------------------------------------------------------------

/// A `SizeThatFits` candidate: a view plus the minimum container width at which
/// it is selected. The candidates' thresholds form the wire `FIT_QUERY` table
/// (ascending); the renderer measures its allocated width, derives the fit
/// locally, and reports `FIT_CHANGED` (spec/PRIMITIVES.md §SizeThatFits).
pub struct Fit {
    view: Box<dyn View>,
    min_width: f32,
}

impl Fit {
    /// A candidate shown when the slot's width is at least `min_width`.
    pub fn new(view: impl View + 'static, min_width: f32) -> Self {
        Self {
            view: Box::new(view),
            min_width,
        }
    }

    /// A candidate with threshold 0 — always applicable (the fallback).
    pub fn any(view: impl View + 'static) -> Self {
        Self {
            view: Box::new(view),
            min_width: 0.0,
        }
    }
}

/// The fit slot (`SIZE_THAT_FITS`): shows **one** candidate — the selected one
/// — so only the selected child is ever transmitted. Candidates are held DSL-side;
/// the emitted `FIT_QUERY` (ascending thresholds) lets the renderer pick.
///
/// The Rust DSL currently holds a **fixed** selected index (reactive selection
/// via `FIT_CHANGED` is the Java DSL's structural slot; the retained
/// `Component::SizeThatFits` output is identical).
#[derive(Default)]
pub struct SizeThatFits {
    fits: Vec<Fit>,
    fit_index: usize,
}

impl core::fmt::Debug for SizeThatFits {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("SizeThatFits")
            .field("fits", &self.fits.len())
            .field("fit_index", &self.fit_index)
            .finish()
    }
}

impl SizeThatFits {
    /// A slot over the given candidates (author order = fit preference; the
    /// emitted `FIT_QUERY` is their ascending `minWidth`s, stable).
    pub fn new(fits: Vec<Fit>) -> Self {
        Self { fits, fit_index: 0 }
    }

    /// Select a candidate by its ascending `FIT_QUERY` index (default 0 — the
    /// smallest threshold / fallback).
    pub fn with_fit(mut self, index: usize) -> Self {
        self.fit_index = index;
        self
    }
}

impl View for SizeThatFits {
    fn build_env(&self, _env: &mut Environment) -> Node {
        let mut sorted: Vec<&Fit> = self.fits.iter().collect();
        sorted.sort_by(|a, b| {
            a.min_width
                .partial_cmp(&b.min_width)
                .unwrap_or(core::cmp::Ordering::Equal)
        });
        let thresholds: Vec<f32> = sorted.iter().map(|f| f.min_width).collect();
        let index = self.fit_index.min(sorted.len().saturating_sub(1));
        let mut node = plain_node(Component::SizeThatFits, Vec::new(), BTreeMap::new());
        node.list_properties
            .insert(property_id::FIT_QUERY, thresholds);
        if let Some(fit) = sorted.get(index) {
            node.children.push(fit.view.build_env(_env));
        }
        node
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
    fn text_with_configures_content() {
        let node = Text::with(|t| {
            t.text("hi");
        })
        .build();
        assert_eq!(node.component, Component::Text { text: "hi".into() });
    }

    #[test]
    fn modifiers_chain_and_apply_to_node() {
        let node = Text::new("hi")
            .modifiers((
                Padding(16.0),
                ForegroundStyle(Color::argb(0xFF_0000FF)),
                Background(Color::argb(0xFF_EEEEEE)),
            ))
            .build();
        assert_eq!(
            node.properties.get(&property_id::PADDING),
            Some(&16.0f32.to_bits())
        );
        assert_eq!(node.properties.get(&property_id::COLOR), Some(&0xFF_0000FF));
        assert_eq!(
            node.properties.get(&property_id::BACKGROUND_COLOR),
            Some(&0xFF_EEEEEE)
        );
    }

    #[test]
    fn single_modifier_needs_no_tuple() {
        let node = Text::new("x").modifiers(Padding(8.0)).build();
        assert_eq!(
            node.properties.get(&property_id::PADDING),
            Some(&8.0f32.to_bits())
        );
    }

    #[test]
    fn modules_apply_in_any_order() {
        // values, then modifiers, then children — and the reverse — are equal.
        let a = VStack::with(|v| {
            v.spacing(4.0);
        })
        .modifiers(Padding(8.0))
        .children(vec![Box::new(text("a"))])
        .build();
        let b = VStack::children(vec![Box::new(text("a"))])
            .with(|v| {
                v.spacing(4.0);
            })
            .modifiers(Padding(8.0))
            .build();
        assert_eq!(a, b);
    }

    #[test]
    fn color_token_refs_ride_design_token_properties() {
        let node = Text::new("hi")
            .modifiers((
                ForegroundStyle(Color::token("color.primary")),
                Background(Color::token("dark.color.surface")),
                Border::new(Color::token("color.border"), 1.0),
            ))
            .build();
        assert!(
            !node.properties.contains_key(&property_id::COLOR),
            "no literal"
        );
        assert_eq!(
            node.token_properties
                .get(&property_id::COLOR)
                .map(String::as_str),
            Some("color.primary")
        );
        assert_eq!(
            node.token_properties
                .get(&property_id::BACKGROUND_COLOR)
                .map(String::as_str),
            Some("dark.color.surface")
        );
        assert_eq!(
            node.token_properties
                .get(&property_id::BORDER_COLOR)
                .map(String::as_str),
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
            node.token_properties
                .get(&property_id::COLOR)
                .map(String::as_str),
            Some("color.primary")
        );
    }

    #[test]
    fn vstack_children_build_to_node_children() {
        let node = vstack![text("a"), text("b")]
            .with(|v| {
                v.spacing(4.0);
            })
            .modifiers(Padding(8.0))
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
        assert_eq!(
            node.children[0].component,
            Component::Text { text: "a".into() }
        );
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
    fn icon_builds_icon_component_with_canonical_name() {
        let node = icon(IconName::Play).build();
        assert_eq!(node.component, Component::Icon);
        assert_eq!(
            node.string_properties.get(&property_id::ICON_NAME),
            Some(&String::from("play"))
        );
    }

    #[test]
    fn icon_name_canonical_matches_catalog() {
        assert_eq!(IconName::Home.canonical(), "home");
        assert_eq!(IconName::SkipForward.canonical(), "skip-forward");
        assert_eq!(IconName::VolumeMute.canonical(), "volume-mute");
    }

    #[test]
    fn icon_size_and_tint_apply_like_text() {
        let node = icon(IconName::Search)
            .modifiers((FontSize(20.0), ForegroundStyle(Color::argb(0xFF_00_00_00))))
            .build();
        assert_eq!(
            node.properties.get(&property_id::FONT_SIZE),
            Some(&20.0f32.to_bits())
        );
        assert_eq!(
            node.properties.get(&property_id::COLOR),
            Some(&0xFF_00_00_00u32)
        );
    }

    #[test]
    fn any_modifier_applies_to_any_view() {
        // FontSize on a stack is "allowed and ignored" — property is emitted.
        let stack = vstack![].modifiers(FontSize(24.0)).build();
        assert_eq!(
            stack.properties.get(&property_id::FONT_SIZE),
            Some(&24.0f32.to_bits())
        );
    }

    #[test]
    fn font_predefined_typography_emits_text_style() {
        let node = text("T").modifiers(Font::headline()).build();
        assert_eq!(
            node.properties.get(&property_id::TEXT_STYLE),
            Some(&(TextStyle::Headline.code() as f32).to_bits())
        );
    }

    #[test]
    fn font_custom_emits_family_and_size() {
        let node = text("T").modifiers(Font::custom("Georgia", 20.0)).build();
        assert_eq!(
            node.string_properties.get(&property_id::FONT_FAMILY),
            Some(&String::from("Georgia"))
        );
        assert_eq!(
            node.properties.get(&property_id::FONT_SIZE),
            Some(&20.0f32.to_bits())
        );
        assert!(!node.properties.contains_key(&property_id::TEXT_STYLE));
    }

    #[test]
    fn font_custom_full_emits_every_axis() {
        let node = text("T")
            .modifiers(Font::custom_full("Georgia", 20.0, 700.0, FontDesign::Serif))
            .build();
        assert_eq!(
            node.string_properties.get(&property_id::FONT_FAMILY),
            Some(&String::from("Georgia"))
        );
        assert_eq!(
            node.properties.get(&property_id::FONT_SIZE),
            Some(&20.0f32.to_bits())
        );
        assert_eq!(
            node.properties.get(&property_id::FONT_WEIGHT),
            Some(&700.0f32.to_bits())
        );
        assert_eq!(
            node.properties.get(&property_id::FONT_DESIGN),
            Some(&(FontDesign::Serif.code() as f32).to_bits())
        );
    }

    #[test]
    fn font_family_and_design_modifiers() {
        let node = text("T")
            .modifiers((FontFamily("Inter"), FontDesign::Monospaced))
            .build();
        assert_eq!(
            node.string_properties.get(&property_id::FONT_FAMILY),
            Some(&String::from("Inter"))
        );
        assert_eq!(
            node.properties.get(&property_id::FONT_DESIGN),
            Some(&(FontDesign::Monospaced.code() as f32).to_bits())
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

        let text = text("A").modifiers(Card).build();
        assert_eq!(
            text.properties.get(&property_id::PADDING),
            Some(&16.0f32.to_bits())
        );
        assert_eq!(
            text.properties.get(&property_id::BACKGROUND_COLOR),
            Some(&0xFF_EEEEEE)
        );

        let stack = vstack![].modifiers(Card).build();
        assert_eq!(
            stack.properties.get(&property_id::BACKGROUND_COLOR),
            Some(&0xFF_EEEEEE)
        );
    }

    #[test]
    fn modifiers_apply_innermost_first() {
        let node = Text::new("x")
            .modifiers((Padding(4.0), Padding(8.0)))
            .build();
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
            .modifiers(Frame::new(
                Some(size::FILL),
                Some(24.0),
                Some(Align::Center),
            ))
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
        let node = text("x").modifiers(Frame::width(100.0)).build();
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
            .modifiers(Frame::new(
                Some(f32::INFINITY),
                Some(f32::INFINITY),
                Some(Align::Leading),
            ))
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
            .modifiers(Frame::new(Some(f32::NEG_INFINITY), Some(24.0), None))
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
        let node = text("x").modifiers(PointerEvents(mask)).build();
        assert_eq!(
            node.properties.get(&property_id::EVENT_LISTENERS),
            Some(&mask)
        );
    }

    #[test]
    fn tap_gesture_declares_listeners_and_stores_callback() {
        let node = text("x").modifiers(TapGesture::new(|| {})).build();
        assert_eq!(
            node.properties.get(&property_id::EVENT_LISTENERS),
            Some(&(pathland_core::listener::POINTER_DOWN | pathland_core::listener::POINTER_UP))
        );
        assert_eq!(node.gestures.len(), 1);
    }

    #[test]
    fn tap_gesture_works_on_any_view() {
        assert_eq!(
            vstack![]
                .modifiers(TapGesture::new(|| {}))
                .build()
                .gestures
                .len(),
            1
        );
        assert_eq!(
            text("a")
                .modifiers(TapGesture::new(|| {}))
                .build()
                .gestures
                .len(),
            1
        );
        assert_eq!(
            hstack![]
                .modifiers(TapGesture::new(|| {}))
                .build()
                .gestures
                .len(),
            1
        );
    }

    #[test]
    fn tap_gesture_ors_into_existing_listener_mask() {
        let node = text("x")
            .modifiers((
                PointerEvents(pathland_core::listener::POINTER_MOVE),
                TapGesture::new(|| {}),
            ))
            .build();
        assert_eq!(
            node.properties.get(&property_id::EVENT_LISTENERS),
            Some(
                &(pathland_core::listener::POINTER_MOVE
                    | pathland_core::listener::POINTER_DOWN
                    | pathland_core::listener::POINTER_UP)
            )
        );
    }

    #[test]
    fn collect_tap_handlers_maps_ids_to_callbacks() {
        let mut root = vstack![text("a").modifiers(TapGesture::new(|| {})), text("b")].build();
        assign_ids(&mut root, &mut 1);
        let mut map = BTreeMap::new();
        collect_tap_handlers(&root, &mut map);
        // root=1, "a"=2 (has tap), "b"=3 (no tap).
        assert!(map.contains_key(&2));
        assert!(!map.contains_key(&1));
        assert!(!map.contains_key(&3));
    }

    #[test]
    fn grid_config_emits_counts() {
        let node = Grid::with(|g| {
            g.columns(2.0).rows(3.0).spacing(4.0);
        })
        .children(vec![Box::new(text("a"))])
        .build();
        assert_eq!(node.component, Component::Grid);
        assert_eq!(
            node.properties.get(&property_id::GRID_COLUMNS),
            Some(&2.0f32.to_bits())
        );
        assert_eq!(
            node.properties.get(&property_id::GRID_ROWS),
            Some(&3.0f32.to_bits())
        );
    }

    #[test]
    fn image_source_sets_string_property() {
        let node = image("/a/b.png").build();
        assert_eq!(
            node.string_properties
                .get(&property_id::IMAGE_SOURCE)
                .map(String::as_str),
            Some("/a/b.png")
        );
    }

    #[test]
    fn text_binds_to_a_signal() {
        let engine = Engine::new();
        let label = engine.signal(String::from("hi"));
        let id = label.id();
        let node = Text::with(|t| {
            t.text_signal(label);
        })
        .build();
        assert_eq!(node.text_binding, Some(id));
    }

    #[test]
    fn property_binds_to_a_signal() {
        let engine = Engine::new();
        let size = engine.signal(20.0f32);
        let id = size.id();
        let node = Text::new("x").modifiers(FontSize(size)).build();
        assert_eq!(node.property_bindings.get(&property_id::FONT_SIZE), Some(&id));
    }

    #[test]
    fn container_spacing_accepts_a_signal() {
        let engine = Engine::new();
        let spacing = engine.signal(4.0f32);
        let id = spacing.id();
        let node = VStack::with(|s| {
            s.spacing(spacing);
        })
        .build();
        assert_eq!(node.property_bindings.get(&property_id::SPACING), Some(&id));
    }

    #[test]
    fn computed_binds_text() {
        let engine = Engine::new();
        let n = engine.signal(2.0f32);
        let nc = n.clone();
        let label = engine.computed(move || {
            String::from(if nc.get().unwrap() > 1.0 { "big" } else { "small" })
        });
        let id = label.id();
        let node = Text::with(|t| {
            t.text_signal(label);
        })
        .build();
        assert_eq!(node.text_binding, Some(id));
    }

    #[test]
    fn text_field_two_way_binding_writes_the_signal() {
        let engine = Engine::new();
        let name = engine.signal(String::from("initial"));
        let node = TextField::with(|t| {
            t.placeholder("Name");
            t.text(name.clone());
        })
        .build();
        assert_eq!(node.text_binding, Some(name.id()));
        assert!(node.properties.contains_key(&property_id::BINDING_ID));

        let mut handlers = InputHandlers::default();
        collect_input_handlers(&node, &mut handlers);
        (handlers.text.get(&0).unwrap().borrow_mut())("updated");
        assert_eq!(name.get(), Some(String::from("updated")));
    }

    #[test]
    fn toggle_two_way_binding_writes_the_signal() {
        let engine = Engine::new();
        let on = engine.signal(false);
        let node = Toggle::with(|t| {
            t.is_on(on.clone());
        })
        .build();
        assert_eq!(
            node.property_bindings.get(&property_id::SELECTED),
            Some(&on.id())
        );

        let mut handlers = InputHandlers::default();
        collect_input_handlers(&node, &mut handlers);
        (handlers.value.get(&0).unwrap().borrow_mut())(1.0);
        assert_eq!(on.get(), Some(true));
    }

    #[test]
    fn slider_two_way_binding_reads_initial_and_writes() {
        let engine = Engine::new();
        let value = engine.signal(5.0f32);
        let node = Slider::with(|s| {
            s.min(0.0);
            s.max(10.0);
            s.bind(value.clone());
        })
        .build();
        // The initial value comes from the signal.
        assert_eq!(
            node.properties.get(&property_id::VALUE),
            Some(&5.0f32.to_bits())
        );
        assert_eq!(
            node.property_bindings.get(&property_id::VALUE),
            Some(&value.id())
        );

        let mut handlers = InputHandlers::default();
        collect_input_handlers(&node, &mut handlers);
        (handlers.value.get(&0).unwrap().borrow_mut())(7.5);
        assert_eq!(value.get(), Some(7.5));
    }

    #[test]
    fn button_action_wires_a_tap() {
        let node = Button::with(|b| {
            b.label("Go");
            b.action(|| {});
        })
        .build();
        let listeners = node
            .properties
            .get(&property_id::EVENT_LISTENERS)
            .copied()
            .unwrap_or(0);
        assert!(listeners & pathland_core::listener::POINTER_DOWN != 0);
        let mut taps = BTreeMap::new();
        collect_tap_handlers(&node, &mut taps);
        assert!(taps.contains_key(&0));
    }

    #[test]
    fn default_button_style_keeps_the_native_path() {
        let node = Button::with(|b| {
            b.label("Go");
        })
        .build();
        assert_eq!(node.component, Component::Button { label: "Go".into() });
        assert!(node.children.is_empty(), "no content child by default");
    }

    #[test]
    fn button_style_supplies_the_content_child() {
        let node = Button::with(|b| {
            b.label("Go");
        })
        .button_style(BorderedButtonStyle)
        .build();
        // The style content is the button's single child (Composite Override).
        assert_eq!(node.children.len(), 1);
        assert_eq!(
            node.children[0].component,
            Component::Text { text: "Go".into() }
        );
        assert_eq!(
            node.children[0].properties.get(&property_id::BORDER_WIDTH),
            Some(&1.0f32.to_bits())
        );
    }

    #[test]
    fn custom_button_style_overrides_content() {
        struct BadgeStyle;
        impl ButtonStyle for BadgeStyle {
            fn make_body(&self, config: &ButtonConfig) -> Option<Box<dyn View>> {
                Some(Box::new(Text::with(|t| {
                    t.text(alloc::format!("[{}]", config.label));
                })))
            }
        }
        let node = Button::with(|b| {
            b.label("Go");
        })
        .button_style(BadgeStyle)
        .build();
        assert_eq!(
            node.children[0].component,
            Component::Text { text: "[Go]".into() }
        );
    }

    #[test]
    fn conditional_selects_branch() {
        let engine = Engine::new();
        let show = engine.signal(true);
        let cond = Conditional::when(show.as_readonly(), Text::new("A"), Text::new("B"));
        assert_eq!(cond.build().component, Component::Text { text: "A".into() });
        show.set(false);
        assert_eq!(cond.build().component, Component::Text { text: "B".into() });
    }

    #[test]
    fn route_table_captures_params() {
        let table = RouteTable::new()
            .route("/", |_| Box::new(Text::new("home")))
            .route("/users/:id", |p| {
                Box::new(Text::with(|t| {
                    t.text(alloc::format!("user {}", p.get("id").unwrap()));
                }))
            })
            .fallback(|_| Box::new(Text::new("404")));
        assert_eq!(
            table.build("/").build().component,
            Component::Text { text: "home".into() }
        );
        assert_eq!(
            table.build("/users/42").build().component,
            Component::Text { text: "user 42".into() }
        );
        assert_eq!(
            table.build("/nope").build().component,
            Component::Text { text: "404".into() }
        );
    }

    #[test]
    fn router_push_pop_navigate_replace() {
        let engine = Engine::new();
        let path = engine.signal(String::from("/"));
        let router = Router::new(path);
        assert_eq!(router.path(), "/");
        router.push("/a");
        assert_eq!(router.path(), "/a");
        assert_eq!(router.depth(), 1);
        router.push("/b");
        assert_eq!(router.depth(), 2);
        router.pop();
        assert_eq!(router.path(), "/a");
        router.navigate("/c");
        assert_eq!(router.path(), "/c");
        router.replace("/d");
        assert_eq!(router.path(), "/d");
        assert_eq!(router.depth(), 1, "replace leaves the back-stack");
    }

    #[test]
    fn navigation_container_builds_destination_and_route() {
        let engine = Engine::new();
        let path = engine.signal(String::from("/a"));
        let router = Rc::new(Router::new(path));
        let table = RouteTable::new()
            .route("/a", |_| Box::new(Text::new("A")))
            .route("/b", |_| Box::new(Text::new("B")));
        let container = NavigationContainer::new(router.clone(), table);
        let node = container.build();
        assert_eq!(
            node.string_properties.get(&property_id::ROUTE).map(String::as_str),
            Some("/a")
        );
        assert_eq!(node.children[0].component, Component::Text { text: "A".into() });

        router.push("/b");
        let node = container.build();
        assert_eq!(
            node.string_properties.get(&property_id::ROUTE).map(String::as_str),
            Some("/b")
        );
        assert_eq!(node.children[0].component, Component::Text { text: "B".into() });
        assert_eq!(
            node.properties.get(&property_id::NAV_DEPTH),
            Some(&1u32)
        );
    }

    #[test]
    fn navigation_link_pushes_on_tap() {
        let engine = Engine::new();
        let path = engine.signal(String::from("/"));
        let router = Rc::new(Router::new(path));
        let node = NavigationLink::of("Go", router.clone(), "/next").build();
        let mut taps = BTreeMap::new();
        collect_tap_handlers(&node, &mut taps);
        (taps.get(&0).unwrap().borrow_mut())();
        assert_eq!(router.path(), "/next");
        assert_eq!(router.depth(), 1);
    }

    #[test]
    fn static_and_chained_entries_agree() {
        let a = VStack::modifiers(Padding(4.0))
            .children(vec![Box::new(text("a"))])
            .with(|v| {
                v.spacing(2.0);
            })
            .build();
        let b = VStack::with(|v| {
            v.spacing(2.0);
        })
        .modifiers(Padding(4.0))
        .children(vec![Box::new(text("a"))])
        .build();
        assert_eq!(a, b);
    }
}

#[cfg(test)]
mod sizethatfits_tests {
    use super::*;

    #[test]
    fn fit_slot_builds_component_query_and_only_the_selected_child() {
        let view = SizeThatFits::new(vec![
            Fit::new(Text::new("wide"), 640.0),
            Fit::any(Text::new("compact")),
        ]);
        let mut root = view.build();
        let mut next = 1;
        pathland_engine::assign_ids(&mut root, &mut next);

        assert_eq!(root.component, Component::SizeThatFits);
        let query = root.list_properties.get(&property_id::FIT_QUERY).unwrap();
        assert_eq!(query.as_slice(), &[0.0, 640.0]);
        assert_eq!(root.children.len(), 1, "only the selected candidate builds");
    }

    #[test]
    fn with_fit_selects_the_requested_candidate() {
        let view = SizeThatFits::new(vec![
            Fit::new(Text::new("wide"), 640.0),
            Fit::any(Text::new("compact")),
        ])
        .with_fit(1);
        let node = view.build();
        assert_eq!(node.children.len(), 1);
        assert!(
            node.children[0].component
                == Component::Text {
                    text: "wide".into()
                }
        );
    }
}
