//! # pathland-render-html
//!
//! A Pathland renderer that maps opcode frames onto **declarative HTML**
//! elements. Like every Pathland renderer it is a pure function of the opcode
//! stream: it retains only its own decoded output tree (a cache), never
//! application state, and emits `WHAT` the UI is — never layout rects. Native
//! browser layout does the positioning.
//!
//! This is the "server-side render" / remote-projection target (Goal #15): the
//! same opcode stream that drives a native backend drives this HTML document.
//!
//! Frames are **self-contained**: `apply(opcodes, strings)` resolves `SET_TEXT`
//! by a *relative* offset into the frame's own string section (no mirrored
//! arena), and every rendered element carries a stable `data-pathland-id` so a
//! client can hydrate it and apply later deltas in place.

use std::collections::BTreeMap;

use pathland_core::{
    Opcode, border_edges, category, component_type, property_id, style, tokens::TokenValue, tree,
    value_type,
};

mod capi;
mod css;
mod debug;
pub mod role_spec;
pub mod token_spec;

/// A decoded node in the retained description.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Node {
    /// Protocol component type id (see `pathland_core::component_type`).
    pub component: u16,
    /// Text content set via `STYLE::SET_TEXT`, if any.
    pub text: Option<String>,
    /// Constraint/style properties (`propertyId → value`).
    pub properties: BTreeMap<u16, u32>,
    /// `STRING`-typed properties resolved at apply time (`propertyId → text`).
    pub strings: BTreeMap<u16, String>,
    /// `DESIGN_TOKEN`-typed properties (`propertyId → token path`).
    pub token_refs: BTreeMap<u16, String>,
    /// Date value from `STYLE::SET_DATE`: (days since epoch, millis of day).
    pub date: Option<(i32, u32)>,
    /// Child node ids in insertion order.
    pub children: Vec<u32>,
}

impl Node {
    fn new(component: u16) -> Self {
        Self {
            component,
            text: None,
            properties: BTreeMap::new(),
            strings: BTreeMap::new(),
            token_refs: BTreeMap::new(),
            date: None,
            children: Vec::new(),
        }
    }

    /// The `SELECTED` checked state, decoded from its `U8` wire value.
    fn checked(&self) -> bool {
        self.properties
            .get(&property_id::SELECTED)
            .copied()
            .unwrap_or(0)
            != 0
    }

    /// The decoded `ROLE` semantic code (0 = none when absent), from its `F32`
    /// wire value.
    fn role_code(&self) -> u8 {
        self.properties
            .get(&property_id::ROLE)
            .map(|b| f32::from_bits(*b) as u8)
            .unwrap_or(0)
    }

    /// The decoded `TEXT_STYLE` typography code (None when absent), from its
    /// `F32` wire value.
    fn text_style_code(&self) -> Option<u8> {
        self.properties
            .get(&property_id::TEXT_STYLE)
            .map(|b| f32::from_bits(*b) as u8)
    }

    /// An `F32` property value, or a default when absent.
    fn f32_property(&self, prop: u16, default: f32) -> f32 {
        self.properties
            .get(&prop)
            .copied()
            .map(f32::from_bits)
            .unwrap_or(default)
    }

    /// `CONTENT_MARGINS` (F32), if present.
    fn content_margins(&self) -> Option<f32> {
        self.properties
            .get(&property_id::CONTENT_MARGINS)
            .map(|bits| f32::from_bits(*bits))
    }

    /// A raw `U32` property value (no f32 reinterpretation), or a default.
    fn u32_property(&self, prop: u16, default: u32) -> u32 {
        self.properties.get(&prop).copied().unwrap_or(default)
    }

    /// If the property carries a `DESIGN_TOKEN` reference, its CSS expression
    /// (`var(--pl-…)` or `calc(var(--pl-space-base) * N)` for the generative
    /// `space.<N>` family). Resolved at render time — the browser resolves the
    /// variable against the token rules in the document head.
    fn token_ref(&self, prop: u16) -> Option<String> {
        self.token_refs.get(&prop).map(|path| resolve_token_ref(path))
    }

    /// Inline CSS for the node. Literal colors and string font-family, plus all
    /// **arbitrary-number (dp/point)** styling — spacing, padding, size, offset,
    /// rotation, scale, filters, opacity, border-radius, z-index — which render
    /// inline because Tailwind can only statically safelist finite enum classes
    /// (dp values are unbounded). 1 dp = 1 CSS px (a renderer interpretation).
    fn style_css(&self) -> String {
        let mut css = String::new();
        let f32p = |prop: u16| self.properties.get(&prop).map(|b| f32::from_bits(*b));
        let px = |v: f32| format!("{v}px");

        // Colors + font family.
        if let Some(v) = self.token_ref(property_id::COLOR) {
            css.push_str(&format!("color:{v};"));
        } else if let Some(bits) = self.properties.get(&property_id::COLOR) {
            css.push_str(&format!("color:{};", rgba(*bits)));
        }
        if let Some(v) = self.token_ref(property_id::BACKGROUND_COLOR) {
            css.push_str(&format!("background-color:{v};"));
        } else if let Some(bits) = self.properties.get(&property_id::BACKGROUND_COLOR) {
            css.push_str(&format!("background-color:{};", rgba(*bits)));
        }
        if let Some(family) = self.strings.get(&property_id::FONT_FAMILY) {
            css.push_str(&format!("font-family:'{}';", family.replace('\'', "\\'")));
        }

        // Layout / distance (dp → px).
        if let Some(v) = self.token_ref(property_id::SPACING) {
            css.push_str(&format!("gap:{v};"));
        } else if let Some(v) = f32p(property_id::SPACING) {
            if v > 0.0 {
                css.push_str(&format!("gap:{};", px(v)));
            }
        }
        if let Some(v) = self.token_ref(property_id::CONTENT_MARGINS) {
            css.push_str(&format!("padding:{v};"));
        } else if let Some(m) = self.content_margins() {
            if m > 0.0 {
                css.push_str(&format!("padding:{};", px(m)));
            }
        }
        if let Some(v) = self.token_ref(property_id::PADDING) {
            css.push_str(&format!("padding:{v};"));
        } else if let Some(v) = f32p(property_id::PADDING) {
            if v > 0.0 {
                css.push_str(&format!("padding:{};", px(v)));
            }
        }
        for (prop, side) in [
            (property_id::PADDING_TOP, "padding-top"),
            (property_id::PADDING_RIGHT, "padding-right"),
            (property_id::PADDING_BOTTOM, "padding-bottom"),
            (property_id::PADDING_LEFT, "padding-left"),
        ] {
            if let Some(v) = self.token_ref(prop) {
                css.push_str(&format!("{side}:{v};"));
            } else if let Some(v) = f32p(prop) {
                if v > 0.0 {
                    css.push_str(&format!("{side}:{};", px(v)));
                }
            }
        }
        // WIDTH/HEIGHT: fixed values inline as px; FILL (-1) → `100%` (expand to
        // the available space in a flex parent — SwiftUI `maxWidth/maxHeight:
        // .infinity`). HUG_CONTENT (-2) is left to the intrinsic size.
        if let Some(v) = self.token_ref(property_id::WIDTH) {
            css.push_str(&format!("width:{v};"));
        } else if let Some(v) = f32p(property_id::WIDTH) {
            if v != pathland_core::size::HUG_CONTENT {
                css.push_str(&format!("width:{};", size_css(v)));
            }
        }
        if let Some(v) = self.token_ref(property_id::HEIGHT) {
            css.push_str(&format!("height:{v};"));
        } else if let Some(v) = f32p(property_id::HEIGHT) {
            if v != pathland_core::size::HUG_CONTENT {
                css.push_str(&format!("height:{};", size_css(v)));
            }
        }
        if let (Some(x), Some(y)) = (f32p(property_id::OFFSET_X), f32p(property_id::OFFSET_Y)) {
            if x != 0.0 || y != 0.0 {
                css.push_str(&format!("transform:translate({}px, {}px);", x, y));
            }
        } else {
            if let Some(v) = f32p(property_id::POSITION_X) {
                if v != 0.0 {
                    css.push_str(&format!("left:{};", px(v)));
                }
            }
            if let Some(v) = f32p(property_id::POSITION_Y) {
                if v != 0.0 {
                    css.push_str(&format!("top:{};", px(v)));
                }
            }
        }

        // Typography (arbitrary size/weight inline).
        if let Some(v) = self.token_ref(property_id::FONT_SIZE) {
            css.push_str(&format!("font-size:{v};"));
        } else if let Some(v) = f32p(property_id::FONT_SIZE) {
            if v > 0.0 {
                css.push_str(&format!("font-size:{};", px(v)));
            }
        }
        if let Some(v) = self.token_ref(property_id::FONT_WEIGHT) {
            css.push_str(&format!("font-weight:{v};"));
        } else if let Some(v) = f32p(property_id::FONT_WEIGHT) {
            if v > 0.0 {
                css.push_str(&format!("font-weight:{v};"));
            }
        }
        if let Some(v) = self.token_ref(property_id::BORDER_RADIUS) {
            css.push_str(&format!("border-radius:{v};"));
        } else if let Some(v) = f32p(property_id::BORDER_RADIUS) {
            if v != 0.0 {
                css.push_str(&format!("border-radius:{};", px(v)));
            }
        }

        // Effects (filters, rotation, scale, shadow, opacity, z-index) inline.
        let mut filters: Vec<String> = Vec::new();
        if let Some(v) = f32p(property_id::BLUR_RADIUS) {
            filters.push(format!("blur({}px)", v));
        }
        if let Some(v) = f32p(property_id::SATURATION) {
            filters.push(format!("saturate({v})"));
        }
        if let Some(v) = f32p(property_id::CONTRAST) {
            filters.push(format!("contrast({v})"));
        }
        if let Some(v) = f32p(property_id::BRIGHTNESS) {
            filters.push(format!("brightness({v})"));
        }
        if let Some(v) = f32p(property_id::GRAYSCALE) {
            filters.push(format!("grayscale({v})"));
        }
        if let Some(v) = f32p(property_id::HUE_ROTATION) {
            filters.push(format!("hue-rotate({v}deg)"));
        }
        if !filters.is_empty() {
            css.push_str(&format!("filter:{};", filters.join(" ")));
        }
        if let Some(v) = f32p(property_id::ROTATION_DEGREES) {
            if v != 0.0 {
                css.push_str(&format!("transform:rotate({v}deg);"));
            }
        }
        if let Some(v) = f32p(property_id::SCALE) {
            if v != 1.0 {
                css.push_str(&format!("transform:scale({v});"));
            }
        }
        // Shadow (accumulate x/y/radius/color — simplified to radius+color inline).
        if let (Some(r), Some(color_bits)) = (
            f32p(property_id::SHADOW_RADIUS),
            self.properties.get(&property_id::SHADOW_COLOR).copied(),
        ) {
            if r != 0.0 {
                let x = f32p(property_id::SHADOW_X).unwrap_or(0.0);
                let y = f32p(property_id::SHADOW_Y).unwrap_or(0.0);
                let color = self
                    .token_ref(property_id::SHADOW_COLOR)
                    .unwrap_or_else(|| rgba(color_bits));
                css.push_str(&format!("box-shadow:{x}px {y}px {r}px {color};"));
            }
        }
        if let Some(v) = self.token_ref(property_id::OPACITY) {
            css.push_str(&format!("opacity:{v};"));
        } else if let Some(v) = f32p(property_id::OPACITY) {
            if v < 1.0 {
                css.push_str(&format!("opacity:{v};"));
            }
        }
        if let Some(v) = f32p(property_id::Z_INDEX) {
            css.push_str(&format!("z-index:{};", v as i32));
        }

        // Enum-derived properties (previously Tailwind classes) now inline.
        // ALIGNMENT cross-axis (Leading=0, Center=1, Trailing=2, Fill=3 → default stretch).
        if let Some(v) = f32p(property_id::ALIGNMENT) {
            css.push_str(&format!(
                "align-items:{};",
                match v as u8 {
                    0 => "flex-start",
                    1 => "center",
                    2 => "flex-end",
                    _ => "stretch",
                }
            ));
        }
        // TEXT_ALIGNMENT (Leading=0, Center=1, Trailing=2).
        if let Some(v) = f32p(property_id::TEXT_ALIGNMENT) {
            css.push_str(&format!(
                "text-align:{};",
                match (v as u8).min(2) {
                    0 => "left",
                    1 => "center",
                    _ => "right",
                }
            ));
        }
        // TEXT_CASE (None=0, Uppercase=1, Lowercase=2).
        if let Some(v) = f32p(property_id::TEXT_CASE) {
            css.push_str(&format!(
                "text-transform:{};",
                match v as u8 {
                    1 => "uppercase",
                    2 => "lowercase",
                    _ => "none",
                }
            ));
        }
        // VISIBLE=0 → hidden.
        if let Some(bits) = self.properties.get(&property_id::VISIBLE) {
            if *bits == 0 {
                css.push_str("display:none;");
            }
        }
        // FONT_STYLE italic.
        if let Some(v) = f32p(property_id::FONT_STYLE) {
            if v != 0.0 {
                css.push_str("font-style:italic;");
            }
        }
        // FONT_DESIGN (Default=0, Serif=1, Rounded=2, Monospaced=3).
        if let Some(v) = f32p(property_id::FONT_DESIGN) {
            css.push_str(&format!(
                "font-family:{};",
                match v as u8 {
                    1 => "Georgia, 'Times New Roman', serif",
                    2 => "ui-rounded, system-ui, sans-serif",
                    3 => "ui-monospace, monospace",
                    _ => "inherit",
                }
            ));
        }
        // UNDERLINE / STRIKETHROUGH.
        let mut decoration = Vec::new();
        if let Some(v) = f32p(property_id::UNDERLINE) {
            if v != 0.0 {
                decoration.push("underline");
            }
        }
        if let Some(v) = f32p(property_id::STRIKETHROUGH) {
            if v != 0.0 {
                decoration.push("line-through");
            }
        }
        if !decoration.is_empty() {
            css.push_str(&format!("text-decoration:{};", decoration.join(" ")));
        }
        // CLIPS_TO_BOUNDS → overflow hidden.
        if let Some(v) = f32p(property_id::CLIPS_TO_BOUNDS) {
            if v != 0.0 {
                css.push_str("overflow:hidden;");
            }
        }
        // ALLOWS_HIT_TESTING (inverted: absent = allowed).
        if let Some(v) = f32p(property_id::ALLOWS_HIT_TESTING) {
            if v == 0.0 {
                css.push_str("pointer-events:none;");
            }
        }
        // COLOR_INVERT.
        if let Some(v) = f32p(property_id::COLOR_INVERT) {
            if v != 0.0 {
                css.push_str("filter:invert(1);");
            }
        }
        // TRUNCATION_MODE → ellipsis.
        if self.properties.contains_key(&property_id::TRUNCATION_MODE) {
            css.push_str("text-overflow:ellipsis;overflow:hidden;white-space:nowrap;");
        }
        // CONTENT_MODE → object-fit (Fit=contain, Fill=cover); ASPECT_RATIO.
        if let Some(v) = f32p(property_id::CONTENT_MODE) {
            css.push_str(&format!(
                "object-fit:{};",
                if (v as u8) == 1 { "cover" } else { "contain" }
            ));
        }
        if let Some(v) = f32p(property_id::ASPECT_RATIO) {
            css.push_str(&format!("aspect-ratio:{v};"));
        }

        css
    }

    /// Border CSS, decoded from `BORDER_WIDTH`/`BORDER_COLOR`/`BORDER_RADIUS`/
    /// `BORDER_EDGES` (empty when the node has no border).
    fn border_style(&self) -> String {
        let width = match self.token_ref(property_id::BORDER_WIDTH) {
            Some(v) => v,
            None => {
                let Some(width_bits) = self.properties.get(&property_id::BORDER_WIDTH) else {
                    return String::new();
                };
                format!("{}px", f32::from_bits(*width_bits))
            }
        };
        let color = match self.token_ref(property_id::BORDER_COLOR) {
            Some(v) => v,
            None => rgba(
                self.properties
                    .get(&property_id::BORDER_COLOR)
                    .copied()
                    .unwrap_or(0xFF00_0000),
            ),
        };
        let edges = self
            .properties
            .get(&property_id::BORDER_EDGES)
            .copied()
            .unwrap_or(border_edges::ALL);

        let mut css = String::new();
        for (flag, side) in [
            (border_edges::TOP, "top"),
            (border_edges::LEADING, "left"),
            (border_edges::BOTTOM, "bottom"),
            (border_edges::TRAILING, "right"),
        ] {
            if edges & flag != 0 {
                css.push_str(&format!("border-{side}:{width} solid {color};"));
            }
        }
        css
    }
}

/// Unpack a `0xAARRGGBB` color into `(r, g, b, alpha)` where `alpha` is `0..1`.
fn unpack_color(argb: u32) -> (u32, u32, u32, f32) {
    let r = (argb >> 16) & 0xFF;
    let g = (argb >> 8) & 0xFF;
    let b = argb & 0xFF;
    let a = ((argb >> 24) & 0xFF) as f32 / 255.0;
    (r, g, b, a)
}

/// A `0xAARRGGBB` color as a CSS `rgba(r,g,b,a)` string.
fn rgba(argb: u32) -> String {
    let (r, g, b, a) = unpack_color(argb);
    format!("rgba({r},{g},{b},{a})")
}

/// A `WIDTH`/`HEIGHT` hint as CSS: `FILL` (-1) → `100%`, `HUG_CONTENT` (-2) →
/// `fit-content`, otherwise pixels.
fn size_css(v: f32) -> String {
    if v == pathland_core::size::FILL {
        "100%".to_string()
    } else if v == pathland_core::size::HUG_CONTENT {
        "fit-content".to_string()
    } else {
        format!("{v}px")
    }
}

/// The column count of a `GRID` from its `WIDTH` property (cell-axis count);
/// `FILL`/absent → auto-fit.
fn grid_columns(node: &Node) -> Option<u32> {
    let w = node.f32_property(property_id::WIDTH, -1.0);
    if w <= 0.0 {
        None
    } else {
        Some(w.round() as u32)
    }
}

/// Convert days since the Unix epoch (negative = before 1970) to a Gregorian
/// `(year, month, day)` using the civil-from-days algorithm.
fn days_to_date(days: i32) -> (i32, u32, u32) {
    let z = i64::from(days) + 719_468;
    let era = if z >= 0 { z } else { z - 146_096 } / 146_097;
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = if m <= 2 { y + 1 } else { y };
    (y as i32, m as u32, d as u32)
}

/// Millis of day → `(hours, minutes, seconds)`.
fn millis_to_hms(millis: u32) -> (u32, u32, u32) {
    let total = millis / 1000;
    (total / 3600, (total % 3600) / 60, total % 60)
}

/// Event-surfacing `data-*` attributes for a hydration client, from
/// `EVENT_LISTENERS` / `ACTION_ID` / `BINDING_ID`.
fn event_attrs(node: &Node) -> String {
    let mut attrs = String::new();
    if let Some(m) = node.properties.get(&property_id::EVENT_LISTENERS) {
        if *m != 0 {
            attrs.push_str(&format!(" data-event-listeners=\"{m}\""));
        }
    }
    if let Some(a) = node.properties.get(&property_id::ACTION_ID) {
        attrs.push_str(&format!(" data-action-id=\"{a}\""));
    }
    if let Some(b) = node.properties.get(&property_id::BINDING_ID) {
        attrs.push_str(&format!(" data-binding-id=\"{b}\""));
    }
    attrs
}

/// ARIA attributes from `ROLE` / `STATE` / `ENABLED`. The `ROLE` map is the
/// canonical `role_spec` (also generated into the DOM client), so a role
/// resolves to the same ARIA attribute on both renderers; roles that render as
/// a semantic element (or are conveyed by a native control element) emit no
/// attribute.
fn aria_attrs(node: &Node) -> String {
    let mut attrs = String::new();
    if let Some(bits) = node.properties.get(&property_id::ROLE) {
        let code = f32::from_bits(*bits) as u8;
        let kind = role_spec::shell_kind(node.component);
        if let Some(role) = role_spec::aria_role(kind, code) {
            attrs.push_str(&format!(" role=\"{role}\""));
        }
    }
    if let Some(bits) = node.properties.get(&property_id::STATE) {
        match f32::from_bits(*bits) as u8 {
            1 => attrs.push_str(" aria-disabled=\"true\""),
            3 => attrs.push_str(" aria-pressed=\"true\""),
            4 => attrs.push_str(" aria-selected=\"true\""),
            5 => attrs.push_str(" aria-expanded=\"true\""),
            6 => attrs.push_str(" aria-busy=\"true\""),
            _ => {}
        }
    }
    if let Some(bits) = node.properties.get(&property_id::ENABLED) {
        if *bits == 0 {
            attrs.push_str(" disabled");
        }
    }
    attrs
}

/// Decode a self-contained snapshot batch (opcodes + string section) into a
/// **transient** `id → Node` map. The map lives only for the duration of one
/// render call — the renderer retains nothing between calls.
fn decode(opcodes: &[Opcode], strings: &[u8]) -> (BTreeMap<u32, Node>, Tokens) {
    let mut nodes = BTreeMap::new();
    let mut tokens = Tokens::default();
    for op in opcodes {
        match op.category() {
            category::TREE => apply_tree(&mut nodes, op.command(), *op),
            category::STYLE => {
                if op.command() == style::SET_DESIGN_TOKEN {
                    // Global override: A = arenaRef (token path), B = valueType,
                    // C = value (for STRING, an arenaRef to the value string).
                    if let Some(path) = strings_str(strings, op.a()) {
                        let vt = (op.b() & 0xFF) as u8;
                        let value = if vt == value_type::STRING {
                            strings_str(strings, op.c())
                                .map(TokenValue::Str)
                                .unwrap_or(TokenValue::U32(0))
                        } else {
                            TokenValue::from_wire(vt, op.c()).unwrap_or(TokenValue::U32(op.c()))
                        };
                        tokens.set(&path, vt, value);
                    }
                } else {
                    apply_style(&mut nodes, op.command(), *op, strings);
                }
            }
            _ => {}
        }
    }
    (nodes, tokens)
}

/// Read a `[u32 len][utf8]` entry from the batch's string section at a relative offset.
fn strings_str(strings: &[u8], offset: u32) -> Option<String> {
    let offset = offset as usize;
    if offset + 4 > strings.len() {
        return None;
    }
    let len = u32::from_le_bytes(strings[offset..offset + 4].try_into().ok()?) as usize;
    let start = offset + 4;
    if start + len > strings.len() {
        return None;
    }
    std::str::from_utf8(&strings[start..start + len]).ok().map(str::to_owned)
}

// --- Design tokens (spec/TOKENS.md) ---

/// `SET_DESIGN_TOKEN` overrides collected from a snapshot batch. `base` holds
/// light tokens (keyed by full path); `dark` holds `dark.`-prefixed overrides
/// (keyed by the bare path). Values are resolved [`TokenValue`]s (STRING values
/// are resolved from the batch's string section at decode time).
#[derive(Debug, Default, Clone)]
struct Tokens {
    base: BTreeMap<String, (u8, TokenValue)>,
    dark: BTreeMap<String, (u8, TokenValue)>,
}

impl Tokens {
    fn set(&mut self, path: &str, value_type: u8, value: TokenValue) {
        if let Some(bare) = path.strip_prefix("dark.") {
            self.dark.insert(bare.to_string(), (value_type, value));
        } else {
            self.base.insert(path.to_string(), (value_type, value));
        }
    }

    /// Render the overrides as CSS rules: base tokens as `:root` rules, dark
    /// overrides inside `@media (prefers-color-scheme: dark)` — the browser
    /// resolves the scheme natively, so SSR needs no client scheme knowledge.
    fn css(&self) -> String {
        let mut css = String::new();
        for (path, (vt, value)) in &self.base {
            css.push_str(&format!(
                ":root{{{}:{};}}",
                token_to_css_var(path),
                token_css_value(path, *vt, value)
            ));
        }
        if !self.dark.is_empty() {
            let rules: String = self
                .dark
                .iter()
                .map(|(path, (vt, value))| {
                    format!(":root{{{}:{};}}", token_to_css_var(path), token_css_value(path, *vt, value))
                })
                .collect();
            css.push_str(&format!("@media (prefers-color-scheme: dark){{{rules}}}"));
        }
        css
    }
}

/// The canonical CSS custom property for a token path: `--pl-` prefix, `.` →
/// `-`. The `dark.` scheme prefix is stripped — the dark variant overrides the
/// same variable inside the media query (spec/TOKENS.md; the naming rules live
/// in [`token_spec`], which `pathland-ts-codegen` mirrors into the DOM client).
fn token_to_css_var(path: &str) -> String {
    let bare = path.strip_prefix(token_spec::DARK_PREFIX).unwrap_or(path);
    let name: String = bare
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() || c == '-' || c == '_' { c } else { '-' })
        .collect();
    format!("{}{name}", token_spec::VAR_PREFIX)
}

/// Token paths whose F32 values are CSS lengths (emitted with `px`).
fn is_length_token(path: &str) -> bool {
    for prefix in token_spec::LENGTH_PREFIXES {
        if path.starts_with(prefix) {
            return true;
        }
    }
    for (prefix, suffix) in token_spec::LENGTH_PREFIX_SUFFIX {
        if path.starts_with(prefix) && path.ends_with(suffix) {
            return true;
        }
    }
    if token_spec::LENGTH_EXACT.contains(&path) {
        return true;
    }
    path.starts_with("elevation.")
        && token_spec::LENGTH_ELEVATION_SUFFIXES
            .iter()
            .any(|s| path.ends_with(s))
}

/// Render a token override value as a CSS value string: `STRING` values are
/// single-quoted (escaped); `F32` length tokens append `px`.
fn token_css_value(path: &str, _value_type: u8, value: &TokenValue) -> String {
    match value {
        TokenValue::Str(s) => format!("'{}'", s.replace('\\', "\\\\").replace('\'', "\\'")),
        TokenValue::Color(argb) => rgba(*argb),
        TokenValue::F32(v) => {
            if is_length_token(path) {
                format!("{v}px")
            } else {
                format!("{v}")
            }
        }
        TokenValue::I32(v) => format!("{v}"),
        TokenValue::U32(v) => format!("{v}"),
        TokenValue::U8(v) => format!("{v}"),
    }
}

/// Resolve a `DESIGN_TOKEN` property reference to a CSS expression. The
/// generative `space.<N>` family resolves to `calc(var(--pl-space-base) * N)`;
/// every other token resolves to `var(--pl-<path>)`.
fn resolve_token_ref(path: &str) -> String {
    let bare = path.strip_prefix(token_spec::DARK_PREFIX).unwrap_or(path);
    if let Some(rest) = bare.strip_prefix(token_spec::SPACE_FAMILY) {
        if rest.parse::<f64>().is_ok() {
            return format!("calc(var(--pl-space-base) * {rest})");
        }
    }
    format!("var({})", token_to_css_var(bare))
}

fn apply_tree(nodes: &mut BTreeMap<u32, Node>, command: u8, op: Opcode) {
    match command {
        tree::CREATE_NODE => {
            let id = op.a();
            let component = op.b() as u16;
            nodes.insert(id, Node::new(component));
        }
        tree::DELETE_NODE => {
            nodes.remove(&op.a());
        }
        tree::INSERT_CHILD => {
            let (parent, child) = (op.a(), op.b());
            if let Some(node) = nodes.get_mut(&parent) {
                let index = op.c();
                if index == pathland_core::APPEND {
                    node.children.push(child);
                } else {
                    let index = (index as usize).min(node.children.len());
                    node.children.insert(index, child);
                }
            }
        }
        tree::REMOVE_CHILD => {
            let (parent, child) = (op.a(), op.b());
            if let Some(node) = nodes.get_mut(&parent) {
                node.children.retain(|&c| c != child);
            }
        }
        tree::MOVE_CHILD => {
            let (parent, child, index) = (op.a(), op.b(), op.c());
            if let Some(node) = nodes.get_mut(&parent) {
                node.children.retain(|&c| c != child);
                let index = (index as usize).min(node.children.len());
                node.children.insert(index, child);
            }
        }
        _ => {}
    }
}

fn apply_style(nodes: &mut BTreeMap<u32, Node>, command: u8, op: Opcode, strings: &[u8]) {
    match command {
        style::SET_TEXT => {
            if let Some(text) = strings_str(strings, op.b()) {
                if let Some(node) = nodes.get_mut(&op.a()) {
                    node.text = Some(text);
                }
            }
        }
        style::SET_PROPERTY => {
            let property = (op.b() & 0xFFFF) as u16;
            if let Some(node) = nodes.get_mut(&op.a()) {
                let vt = (op.b() >> 16) as u8;
                if vt == value_type::STRING {
                    if let Some(text) = strings_str(strings, op.c()) {
                        node.strings.insert(property, text);
                    }
                } else if vt == value_type::DESIGN_TOKEN {
                    // C = arenaRef to the token path; resolved at render time to
                    // `var(--pl-…)` / `calc(...)` (see `Node::token_ref`).
                    if let Some(path) = strings_str(strings, op.c()) {
                        node.token_refs.insert(property, path);
                    }
                } else {
                    node.properties.insert(property, op.c());
                }
            }
        }
        style::SET_DATE => {
            if let Some(node) = nodes.get_mut(&op.a()) {
                // B = days since epoch (I32), C = millis of day (U32).
                node.date = Some((op.b() as i32, op.c()));
            }
        }
        _ => {}
    }
}

/// A **stateless, streaming** HTML renderer. Each render call decodes a
/// self-contained snapshot batch into a transient map, walks it once, and
/// streams HTML text out — zero state retained across calls. A pure function
/// of the opcode stream (Renderer Statelessness).
#[derive(Debug, Clone, Copy)]
pub struct HtmlRenderer {
    /// When enabled, every rendered node is prefixed with an HTML comment
    /// describing its component type and the modifiers (style properties)
    /// applied to it (`<!-- #1 VStack: spacing=4, alignment=Fill -->`). A
    /// debugging aid — off by default so production SSR stays lean.
    debug_comments: bool,
}

impl HtmlRenderer {
    /// Create a renderer (stateless; the same value serves every render).
    #[must_use]
    pub fn new() -> Self {
        Self {
            debug_comments: false,
        }
    }

    /// Enable/disable debug comments. When on, each rendered node is prefixed
    /// with an HTML comment naming its component and the modifiers applied
    /// (see [`crate::debug`]).
    #[must_use]
    pub fn with_debug_comments(mut self, on: bool) -> Self {
        self.debug_comments = on;
        self
    }

    /// Whether debug comments are enabled.
    #[must_use]
    pub fn debug_comments(&self) -> bool {
        self.debug_comments
    }

    /// Render the subtree rooted at `root` as a full HTML document.
    #[must_use]
    pub fn render_document(&self, opcodes: &[Opcode], strings: &[u8], root: u32) -> String {
        let (nodes, tokens) = decode(opcodes, strings);
        let body = self.render_node(&nodes, root);
        // Token overrides render as their own `<style data-pathland-tokens>`
        // element AFTER the built-in block so their `:root` variables win the
        // cascade (same specificity, later wins) — matching the JS DOM client's
        // `style[data-pathland-tokens]` element. No overrides → no element.
        let token_rules = tokens.css();
        let token_style = if token_rules.is_empty() {
            String::new()
        } else {
            format!("<style data-pathland-tokens>{token_rules}</style>\n")
        };
        format!(
            "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n\
             <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n\
             <title>Pathland</title>\n\
             <link rel=\"preconnect\" href=\"https://rsms.me/\">\n\
             <link rel=\"stylesheet\" href=\"https://rsms.me/inter/inter.css\">\n\
             {}{}\n</head>\n<body>{body}</body>\n</html>\n",
            css::STYLE,
            token_style
        )
    }

    /// Render the subtree rooted at `root` as an HTML fragment (no `<html>`).
    /// `SET_DESIGN_TOKEN` overrides are applied to the document head by
    /// `render_document`; a fragment is assumed to be embedded in a document
    /// that already defines the token variables.
    #[must_use]
    pub fn render_fragment(&self, opcodes: &[Opcode], strings: &[u8], root: u32) -> String {
        let (nodes, _) = decode(opcodes, strings);
        self.render_node(&nodes, root)
    }

    fn render_node(&self, nodes: &BTreeMap<u32, Node>, id: u32) -> String {
        let Some(node) = nodes.get(&id) else {
            return String::new();
        };
        let children: String = node
            .children
            .iter()
            .map(|&child| self.render_node(nodes, child))
            .collect();
        let mut data_id = format!(" data-pathland-id=\"{id}\"");
        data_id.push_str(&slot_attrs(node));
        let css = format!("{}{}", node.style_css(), node.border_style());
        let style = style_attr(&css);
        let event = event_attrs(node);
        let aria = aria_attrs(node);
        // A semantic role may retag a generic div/span shell (role_spec);
        // control components keep their native element.
        let kind = role_spec::shell_kind(node.component);
        let semantic = role_spec::semantic_tag(kind, node.role_code());

        let element = match node.component {
            component_type::VSTACK => wrap_stack(id, "column", semantic, node, &children, &css, &event, &aria),
            component_type::HSTACK => wrap_stack(id, "row", semantic, node, &children, &css, &event, &aria),
            component_type::LAZY_VSTACK => {
                wrap_stack(id, "column", semantic, node, &children, &css, &event, &aria)
            }
            component_type::LAZY_HSTACK => {
                wrap_stack(id, "row", semantic, node, &children, &css, &event, &aria)
            }
            component_type::TEXT => {
                // A TEXT's tag is resolved by typography + role: a heading
                // `TEXT_STYLE` (LargeTitle…Headline) is always a heading; else
                // `ROLE_HEADER` → `<h2>`, `ROLE_PARAGRAPH` → `<p>`; else `<span>`.
                let tag = role_spec::text_tag(kind, node.role_code(), node.text_style_code())
                    .unwrap_or("span");
                let text = escape(node.text.as_deref().unwrap_or_default());
                format!("<{tag}{data_id}{event}{aria}{style}>{text}</{tag}>")
            }
            component_type::BUTTON => {
                // Composite Override Mode: children present → custom body.
                let body = if node.children.is_empty() {
                    escape(node.text.as_deref().unwrap_or_default())
                } else {
                    children
                };
                let button_class = class_attr("pathland-button");
                format!("<button{data_id}{event}{aria}{button_class}{style}>{body}</button>")
            }
            component_type::TOGGLE => {
                let toggle_style = node.f32_property(property_id::TOGGLE_STYLE, 0.0).round() as u8;
                let checked = if node.checked() { " checked" } else { "" };
                let body = if node.children.is_empty() {
                    format!(
                        "<span class=\"pathland-text\">{}</span>",
                        escape(node.text.as_deref().unwrap_or_default())
                    )
                } else {
                    format!("<div class=\"pathland-custom-body\">{children}</div>")
                };
                let toggle_class = class_attr("pathland-toggle");
                match toggle_style {
                    1 => format!(
                        "<label{data_id}{event}{aria}{toggle_class}{style}><input type=\"checkbox\"{checked}>{body}</label>"
                    ),
                    2 => {
                        let pressed = if node.checked() { "true" } else { "false" };
                        format!(
                            "<button{data_id}{event}{aria} type=\"button\" aria-pressed=\"{pressed}\"{style}>{body}</button>"
                        )
                    }
                    _ => format!(
                        "<label{data_id}{event}{aria}{toggle_class}{style}><input type=\"checkbox\" role=\"switch\"{checked}>{body}</label>"
                    ),
                }
            }
            component_type::SPACER => {
                let tag = semantic.unwrap_or("div");
                let combined = format!("flex:1;{css}");
                let spacer_style = style_attr(&combined);
                format!("<{tag}{data_id}{event}{aria}{spacer_style}></{tag}>")
            }
            component_type::SLIDER => {
                let min = node.f32_property(property_id::MIN_VALUE, 0.0);
                let max = node.f32_property(property_id::MAX_VALUE, 1.0);
                let value = node.f32_property(property_id::VALUE, min);
                let body = if node.children.is_empty() {
                    format!(
                        "<span class=\"pathland-text\">{}</span>",
                        escape(node.text.as_deref().unwrap_or_default())
                    )
                } else {
                    format!("<div class=\"pathland-custom-body\">{children}</div>")
                };
                let slider_class = class_attr("pathland-slider");
                format!(
                    "<label{data_id}{event}{aria}{slider_class}{style}><input type=\"range\" min=\"{min}\" max=\"{max}\" step=\"any\" value=\"{value}\">{body}</label>"
                )
            }
            component_type::TEXT_FIELD => {
                let value = escape(node.text.as_deref().unwrap_or_default());
                let label = escape(
                    node.strings
                        .get(&property_id::LABEL)
                        .map(String::as_str)
                        .unwrap_or_default(),
                );
                let prompt = escape(
                    node.strings
                        .get(&property_id::PROMPT)
                        .map(String::as_str)
                        .unwrap_or_default(),
                );
                let input_type = if node.f32_property(property_id::IS_SECURE, 0.0) != 0.0 {
                    "password"
                } else {
                    "text"
                };
                let textfield_class = class_attr("pathland-textfield");
                format!(
                    "<label{data_id}{event}{aria}{textfield_class}{style}><span class=\"pathland-label\">{label}</span><input class=\"pathland-input\" type=\"{input_type}\" value=\"{value}\" placeholder=\"{prompt}\"></label>"
                )
            }
            component_type::TEXT_EDITOR => {
                let value = escape(node.text.as_deref().unwrap_or_default());
                format!("<textarea{data_id}{event}{aria} class=\"pathland-input\" rows=\"4\"{style}>{value}</textarea>")
            }
            component_type::IMAGE => {
                let src = escape(
                    node.strings
                        .get(&property_id::IMAGE_SOURCE)
                        .map(String::as_str)
                        .unwrap_or_default(),
                );
                // The accessibility label (`LABEL` / `.accessibilityLabel`) is the
                // image's `alt` text (empty = decorative).
                let alt = escape(
                    node.strings
                        .get(&property_id::LABEL)
                        .map(String::as_str)
                        .unwrap_or_default(),
                );
                format!("<img{data_id}{event}{aria} src=\"{src}\" alt=\"{alt}\"{style}>")
            }
            component_type::AUDIO => {
                let src = escape(
                    node.strings
                        .get(&property_id::AUDIO_SOURCE)
                        .map(String::as_str)
                        .unwrap_or_default(),
                );
                // Playback interaction is renderer-native (`controls`).
                format!("<audio{data_id}{event}{aria} src=\"{src}\" controls{style}></audio>")
            }
            component_type::VIDEO => {
                let src = escape(
                    node.strings
                        .get(&property_id::VIDEO_SOURCE)
                        .map(String::as_str)
                        .unwrap_or_default(),
                );
                // Playback interaction is renderer-native (`controls`).
                format!("<video{data_id}{event}{aria} src=\"{src}\" controls{style}></video>")
            }
            component_type::COLOR => {
                let tag = semantic.unwrap_or("div");
                let color = node.u32_property(property_id::COLOR, 0xFF00_0000);
                // Layout-greedy (SwiftUI Color): expands to the available space
                // unless a size modifier constrains it.
                format!(
                    "<{tag}{data_id}{event}{aria} style=\"flex:1 1 auto;align-self:stretch;background-color:{};{css}\">{children}</{tag}>",
                    rgba(color)
                )
            }
            component_type::SHAPE => {
                let kind = node.f32_property(property_id::SHAPE_KIND, 0.0).round() as u8;
                let fill = node.u32_property(property_id::COLOR, 0xFF00_0000);
                let w = node.f32_property(property_id::WIDTH, 100.0);
                let h = node.f32_property(property_id::HEIGHT, 100.0);
                let base = format!(
                    "width:{};height:{};background-color:{};{css}",
                    size_css(w),
                    size_css(h),
                    rgba(fill)
                );
                // A semantic role retags the CSS-shape divs; the Path branch is an
                // `<svg>` and ignores the role.
                let tag = semantic.unwrap_or("div");
                match kind {
                    0 | 4 => format!("<{tag}{data_id}{event}{aria} style=\"{base}border-radius:50%;\"></{tag}>"),
                    2 => {
                        let r = node.f32_property(property_id::BORDER_RADIUS, 8.0);
                        format!("<{tag}{data_id}{event}{aria} style=\"{base}border-radius:{r}px;\"></{tag}>")
                    }
                    3 => format!("<{tag}{data_id}{event}{aria} style=\"{base}border-radius:9999px;\"></{tag}>"),
                    5 => format!(
                        "<svg{data_id}{event}{aria} width=\"100\" height=\"100\" viewBox=\"0 0 100 100\"><rect width=\"100\" height=\"100\" fill=\"{}\"/></svg>",
                        rgba(fill)
                    ),
                    _ => format!("<{tag}{data_id}{event}{aria} style=\"{base}\"></{tag}>"),
                }
            }
            component_type::DIVIDER => {
                let width = node.f32_property(property_id::BORDER_WIDTH, 1.0);
                let color = node
                    .properties
                    .get(&property_id::COLOR)
                    .copied()
                    .map(rgba)
                    .unwrap_or_else(|| "rgba(0,0,0,0.2)".to_string());
                format!(
                    "<div{data_id}{event}{aria} style=\"height:0;border-top:{width}px solid {color};\"></div>"
                )
            }
            component_type::PROGRESS_VIEW => {
                let indeterminate = node.f32_property(property_id::IS_INDETERMINATE, 0.0) != 0.0
                    || node.f32_property(property_id::PROGRESS, -1.0) < 0.0;
if indeterminate {
                    let spinner_class = class_attr("pathland-spinner");
                    format!("<div{data_id}{spinner_class}{style}></div>")
                } else {
                    let value = node.f32_property(property_id::PROGRESS, 0.0);
                    let max = node.f32_property(property_id::MAX_VALUE, 1.0);
                    format!(
                        "<progress{data_id}{event}{aria} value=\"{value}\" max=\"{max}\"{style}></progress>"
                    )
                }
            }
            component_type::GAUGE => {
                let min = node.f32_property(property_id::MIN_VALUE, 0.0);
                let max = node.f32_property(property_id::MAX_VALUE, 1.0);
                let value = node.f32_property(property_id::VALUE, min);
                let pct = if max > min {
                    ((value - min) / (max - min)).clamp(0.0, 1.0) * 100.0
                } else {
                    0.0
                };
                let gauge_class = class_attr("pathland-gauge");
                // The bounds ride as `data-min`/`data-max` so the DOM client can
                // recompute the percentage when VALUE changes (its gauge handler
                // reads them), mirroring the stepper's `.pathland-stepper-range`.
                format!(
                    "<div{data_id}{event}{aria}{gauge_class} data-min=\"{min}\" data-max=\"{max}\"{style}><div style=\"width:{pct}%\"></div></div>"
                )
            }
            component_type::GRID | component_type::LAZY_VGRID | component_type::LAZY_HGRID => {
                let tag = semantic.unwrap_or("div");
                let cols = grid_columns(node);
                let grid_css = grid_style(cols, node.component == component_type::LAZY_HGRID);
                let combined = format!("{grid_css}{css}");
                let grid_style = style_attr(&combined);
                format!("<{tag}{data_id}{event}{aria}{grid_style}>{children}</{tag}>")
            }
            component_type::SCROLLVIEW => {
                let tag = semantic.unwrap_or("div");
                let combined = format!("overflow:auto;{css}");
                let scroll_style = style_attr(&combined);
                format!("<{tag}{data_id}{event}{aria}{scroll_style}>{children}</{tag}>")
            }
            component_type::ZSTACK => {
                let tag = semantic.unwrap_or("div");
                let inner: String = node
                    .children
                    .iter()
                    .map(|&child| {
                        let child_html = self.render_node(nodes, child);
                        if child_html.is_empty() {
                            String::new()
                        } else {
                            format!("<div style=\"position:absolute;inset:0\">{child_html}</div>")
                        }
                    })
                    .collect();
                let combined = format!("position:relative;width:100%;height:100%;{css}");
                let zstyle = style_attr(&combined);
                format!("<{tag}{data_id}{event}{aria}{zstyle}>{inner}</{tag}>")
            }
            component_type::STEPPER => {
                let min = node.f32_property(property_id::MIN_VALUE, 0.0);
                let max = node.f32_property(property_id::MAX_VALUE, 100.0);
                let value = node.f32_property(property_id::VALUE, min);
                let step = node.f32_property(property_id::STEP_VALUE, 1.0);
                // Composite shell matching the DOM client's `stepperShell()`
                // (lib/typescript/src/elements.ts): the +/- buttons and a hidden
                // `.pathland-stepper-range` span carrying the bounds for the
                // client's click math. The value is displayed in the first span,
                // rounded to 2 decimals like the client.
                let display = format!("{}", (value * 100.0).round() / 100.0);
                let stepper_class = class_attr("pathland-stepper");
                format!(
                    "<div{data_id}{event}{aria}{stepper_class}{style}><button type=\"button\" data-step=\"-1\">−</button><span>{display}</span><button type=\"button\" data-step=\"1\">+</button><span class=\"pathland-stepper-range\" hidden data-min=\"{min}\" data-max=\"{max}\" data-step=\"{step}\"></span></div>"
                )
            }
            component_type::DATE_PICKER => {
                let mode = node.f32_property(property_id::DATE_PICKER_MODE, 0.0).round() as u8;
                let (t, value) = match node.date {
                    Some((days, millis)) => match mode {
                        1 => {
                            let (hh, mm, _) = millis_to_hms(millis);
                            ("time", format!("{hh:02}:{mm:02}"))
                        }
                        2 => {
                            let (y, m, d) = days_to_date(days);
                            let (hh, mm, _) = millis_to_hms(millis);
                            (
                                "datetime-local",
                                format!("{y:04}-{m:02}-{d:02}T{hh:02}:{mm:02}"),
                            )
                        }
                        _ => {
                            let (y, m, d) = days_to_date(days);
                            ("date", format!("{y:04}-{m:02}-{d:02}"))
                        }
                    },
                    None => ("date", String::new()),
                };
                format!("<input{data_id}{event}{aria} type=\"{t}\" value=\"{value}\"{style}>")
            }
            component_type::PICKER => {
                let selected = node.u32_property(property_id::SELECTION, 0);
                let options: String = node
                    .children
                    .iter()
                    .enumerate()
                    .map(|(i, &child)| {
                        let label = nodes
                            .get(&child)
                            .and_then(|n| n.text.clone())
                            .unwrap_or_default();
                        let sel = if i as u32 == selected { " selected" } else { "" };
                        // Each option carries the child node's id so the DOM
                        // client can hydrate/reconcile it against the child's
                        // own TREE/STYLE deltas.
                        format!(
                            "<option data-pathland-id=\"{child}\" value=\"{i}\"{sel}>{}</option>",
                            escape(&label)
                        )
                    })
                    .collect();
                format!("<select{data_id}{event}{aria}{style}>{options}</select>")
            }
            component_type::MENU => {
                // Composite shell matching the DOM client's `menuShell()`
                // (lib/typescript/src/elements.ts): the trigger holds the label,
                // and TREE children are routed into `.pathland-menu-items` (the
                // client's `childrenContainer`). `role="menu"` is the control's
                // intrinsic semantics (not a `ROLE` property).
                let label = escape(node.text.as_deref().unwrap_or_default());
                let menu_class = class_attr("pathland-menu");
                format!(
                    "<div{data_id}{event}{aria}{menu_class} role=\"menu\"{style}><div class=\"pathland-menu-trigger\">{label}</div><div class=\"pathland-menu-items\">{children}</div></div>"
                )
            }
            component_type::COLOR_PICKER => {
                let color = node.u32_property(property_id::COLOR_VALUE, 0xFF00_0000);
                let hex = format!(
                    "#{:02x}{:02x}{:02x}",
                    (color >> 16) & 0xFF,
                    (color >> 8) & 0xFF,
                    color & 0xFF
                );
                format!("<input{data_id}{event}{aria} type=\"color\" value=\"{hex}\"{style}>")
            }
            component_type::COMMENT => String::new(),
            _ => String::new(),
        };
        if self.debug_comments {
            format!("{}{}", debug::node_comment(id, node), element)
        } else {
            element
        }
    }
}

    fn wrap_stack(
        id: u32,
        direction: &str,
        semantic: Option<&'static str>,
        node: &Node,
        children: &str,
        css: &str,
        event: &str,
        aria: &str,
    ) -> String {
        let tag = semantic.unwrap_or("div");
        let alignment = node
            .properties
            .get(&property_id::ALIGNMENT)
            .map(|bits| f32::from_bits(*bits) as u8)
            .unwrap_or(3);
        let align = match alignment {
            0 => "flex-start",
            1 => "center",
            2 => "flex-end",
            _ => "stretch",
        };
        let combined = format!(
            "display:flex;flex-direction:{direction};align-items:{align};{css}"
        );
        let stack_style = style_attr(&combined);
        format!(
            "<{tag} data-pathland-id=\"{id}\"{}{event}{aria}{stack_style}>{children}</{tag}>",
            slot_attrs(node),
        )
    }

/// Inline grid CSS: `display:grid` + `grid-template-columns` from the column count.
fn grid_style(columns: Option<u32>, horizontal: bool) -> String {
    let mut css = String::from("display:grid;");
    if let Some(n) = columns {
        css.push_str(&format!("grid-template-columns:repeat({n},1fr);"));
    }
    if horizontal {
        css.push_str("grid-auto-flow:column;grid-auto-columns:1fr;");
    }
    css
}

/// A `style="…"` attribute for a non-empty CSS fragment.
fn style_attr(css: &str) -> String {
    if css.is_empty() {
        String::new()
    } else {
        format!(" style=\"{css}\"")
    }
}

/// A `class="…"` attribute for a non-empty Tailwind class list.
fn class_attr(classes: &str) -> String {
    let classes = classes.trim();
    if classes.is_empty() {
        String::new()
    } else {
        format!(" class=\"{classes}\"")
    }
}

/// Escape text for inclusion in an HTML body.
fn escape(input: &str) -> String {
    input
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&#39;")
}

/// Slot attributes for a navigation/conditional slot node: the current path as
/// `data-pathland-route` (the DOM client mirrors it into the URL), the
/// `data-pathland-transition` swap hint (`platform`/`fade`/`slide`/`scale`),
/// and the `data-pathland-nav-chrome` chrome mode (`custom` only — the default
/// is the renderer's own chrome, so it is omitted). Empty for ordinary nodes.
/// See `spec/DSL.md` §4.5 and `spec/MODIFIERS.md`.
fn slot_attrs(node: &Node) -> String {
    let mut attrs = String::new();
    if let Some(path) = node.strings.get(&property_id::ROUTE) {
        attrs.push_str(&format!(" data-pathland-route=\"{}\"", escape(path)));
    }
    let chrome = node.f32_property(property_id::NAV_CHROME, 0.0).round() as u8;
    if chrome == 1 {
        attrs.push_str(" data-pathland-nav-chrome=\"custom\"");
    }
    // Back-stack depth: lets the DOM client hydrate the renderer's default back
    // button (shown when a PlatformDefault slot is deeper than its root) from the
    // SSR HTML before any delta frame arrives.
    let depth = node.u32_property(property_id::NAV_DEPTH, 1).max(1);
    if depth > 1 {
        attrs.push_str(&format!(" data-pathland-depth=\"{depth}\""));
    }
    let transition = node.f32_property(property_id::TRANSITION, 0.0).round() as u8;
    if transition != 0 {
        let name = match transition {
            2 => "fade",
            3 => "slide",
            4 => "scale",
            _ => "platform",
        };
        attrs.push_str(&format!(" data-pathland-transition=\"{name}\""));
    }
    attrs
}

#[cfg(test)]
mod tests {
    use super::*;
    use pathland_core::Opcode;

    #[test]
    fn renders_vstack_of_text() {
        // Hand-craft a self-contained frame: VSTACK(1) with a TEXT(2) child.
        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 0, 1, pathland_core::APPEND));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, pathland_core::APPEND));
        strings.extend_from_slice(&(5u32).to_le_bytes());
        strings.extend_from_slice(b"Hello");
        opcodes.push(Opcode::new(category::STYLE, style::SET_TEXT, 0, 2, 0, 0));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);
        assert!(html.contains("<!DOCTYPE html>"));
        assert!(html.contains("flex-direction:column"), "stack inline flex");
        assert!(html.contains("<span data-pathland-id=\"2\">Hello</span>"));
        assert!(html.contains("data-pathland-id=\"1\""));
    }

    #[test]
    fn escapes_text() {
        assert_eq!(escape("<a & b>"), "&lt;a &amp; b&gt;");
    }

    #[test]
    fn renders_border() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(
            category::TREE,
            tree::CREATE_NODE,
            0,
            1,
            component_type::TEXT as u32,
            0,
        ));
        strings.extend_from_slice(&(5u32).to_le_bytes());
        strings.extend_from_slice(b"Hello");
        opcodes.push(Opcode::new(category::STYLE, style::SET_TEXT, 0, 1, 0, 0));
        // border: width 2, opaque red, all edges
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::BORDER_WIDTH as u32,
            2.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::COLOR as u32) << 16) | property_id::BORDER_COLOR as u32,
            0xFFFF_0000,
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::U32 as u32) << 16) | property_id::BORDER_EDGES as u32,
            border_edges::ALL,
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);

        assert!(html.contains("border-top:2px solid rgba(255,0,0,1)"));
        assert!(html.contains("border-left:2px solid rgba(255,0,0,1)"));
        assert!(html.contains("border-bottom:2px solid rgba(255,0,0,1)"));
        assert!(html.contains("border-right:2px solid rgba(255,0,0,1)"));
    }

    #[test]
    fn renders_toggle_switch_checked() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(
            category::TREE,
            tree::CREATE_NODE,
            0,
            1,
            component_type::TOGGLE as u32,
            0,
        ));
        strings.extend_from_slice(&(7u32).to_le_bytes());
        strings.extend_from_slice(b"Enabled");
        opcodes.push(Opcode::new(category::STYLE, style::SET_TEXT, 0, 1, 0, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::TOGGLE_STYLE as u32,
            0.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::U8 as u32) << 16) | property_id::SELECTED as u32,
            1,
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);

        assert!(html.contains("<input type=\"checkbox\" role=\"switch\" checked>"));
        assert!(html.contains("<span class=\"pathland-text\">Enabled</span>"));
    }

    #[test]
    fn renders_toggle_checkbox_unchecked() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(
            category::TREE,
            tree::CREATE_NODE,
            0,
            1,
            component_type::TOGGLE as u32,
            0,
        ));
        strings.extend_from_slice(&(5u32).to_le_bytes());
        strings.extend_from_slice(b"Email");
        opcodes.push(Opcode::new(category::STYLE, style::SET_TEXT, 0, 1, 0, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::TOGGLE_STYLE as u32,
            1.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::U8 as u32) << 16) | property_id::SELECTED as u32,
            0,
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);

        assert!(html.contains("<input type=\"checkbox\">"));
        assert!(!html.contains("<input type=\"checkbox\" role=\"switch\""));
        assert!(html.contains("<span class=\"pathland-text\">Email</span>"));
    }

    #[test]
    fn renders_toggle_button_pressed() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(
            category::TREE,
            tree::CREATE_NODE,
            0,
            1,
            component_type::TOGGLE as u32,
            0,
        ));
        strings.extend_from_slice(&(4u32).to_le_bytes());
        strings.extend_from_slice(b"Mute");
        opcodes.push(Opcode::new(category::STYLE, style::SET_TEXT, 0, 1, 0, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::TOGGLE_STYLE as u32,
            2.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::U8 as u32) << 16) | property_id::SELECTED as u32,
            1,
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);

        assert!(html.contains("<button") && html.contains("type=\"button\""));
        assert!(html.contains("aria-pressed=\"true\""));
        assert!(html.contains("<span class=\"pathland-text\">Mute</span>"));
        assert!(html.contains("</button>"));
    }

    #[test]
    fn renders_slider() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(
            category::TREE,
            tree::CREATE_NODE,
            0,
            1,
            component_type::SLIDER as u32,
            0,
        ));
        strings.extend_from_slice(&(6u32).to_le_bytes());
        strings.extend_from_slice(b"Volume");
        opcodes.push(Opcode::new(category::STYLE, style::SET_TEXT, 0, 1, 0, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::MIN_VALUE as u32,
            0.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::MAX_VALUE as u32,
            1.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::VALUE as u32,
            0.5f32.to_bits(),
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);

        assert!(html.contains(
            "<input type=\"range\" min=\"0\" max=\"1\" step=\"any\" value=\"0.5\">"
        ));
        assert!(html.contains("<span class=\"pathland-text\">Volume</span>"));
    }

    #[test]
    fn renders_text_field() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(
            category::TREE,
            tree::CREATE_NODE,
            0,
            1,
            component_type::TEXT_FIELD as u32,
            0,
        ));
        // value (node text)
        strings.extend_from_slice(&(3u32).to_le_bytes());
        strings.extend_from_slice(b"Bob");
        opcodes.push(Opcode::new(category::STYLE, style::SET_TEXT, 0, 1, 0, 0));
        // label (STRING property)
        strings.extend_from_slice(&(5u32).to_le_bytes());
        strings.extend_from_slice(b"Name:");
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::STRING as u32) << 16) | property_id::LABEL as u32,
            7, // relative offset of the label entry in the string section
        ));
        // prompt (STRING property)
        strings.extend_from_slice(&(5u32).to_le_bytes());
        strings.extend_from_slice(b"Enter");
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::STRING as u32) << 16) | property_id::PROMPT as u32,
            16, // relative offset of the prompt entry
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);

        assert!(html.contains("class=\"pathland-textfield\""));
        assert!(html.contains("<span class=\"pathland-label\">Name:</span>"));
        assert!(html.contains(
            "<input class=\"pathland-input\" type=\"text\" value=\"Bob\" placeholder=\"Enter\">"
        ));
    }

    #[test]
    fn applies_network_decoded_frame() {
        // The renderer consumes a self-contained opcode + string section directly
        // (the transport layer's encode/decode round-trip is covered by the
        // transport crate's own tests).
        use pathland_core_transport::encode_frame;

        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(
            category::TREE,
            tree::CREATE_NODE,
            0,
            1,
            component_type::TEXT as u32,
            0,
        ));
        strings.extend_from_slice(&(5u32).to_le_bytes());
        strings.extend_from_slice(b"Hello");
        opcodes.push(Opcode::new(category::STYLE, style::SET_TEXT, 0, 1, 0, 0));

        let bytes = encode_frame(&opcodes, &strings);
        assert!(bytes.len() > 16);

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);
        assert!(html.contains("<span data-pathland-id=\"1\">Hello</span>"));
    }

    #[test]
    fn days_to_date_maps_epoch_and_pre_epoch() {
        assert_eq!(days_to_date(0), (1970, 1, 1));
        assert_eq!(days_to_date(19_723), (2024, 1, 1));
        // Pre-1970 (negative days).
        assert_eq!(days_to_date(-1), (1969, 12, 31));
        assert_eq!(days_to_date(-365), (1969, 1, 1));
    }

    #[test]
    fn renders_image_with_source() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::IMAGE as u32, 0));
        strings.extend_from_slice(&(15u32).to_le_bytes());
        strings.extend_from_slice(b"assets/logo.png");
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::STRING as u32) << 16) | property_id::IMAGE_SOURCE as u32,
            0,
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);
        assert!(html.contains("<img data-pathland-id=\"1\" src=\"assets/logo.png\" alt=\"\">"));
    }

    #[test]
    fn renders_media_and_image_alt() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        // IMAGE with an accessibility label → alt; CONTENT_MODE Fill → cover.
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::IMAGE as u32, 0));
        strings.extend_from_slice(&(32u32).to_le_bytes());
        strings.extend_from_slice(b"/_pathland/assets/icons/home.svg");
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::STRING as u32) << 16) | property_id::IMAGE_SOURCE as u32,
            0,
        ));
        strings.extend_from_slice(&(4u32).to_le_bytes());
        strings.extend_from_slice(b"Home");
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::STRING as u32) << 16) | property_id::LABEL as u32,
            36,
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::CONTENT_MODE as u32,
            1f32.to_bits(),
        ));
        // VIDEO + AUDIO with renderer-native controls.
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::VIDEO as u32, 0));
        strings.extend_from_slice(&(30u32).to_le_bytes());
        strings.extend_from_slice(b"https://example.com/sample.mp4");
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            2,
            ((value_type::STRING as u32) << 16) | property_id::VIDEO_SOURCE as u32,
            44,
        ));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 3, component_type::AUDIO as u32, 0));
        strings.extend_from_slice(&(30u32).to_le_bytes());
        strings.extend_from_slice(b"https://example.com/sample.mp3");
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            3,
            ((value_type::STRING as u32) << 16) | property_id::AUDIO_SOURCE as u32,
            78,
        ));

        let renderer = HtmlRenderer::new();
        let img = renderer.render_fragment(&opcodes, &strings, 1);
        assert!(img.contains("alt=\"Home\""), "image alt from LABEL");
        assert!(img.contains("object-fit:cover;"), "ContentMode Fill -> cover");
        let video = renderer.render_fragment(&opcodes, &strings, 2);
        assert!(video.contains("<video data-pathland-id=\"2\" src=\"https://example.com/sample.mp4\" controls"), "video native controls");
        let audio = renderer.render_fragment(&opcodes, &strings, 3);
        assert!(audio.contains("<audio data-pathland-id=\"3\" src=\"https://example.com/sample.mp3\" controls"), "audio native controls");
    }

    #[test]
    fn renders_grid_with_columns() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::GRID as u32, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::WIDTH as u32,
            2.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, 0));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("grid-template-columns:repeat(2,1fr)"), "grid inline");
    }

    #[test]
    fn renders_scrollview_and_zstack_containers() {
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::SCROLLVIEW as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 3, component_type::ZSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 4, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 3, 4, 0));

        let renderer = HtmlRenderer::new();
        let scroll = renderer.render_document(&opcodes, &[], 1);
        assert!(scroll.contains("overflow:auto"), "scrollview inline");
        assert!(scroll.contains("<span data-pathland-id=\"2\"></span>"));
        let zstack = renderer.render_document(&opcodes, &[], 3);
        assert!(zstack.contains("position:relative"), "zstack inline");
        assert!(zstack.contains("position:absolute;inset:0"), "zstack child inline");
    }

    #[test]
    fn renders_progress_view_and_spinner() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::PROGRESS_VIEW as u32, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::PROGRESS as u32,
            0.5f32.to_bits(),
        ));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::PROGRESS_VIEW as u32, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            2,
            ((value_type::U8 as u32) << 16) | property_id::IS_INDETERMINATE as u32,
            1,
        ));

        let renderer = HtmlRenderer::new();
        assert!(renderer.render_document(&opcodes, &[], 1).contains("<progress"));
        assert!(renderer.render_document(&opcodes, &[], 2).contains("pathland-spinner"));
    }

    #[test]
    fn renders_date_picker_from_set_date() {
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::DATE_PICKER as u32, 0));
        // days=19723 → 2024-01-01; millis of day = 0.
        opcodes.push(Opcode::new(category::STYLE, style::SET_DATE, 0, 1, 19_723, 0));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("<input data-pathland-id=\"1\" type=\"date\" value=\"2024-01-01\">"));
    }

    #[test]
    fn renders_picker_from_child_options() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::PICKER as u32, 0));
        let mut strings = Vec::new();
        strings.extend_from_slice(&(3u32).to_le_bytes());
        strings.extend_from_slice(b"Red");
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::STYLE, style::SET_TEXT, 0, 2, 0, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::U32 as u32) << 16) | property_id::SELECTION as u32,
            0,
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);
        assert!(html.contains("<select"));
        assert!(html.contains("<option data-pathland-id=\"2\" value=\"0\" selected>Red</option>"));
    }

    #[test]
    fn renders_color_picker_and_color_node() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::COLOR_PICKER as u32, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::COLOR as u32) << 16) | property_id::COLOR_VALUE as u32,
            0xFF00_00FF,
        ));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::COLOR as u32, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            2,
            ((value_type::COLOR as u32) << 16) | property_id::COLOR as u32,
            0xFF00_00FF,
        ));

        let renderer = HtmlRenderer::new();
        assert!(renderer.render_document(&opcodes, &[], 1).contains("<input data-pathland-id=\"1\" type=\"color\" value=\"#0000ff\">"));
        assert!(renderer.render_document(&opcodes, &[], 2).contains("background-color:rgba(0,0,255,1)"));
    }

    #[test]
    fn button_composite_renders_custom_body() {
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::BUTTON as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, 0));
        let mut strings = Vec::new();
        strings.extend_from_slice(&(1u32).to_le_bytes());
        strings.extend_from_slice(b"A");
        opcodes.push(Opcode::new(category::STYLE, style::SET_TEXT, 0, 2, 0, 0));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);
        assert!(html.contains("<button data-pathland-id=\"1\" class=\"pathland-button\">"), "button uses the design-system class");
        assert!(html.contains("<span data-pathland-id=\"2\">A</span>"));
    }

    #[test]
    fn event_attrs_and_aria_are_emitted() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::BUTTON as u32, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::U32 as u32) << 16) | property_id::EVENT_LISTENERS as u32,
            pathland_core::listener::POINTER_DOWN | pathland_core::listener::POINTER_UP,
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::U32 as u32) << 16) | property_id::ACTION_ID as u32,
            42,
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::U8 as u32) << 16) | property_id::ENABLED as u32,
            0,
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("data-event-listeners=\"5\""));
        assert!(html.contains("data-action-id=\"42\""));
        assert!(html.contains(" disabled"));
    }

    #[test]
    fn alignment_maps_to_flex_cross_axis() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::ALIGNMENT as u32,
            0.0f32.to_bits(), // Leading
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("flex-direction:column"), "stack inline flex"); assert!(html.contains("align-items:flex-start"), "alignment inline");
    }

    #[test]
    fn text_field_secure_uses_password_input() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::TEXT_FIELD as u32, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::U8 as u32) << 16) | property_id::IS_SECURE as u32,
            1,
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("<input class=\"pathland-input\" type=\"password\""));
    }

    #[test]
    fn style_css_applies_color_font_and_visibility() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::COLOR as u32) << 16) | property_id::COLOR as u32,
            0xFF11_2233,
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::FONT_SIZE as u32,
            18.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::U8 as u32) << 16) | property_id::VISIBLE as u32,
            0,
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("color:rgba(17,34,51,1)"));
        assert!(html.contains("font-size:18px"), "font-size inline (dp): {}", html);
        assert!(html.contains("hidden"), "visible=0 -> hidden class");
    }

    #[test]
    fn style_css_expands_fill_hints_to_percent() {
        use pathland_core::size;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::WIDTH as u32,
            size::FILL.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::HEIGHT as u32,
            size::FILL.to_bits(),
        ));

        // A FILL hint expands to the available space (`100%`); a fixed value
        // stays in px (see `size_css`), and the HUG_CONTENT sentinel is left
        // intrinsic.
        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("width:100%;height:100%;"), "FILL expands: {}", html);
    }

    #[test]
    fn debug_comments_are_opt_in() {
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::SPACING as u32,
            4.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::ALIGNMENT as u32,
            3.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, 0));

        // Default renderer: no comments.
        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(!html.contains("<!--"), "comments off by default: {html}");

        // with_debug_comments(true): a comment before each node naming its
        // component and the modifiers applied (sentinels + enums decoded).
        let renderer = HtmlRenderer::new().with_debug_comments(true);
        assert!(renderer.debug_comments());
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(
            html.contains("<!-- #1 VStack: spacing=4, alignment=Fill -->"),
            "{html}"
        );
        assert!(html.contains("<!-- #2 Text -->"), "{html}");
    }

    #[test]
    fn builtin_css_block_contains_reset_tokens_and_components() {
        let css = css::STYLE;
        // Preflight reset.
        assert!(css.contains("box-sizing: border-box"), "preflight reset present");
        // Design tokens on :root (canonical spec paths → --pl-* vars).
        assert!(css.contains("--pl-color-primary"), "primary token");
        assert!(css.contains("--pl-color-background"), "background token");
        assert!(css.contains("--pl-color-surface"), "surface token");
        assert!(css.contains("--pl-color-text-primary"), "text token");
        assert!(css.contains("--pl-color-text-secondary"), "muted/secondary text token");
        assert!(css.contains("--pl-color-border"), "border token");
        assert!(css.contains("--pl-font-sans"), "font token");
        // Component defaults.
        assert!(css.contains(".pathland-button"), "button component");
        assert!(css.contains(".pathland-toggle"), "toggle component");
        assert!(css.contains(".pathland-spinner"), "spinner component");
        assert!(css.contains(".pathland-gauge"), "gauge component");
        // Dark mode media query.
        assert!(css.contains("prefers-color-scheme: dark"), "dark mode");
        // Inter font loading (rsms.me CDN) + variable-font enhancement.
        assert!(css.contains("font-feature-settings: 'liga' 1, 'calt' 1"), "Chrome ligature fix");
        assert!(css.contains("font-variation-settings: normal"), "InterVariable enhancement");
        assert!(css.contains("'InterVariable', 'Inter'"), "variable font preferred when supported");
        // Document background + input field styling (Tailwind-style inset outline).
        assert!(css.contains("--pl-color-background"), "page background token");
        assert!(css.contains("color-scheme: light dark"), "native form controls follow the theme");
        assert!(css.contains(".pathland-input"), "input component");
        assert!(css.contains("--pl-input-border"), "input border token");
        assert!(css.contains("outline: 1px solid var(--pl-input-border)"), "inset outline instead of border");
        assert!(css.contains("outline-offset: -1px"), "inset ring");
        assert!(css.contains("--pl-input-focus"), "input focus token");
    }

    #[test]
    fn builtin_css_covers_tier1_default_tokens() {
        let css = css::STYLE;
        // Generative spacing base — without it `space.<N>` refs resolve to an
        // undefined variable unless the app overrides `space.base`.
        assert!(css.contains("--pl-space-base"), "space.base token");
        assert_eq!(count(css, "--pl-space-base"), 2, "space.base has a light + dark default");
        // Canonical typography tokens (spec/TOKENS.md font.body.*).
        for var in [
            "--pl-font-body-size",
            "--pl-font-body-weight",
            "--pl-font-body-family",
        ] {
            assert!(css.contains(var), "missing {var}");
        }
        // Canonical control/button/input tokens so app overrides retheme components.
        for var in [
            "--pl-control-background",
            "--pl-control-foreground",
            "--pl-control-border",
            "--pl-control-border-width",
            "--pl-control-radius",
            "--pl-control-height",
            "--pl-control-accent",
            "--pl-button-background",
            "--pl-button-background-hover",
            "--pl-button-foreground",
            "--pl-button-border",
            "--pl-button-radius",
            "--pl-button-padding",
            "--pl-button-font-size",
            "--pl-button-font-weight",
            "--pl-input-background",
            "--pl-input-foreground",
            "--pl-input-placeholder",
            "--pl-input-border",
            "--pl-input-radius",
            "--pl-input-font-size",
        ] {
            assert!(css.contains(var), "missing canonical token {var}");
        }
        // Radius scale + hairline border width (Tier 1).
        for var in [
            "--pl-radius-xs",
            "--pl-radius-sm",
            "--pl-radius-md",
            "--pl-radius-lg",
            "--pl-radius-xl",
            "--pl-radius-full",
            "--pl-border-width-thin",
        ] {
            assert!(css.contains(var), "missing {var}");
        }
        // Elevation (composite shadows) — the shadow-sm replacement.
        for var in [
            "--pl-elevation-low-color",
            "--pl-elevation-low-x",
            "--pl-elevation-low-y",
            "--pl-elevation-low-blur",
            "--pl-elevation-high-color",
        ] {
            assert!(css.contains(var), "missing {var}");
        }
        assert!(!css.contains("--pl-color-focus-ring"), "focus ring now maps to control.accent");
        assert!(!css.contains("--pl-button-bg"), "internal button vars mapped onto button.*");
        assert!(!css.contains("--pl-shadow-sm"), "shadow-sm mapped onto elevation.low.*");
    }

    #[test]
    fn builtin_css_canonical_rules_reference_the_catalog() {
        let css = css::STYLE;
        assert!(css.contains("border-radius: var(--pl-button-radius)"), "button radius from button.*");
        assert!(css.contains("background-color: var(--pl-button-background)"), "button bg from button.*");
        assert!(css.contains("color: var(--pl-button-foreground)"), "button fg from button.*");
        assert!(css.contains("background-color: var(--pl-input-background)"), "input bg from input.*");
        assert!(css.contains("color: var(--pl-input-foreground)"), "input fg from input.*");
        assert!(css.contains("outline: 2px solid var(--pl-control-accent)"), "focus ring uses control.accent");
        assert!(css.contains("var(--pl-elevation-low-x)"), "button shadow uses elevation.low.*");
        assert!(css.contains("var(--pl-elevation-high-x)"), "menu shadow uses elevation.high.*");
    }

    fn count(haystack: &str, needle: &str) -> usize {
        haystack.matches(needle).count()
    }

    #[test]
    fn document_head_loads_inter_from_the_rsms_cdn() {
        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&[], &[], 0);
        assert!(html.contains("<link rel=\"preconnect\" href=\"https://rsms.me/\">"), "preconnect to the font CDN");
        assert!(html.contains("<link rel=\"stylesheet\" href=\"https://rsms.me/inter/inter.css\">"), "Inter stylesheet link");
        // The font links must come before the built-in <style> block.
        let preconnect = html.find("rsms.me/inter/inter.css").unwrap();
        let style = html.find("<style>").unwrap();
        assert!(preconnect < style, "Inter CSS loads before the design-system style block");
    }

    #[test]
    fn renders_stack_with_gap_and_structural_child_inline() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::SPACING as u32,
            12.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, 0));
        opcodes.push(Opcode::new(
            category::STYLE,
            style::SET_PROPERTY,
            0,
            2,
            ((value_type::F32 as u32) << 16) | property_id::PADDING as u32,
            8.0f32.to_bits(),
        ));
        strings.extend_from_slice(&(2u32).to_le_bytes());
        strings.extend_from_slice(b"Hi");
        opcodes.push(Opcode::new(category::STYLE, style::SET_TEXT, 0, 2, 0, 0));

        let html = HtmlRenderer::new().render_document(&opcodes, &strings, 1);
        assert!(html.contains("flex-direction:column"), "stack inline flex");
        assert!(html.contains("gap:12px"), "spacing inline (dp): {}", html);
        assert!(html.contains("padding:8px"), "padding inline (dp): {}", html);
    }

    #[test]
    fn renders_decorative_typography_inline_and_enum_class() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::TEXT as u32, 0));
        for (prop, vt, value) in [
            (property_id::FONT_SIZE, value_type::F32, 18.0f32.to_bits()),
            (property_id::FONT_WEIGHT, value_type::F32, 700.0f32.to_bits()),
            (property_id::TEXT_ALIGNMENT, value_type::F32, 1.0f32.to_bits()),
            (property_id::BORDER_RADIUS, value_type::F32, 8.0f32.to_bits()),
            (property_id::BLUR_RADIUS, value_type::F32, 3.0f32.to_bits()),
            (property_id::ROTATION_DEGREES, value_type::F32, 90.0f32.to_bits()),
        ] {
            opcodes.push(Opcode::new(
                category::STYLE,
                style::SET_PROPERTY,
                0,
                1,
                ((vt as u32) << 16) | prop as u32,
                value,
            ));
        }
        strings.extend_from_slice(&(2u32).to_le_bytes());
        strings.extend_from_slice(b"Hi");
        opcodes.push(Opcode::new(category::STYLE, style::SET_TEXT, 0, 1, 0, 0));

        let html = HtmlRenderer::new().render_document(&opcodes, &strings, 1);
        // Arbitrary numbers inline; enum-derived stays a Tailwind class.
        assert!(html.contains("font-size:18px"), "font-size inline: {}", html);
        assert!(html.contains("font-weight:700"), "font-weight inline");
        assert!(html.contains("border-radius:8px"), "border-radius inline");
        assert!(html.contains("blur(3px)"), "blur inline filter");
        assert!(html.contains("rotate(90deg)"), "rotation inline transform");
        assert!(html.contains("text-align:center"), "text-align inline");
    }

    fn string_section(entries: &[&str]) -> Vec<u8> {
        let mut v = Vec::new();
        for s in entries {
            v.extend_from_slice(&(s.len() as u32).to_le_bytes());
            v.extend_from_slice(s.as_bytes());
        }
        v
    }

    #[test]
    fn set_design_token_emits_base_and_dark_override_css() {
        let strings = string_section(&["color.primary", "dark.color.primary"]);
        let dark_offset = (4 + 13) as u32; // entry 0: [len=13]"color.primary"
        let opcodes = vec![
            Opcode::new(category::STYLE, style::SET_DESIGN_TOKEN, 0, 0, value_type::COLOR as u32, 0xFF_2563EB),
            Opcode::new(
                category::STYLE,
                style::SET_DESIGN_TOKEN,
                0,
                dark_offset,
                value_type::COLOR as u32,
                0xFF_60A5FA,
            ),
        ];
        let html = HtmlRenderer::new().render_document(&opcodes, &strings, 1);
        assert!(html.contains(":root{--pl-color-primary:rgba(37,99,235,1);}"), "base override: {html}");
        assert!(
            html.contains("@media (prefers-color-scheme: dark){:root{--pl-color-primary:rgba(96,165,250,1);}}"),
            "dark override scoped in media query: {html}"
        );
    }

    #[test]
    fn design_token_css_renders_inside_a_style_element() {
        // The token overrides must render as their own <style data-pathland-tokens>
        // element in <head> — NOT as bare rules after the built-in </style>.
        let strings = string_section(&["color.primary", "dark.color.primary"]);
        let dark_offset = (4 + 13) as u32; // entry 0: [len=13]"color.primary"
        let opcodes = vec![
            Opcode::new(category::STYLE, style::SET_DESIGN_TOKEN, 0, 0, value_type::COLOR as u32, 0xFF_2563EB),
            Opcode::new(
                category::STYLE,
                style::SET_DESIGN_TOKEN,
                0,
                dark_offset,
                value_type::COLOR as u32,
                0xFF_60A5FA,
            ),
        ];
        let html = HtmlRenderer::new().render_document(&opcodes, &strings, 1);

        // The rules sit inside a dedicated style element, after the built-in block.
        assert!(
            html.contains("<style data-pathland-tokens>:root{--pl-color-primary:rgba(37,99,235,1);}"),
            "base rule inside the token style element: {html}"
        );
        assert!(
            html.contains("rgba(96,165,250,1);}}</style>"),
            "token style element is closed after the dark media query: {html}"
        );
        assert!(
            !html.contains("</style>:root") && !html.contains("</style>@media"),
            "no bare token rules outside a style element: {html}"
        );

        // No overrides → no token style element at all.
        let plain = HtmlRenderer::new().render_document(&[], &[], 0);
        assert!(
            !plain.contains("data-pathland-tokens"),
            "no token style element without overrides: {plain}"
        );
    }

    #[test]
    fn set_design_token_registers_a_length_token_with_px() {
        let strings = string_section(&["space.base"]);
        let opcodes = vec![Opcode::new(
            category::STYLE,
            style::SET_DESIGN_TOKEN,
            0,
            0,
            value_type::F32 as u32,
            4.0f32.to_bits(),
        )];
        let html = HtmlRenderer::new().render_document(&opcodes, &strings, 1);
        assert!(html.contains(":root{--pl-space-base:4px;}"), "length token px: {html}");
    }

    #[test]
    fn string_valued_design_token_emits_quoted_css() {
        // Path "font.body.family" (16 chars → entry = 20 bytes) at offset 0,
        // value "Inter" at offset 20 (conformance vector 20 wire shape).
        let strings = string_section(&["font.body.family", "Inter"]);
        let opcodes = vec![Opcode::new(
            category::STYLE,
            style::SET_DESIGN_TOKEN,
            0,
            0,
            value_type::STRING as u32,
            20,
        )];
        let html = HtmlRenderer::new().render_document(&opcodes, &strings, 1);
        assert!(html.contains(":root{--pl-font-body-family:'Inter';}"), "string token: {html}");
    }

    #[test]
    fn design_token_property_ref_renders_var() {
        let strings = string_section(&["color.primary"]);
        let opcodes = vec![
            Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::TEXT as u32, 0),
            Opcode::new(
                category::STYLE,
                style::SET_PROPERTY,
                0,
                1,
                ((value_type::DESIGN_TOKEN as u32) << 16) | property_id::COLOR as u32,
                0,
            ),
        ];
        let html = HtmlRenderer::new().render_fragment(&opcodes, &strings, 1);
        assert!(html.contains("color:var(--pl-color-primary);"), "token ref: {html}");
    }

    #[test]
    fn generative_space_ref_renders_calc() {
        let strings = string_section(&["space.2"]);
        let opcodes = vec![
            Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0),
            Opcode::new(
                category::STYLE,
                style::SET_PROPERTY,
                0,
                1,
                ((value_type::DESIGN_TOKEN as u32) << 16) | property_id::SPACING as u32,
                0,
            ),
        ];
        let html = HtmlRenderer::new().render_fragment(&opcodes, &strings, 1);
        assert!(
            html.contains("gap:calc(var(--pl-space-base) * 2);"),
            "generative space ref: {html}"
        );
    }
}
