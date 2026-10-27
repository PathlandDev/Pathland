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
    Opcode, border_edges, category, color_scheme_mode, component_type, meta, parameter,
    property_id, size, tokens::Scheme, tokens::TokenValue, tree, value_type,
};

mod capi;
mod css;
mod debug;
pub mod icons;
pub mod role_spec;
pub mod token_spec;

/// The `MEDIA_CONTROLLER` shell's inline style (spec/MEDIA.md). A media
/// controller owns playback but renders nothing: the container is **off-flow**
/// (`position:absolute`, 1×1, transparent) so it contributes no spacing while
/// still being *rendered* — browsers refuse to route a `<video>`'s **audio** to
/// the output when its ancestor is `display:none` (the pipeline still runs and
/// `currentTime` advances, so the failure is silent). `visibility:hidden` /
/// `display:none` are both wrong here for the same reason.
pub const MEDIA_CONTROLLER_SHELL_STYLE: &str =
    "position:absolute;width:1px;height:1px;opacity:0;overflow:hidden;pointer-events:none";

/// The renderer-owned default typography scale for a `TEXT_STYLE` code
/// (spec/MODIFIERS.md §2): `(size_px, weight_100_900)`. `None` for an unknown
/// code. Explicit `FONT_SIZE`/`FONT_WEIGHT` override these.
fn text_style_scale(code: u8) -> Option<(f32, f32)> {
    Some(match code {
        0 => (34.0, 700.0),  // LargeTitle
        1 => (28.0, 700.0),  // Title
        2 => (22.0, 700.0),  // Title2
        3 => (20.0, 600.0),  // Title3
        4 => (17.0, 600.0),  // Headline
        5 => (15.0, 400.0),  // Subheadline
        6 => (15.0, 400.0),  // Body
        7 => (14.0, 400.0),  // Callout
        8 => (13.0, 400.0),  // Footnote
        9 => (12.0, 400.0),  // Caption
        10 => (11.0, 400.0), // Caption2
        _ => return None,
    })
}

/// A generic CSS family for a `FONT_DESIGN` code (Default → the renderer's UI
/// font, i.e. no override).
fn font_design_family(code: u8) -> Option<&'static str> {
    match code {
        1 => Some("serif"),
        2 => Some("sans-serif"),
        3 => Some("ui-monospace, SFMono-Regular, Menlo, monospace"),
        _ => None,
    }
}

/// A decoded node in the retained description.
#[derive(Debug, Clone, PartialEq)]
pub struct Node {
    /// Protocol component type id (see `pathland_core::component_type`).
    pub component: u16,
    /// Text content set via `PARAMETER::SET_TEXT`, if any.
    pub text: Option<String>,
    /// Constraint/style properties (`propertyId → value`).
    pub properties: BTreeMap<u16, u32>,
    /// `STRING`-typed properties resolved at apply time (`propertyId → text`).
    pub strings: BTreeMap<u16, String>,
    /// `DESIGN_TOKEN`-typed properties (`propertyId → token path`).
    pub token_refs: BTreeMap<u16, String>,
    /// Date value from `PARAMETER::SET_DATE`: (days since epoch, millis of day).
    pub date: Option<(i32, u32)>,
    /// Child node ids in insertion order.
    pub children: Vec<u32>,
    /// A `FIT_QUERY` threshold table, decoded numerically (ascending f32s) — the
    /// candidate `minWidth`s the web renderer turns into `@container` band rules
    /// for the SSR first paint (issue #141).
    pub fit_thresholds: Vec<f32>,
}

/// Whether a `SIZE_THAT_FITS` node satisfies the **candidate-identity contract**
/// (spec/PRIMITIVES.md §SizeThatFits): a full-snapshot slot has MORE THAN ONE
/// child, exactly one ascending `FIT_QUERY` threshold per child (so child `i` ⇔
/// candidate `i`). A frame that violates this (table length ≠ child count, or a
/// non-ascending table) cannot be CSS-gated and is rendered as the threshold-0
/// fallback child only — never as ungated overlapping candidates.
fn fit_candidates_valid(node: &Node) -> bool {
    node.children.len() > 1
        && node.fit_thresholds.len() == node.children.len()
        && node.fit_thresholds.windows(2).all(|w| w[0] <= w[1])
}

/// The `@container` band rules for every multi-candidate fit slot, rendered into
/// a `<style data-pathland-fit>` element in the document head (issue #141). For
/// each distinct threshold `T_i` (ascending) the rule is **self-contained**: at
/// `width >= T_i` show candidate `i` and hide every OTHER candidate of this slot,
/// so exactly one candidate is visible at any container width — mirroring
/// `pathland_core::fit::fit_index` (equal thresholds pick the lower index, so
/// later duplicates never get a rule).
///
/// Bands use `min-width`, so at a width past several thresholds every matching
/// rule applies; all selectors are one attribute deep (equal specificity) and
/// `!important`, and the bands are emitted **ascending**, so the highest matching
/// band wins the cascade. Below the first threshold no rule matches → candidate 0
/// keeps its default `display:contents` (fallback) and candidates ≥ 1 keep
/// `display:none`.
///
/// The child at position `i` is candidate `i` (spec/PRIMITIVES.md §SizeThatFits
/// candidate-identity contract): the slot's child order equals the `FIT_QUERY`
/// order, so the band index and the child index are the same.
fn fit_band_styles(nodes: &BTreeMap<u32, Node>) -> String {
    let mut rules = String::new();
    for (id, node) in nodes {
        if node.component != component_type::SIZE_THAT_FITS || !fit_candidates_valid(node) {
            continue;
        }
        let n = node.children.len();
        let mut seen = f32::NEG_INFINITY;
        for (i, &t) in node.fit_thresholds.iter().enumerate() {
            if i == 0 {
                seen = t;
                continue; // candidate 0 is the default-visible fallback
            }
            if t <= seen {
                continue; // equal threshold → lower index already owns the band
            }
            seen = t;
            let mut band = format!("@container pf-{id} (min-width: {t}px) {{");
            for j in 0..n {
                if j == i {
                    continue; // never hide the winner
                }
                band.push_str(&format!(
                    " [data-pathland-id=\"{id}\"] > [data-pathland-fit-cand=\"{j}\"] {{ display: none !important; }}"
                ));
            }
            band.push_str(&format!(
                " [data-pathland-id=\"{id}\"] > [data-pathland-fit-cand=\"{i}\"] {{ display: contents !important; }} }} \n"
            ));
            rules.push_str(&band);
        }
    }
    rules
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
            fit_thresholds: Vec::new(),
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
        if let Some(bits) = self.properties.get(&property_id::FONT_DESIGN) {
            if let Some(family) = font_design_family(f32::from_bits(*bits) as u8) {
                css.push_str(&format!("font-family:{family};"));
            }
        }
        if let Some(v) = self.token_ref(property_id::FONT_FAMILY) {
            css.push_str(&format!("font-family:{v};"));
        } else if let Some(family) = self.strings.get(&property_id::FONT_FAMILY) {
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
        // .infinity`). HUG_CONTENT (-2) is left to the intrinsic size. (A grid's
        // track counts live in `GRID_COLUMNS`/`GRID_ROWS`, never here.)
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

        // Typography (arbitrary size/weight inline). A predefined `TEXT_STYLE`
        // supplies the default size/weight; explicit `FONT_SIZE`/`FONT_WEIGHT`
        // below override it (raw modifiers layer on top).
        if let Some(style) = self.text_style_code() {
            if let Some((size, weight)) = text_style_scale(style) {
                css.push_str(&format!("font-size:{size}px;font-weight:{weight};"));
            }
        }
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
        // Shadow: a non-zero `SHADOW_RADIUS` (or an x/y offset) draws a
        // box-shadow. The color is `SHADOW_COLOR` when set, else the
        // renderer-owned default `rgba(0,0,0,0.3)` — mirroring the DOM client.
        // `shadow(radius)` carries only the radius (the color is the renderer's).
        let shadow_radius_ref = self.token_ref(property_id::SHADOW_RADIUS);
        let shadow_x_ref = self.token_ref(property_id::SHADOW_X);
        let shadow_y_ref = self.token_ref(property_id::SHADOW_Y);
        let shadow_radius = f32p(property_id::SHADOW_RADIUS).unwrap_or(0.0);
        let shadow_x = f32p(property_id::SHADOW_X).unwrap_or(0.0);
        let shadow_y = f32p(property_id::SHADOW_Y).unwrap_or(0.0);
        if shadow_radius_ref.is_some()
            || shadow_x_ref.is_some()
            || shadow_y_ref.is_some()
            || shadow_radius != 0.0
            || shadow_x != 0.0
            || shadow_y != 0.0
        {
            let color = self
                .token_ref(property_id::SHADOW_COLOR)
                .or_else(|| self.properties.get(&property_id::SHADOW_COLOR).copied().map(rgba))
                .unwrap_or_else(|| "rgba(0,0,0,0.3)".to_string());
            let x = shadow_x_ref.unwrap_or_else(|| format!("{shadow_x}px"));
            let y = shadow_y_ref.unwrap_or_else(|| format!("{shadow_y}px"));
            let r = shadow_radius_ref.unwrap_or_else(|| format!("{shadow_radius}px"));
            // Color first, then the lengths — the order the browser serializes a
            // `box-shadow` back to, so the SSR string matches the hydrated DOM.
            css.push_str(&format!("box-shadow:{color} {x} {y} {r};"));
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
        // ALIGNMENT cross-axis position (Leading=0, Center=1, Trailing=2,
        // → start). Positions children; the default is hug (flex-start), never
        // CSS stretch — only FILL-sized children stretch (LAYOUT.md). A
        // `ZSTACK`/grid positions on BOTH axes (its own branch emits the
        // per-axis values), so the single-axis `align-items` is skipped there.
        if let Some(v) = f32p(property_id::ALIGNMENT) {
            if !matches!(
                self.component,
                component_type::ZSTACK
                    | component_type::GRID
                    | component_type::LAZY_VGRID
                    | component_type::LAZY_HGRID
            ) {
                css.push_str(&format!(
                    "align-items:{};",
                    match v as u8 {
                        0 => "flex-start",
                        1 => "center",
                        2 => "flex-end",
                        _ => "flex-start",
                    }
                ));
            }
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
        // TRUNCATION_MODE alone has no observable effect (spec LAYOUT.md §content
        // fitting, SwiftUI-aligned): it only positions the ellipsis under a
        // `LINE_LIMIT` clamp, which the renderer tail-ellipsizes via `line-clamp`
        // (renderer-owned fidelity: CSS cannot place a head/middle ellipsis).
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

/// The stack's main axis as a boolean (horizontal) when `component` is a flex
/// stack; `None` otherwise.
fn stack_main_horizontal(component: u16) -> Option<bool> {
    match component {
        component_type::HSTACK | component_type::LAZY_HSTACK => Some(true),
        component_type::VSTACK | component_type::LAZY_VSTACK => Some(false),
        _ => None,
    }
}

/// Whether a node is **effectively `FILL`-sized on an axis** (LAYOUT.md §fill
/// propagation, SwiftUI/Compose parity): it is `FILL` itself, a layout-greedy
/// primitive that fills the axis by nature, or a Hug-sized container whose
/// subtree carries such a child. A Fixed box bounds its subtree.
///
/// `axis_horizontal` selects `WIDTH` (true) or `HEIGHT`; `main_axis` tells
/// whether the axis is the node's parent's main axis (governs `SPACER`/`DIVIDER`
/// greediness).
fn fills_axis(
    nodes: &BTreeMap<u32, Node>,
    id: u32,
    axis_horizontal: bool,
    main_axis: bool,
) -> bool {
    let Some(node) = nodes.get(&id) else {
        return false;
    };
    let prop = if axis_horizontal {
        property_id::WIDTH
    } else {
        property_id::HEIGHT
    };
    let hint = node.properties.get(&prop).map(|b| f32::from_bits(*b));
    match hint {
        Some(v) if v == size::FILL => return true,
        Some(v) if v.is_finite() && v > 0.0 => return false, // Fixed bounds
        _ => {}
    }
    let greedy = match node.component {
        component_type::COLOR | component_type::SCROLLVIEW => true,
        component_type::SPACER => main_axis,
        component_type::DIVIDER => !main_axis,
        _ => false,
    };
    if greedy {
        return true;
    }
    if !matches!(
        node.component,
        component_type::VSTACK
            | component_type::HSTACK
            | component_type::ZSTACK
            | component_type::LAZY_VSTACK
            | component_type::LAZY_HSTACK
    ) {
        return false;
    }
    // A child's greedy nature is relative to its own parent (this node).
    let child_main = stack_main_horizontal(node.component)
        .map(|main_h| main_h == axis_horizontal)
        .unwrap_or(false); // ZSTACK has no main axis
    node.children
        .iter()
        .any(|&child| fills_axis(nodes, child, axis_horizontal, child_main))
}

/// Fill-propagation sizing for a flex stack: a Hug-sized stack that contains an
/// effectively-`FILL` descendant on an axis becomes `FILL` on that axis (emit
/// `100%`), matching SwiftUI/Compose. Returns the extra CSS, empty when the
/// stack is Fixed/FILL-sized itself (its own frame already covers it).
fn fill_propagation(
    nodes: &BTreeMap<u32, Node>,
    id: u32,
    node: &Node,
    main_horizontal: bool,
) -> String {
    let mut extra = String::new();
    for (axis_h, prop, css) in [
        (true, property_id::WIDTH, "width:100%;"),
        (false, property_id::HEIGHT, "height:100%;"),
    ] {
        let hint = node.properties.get(&prop).map(|b| f32::from_bits(*b));
        let hug = hint.is_none() || hint == Some(size::HUG_CONTENT);
        if hug && fills_axis(nodes, id, axis_h, main_horizontal == axis_h) {
            extra.push_str(css);
        }
    }
    extra
}
/// A grid's fixed track count from `prop`: a positive finite Fixed value pins
/// the count; `FILL`/absent → auto-fit (`None`).
fn grid_count(node: &Node, prop: u16) -> Option<u32> {
    let v = node.f32_property(prop, -1.0);
    if v <= 0.0 {
        None
    } else {
        Some(v.round() as u32)
    }
}

/// A grid's column count from its `GRID_COLUMNS` constructor property
/// (cell-axis count; `FILL`/absent → auto-fit).
fn grid_columns(node: &Node) -> Option<u32> {
    grid_count(node, property_id::GRID_COLUMNS)
}

/// A grid's row count from its `GRID_ROWS` constructor property (cell-axis
/// count; a `LAZY_HGRID`'s fixed track).
fn grid_rows(node: &Node) -> Option<u32> {
    grid_count(node, property_id::GRID_ROWS)
}

/// The horizontal position component of an `ALIGNMENT` 2D code (0–8, spec
/// PRIMITIVES.md §ZStack).
fn align_h(code: u8) -> &'static str {
    match code {
        1 | 3 | 4 => "center",
        2 | 6 | 7 => "end",
        _ => "start",
    }
}

/// The vertical position component of an `ALIGNMENT` 2D code (0–8).
fn align_v(code: u8) -> &'static str {
    match code {
        1 | 5 | 6 => "center",
        2 | 4 | 8 => "end",
        _ => "start",
    }
}

/// A grid's `GRID_TRACKS` tokens (comma-separated `flex`/`fixed:<pts>`/
/// `adaptive:<pts>`, spec §grid model). `None` when absent.
fn grid_tracks(node: &Node) -> Option<Vec<&str>> {
    node.strings
        .get(&property_id::GRID_TRACKS)
        .map(|s| s.split(',').map(str::trim).collect())
}

/// The CSS track template for a `GRID_TRACKS` spec: `flex` → `1fr`,
/// `fixed:<pts>` → `<pts>px`, `adaptive:<pts>` → `minmax(<pts>px,1fr)`.
fn grid_tracks_css(node: &Node) -> Option<String> {
    let tracks = grid_tracks(node)?;
    let mut out = String::new();
    for t in tracks {
        if !out.is_empty() {
            out.push(' ');
        }
        if let Some(pts) = t.strip_prefix("fixed:") {
            out.push_str(&format!("{pts}px"));
        } else if let Some(pts) = t.strip_prefix("adaptive:") {
            out.push_str(&format!("minmax({pts}px,1fr)"));
        } else {
            out.push_str("1fr");
        }
    }
    Some(out)
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
            category::PARAMETER => {
                if op.command() == parameter::SET_DESIGN_TOKEN {
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
            category::META => match op.command() {
                meta::SET_COLOR_SCHEME => {
                    tokens.scheme_override = match op.a() {
                        color_scheme_mode::LIGHT => Some(Scheme::Light),
                        color_scheme_mode::DARK => Some(Scheme::Dark),
                        _ => None,
                    };
                }
                meta::RESET_THEME => tokens.reset(),
                _ => {}
            },
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

/// Decode a `LIST` arena entry (`[u32 count][f32 × count]`) as a CSV string with
/// trimmed thresholds (e.g. `"0,640"`) — the `FIT_QUERY` mirror the SSR shell
/// emits in its `data-pathland-fit` attribute.
fn list_csv(strings: &[u8], offset: u32) -> Option<String> {
    let offset = offset as usize;
    if offset + 4 > strings.len() {
        return None;
    }
    let count = u32::from_le_bytes(strings[offset..offset + 4].try_into().ok()?) as usize;
    let start = offset + 4;
    if start + count * 4 > strings.len() {
        return None;
    }
    let mut parts = Vec::with_capacity(count);
    for i in 0..count {
        let bits = u32::from_le_bytes(
            strings[start + i * 4..start + i * 4 + 4].try_into().ok()?,
        );
        let value = f32::from_bits(bits);
        // Trim: whole values render without a fraction ("0,640").
        parts.push(if value.fract() == 0.0 {
            format!("{:.0}", value)
        } else {
            format!("{:.1}", value)
        });
    }
    Some(parts.join(","))
}

/// Read a length-prefixed f32 array (`[u32 count][f32 × count]`) from the arena —
/// the numeric form of a `LIST`-typed value (today the `FIT_QUERY` threshold table).
fn list_f32(strings: &[u8], offset: u32) -> Option<Vec<f32>> {
    let offset = offset as usize;
    if offset + 4 > strings.len() {
        return None;
    }
    let count = u32::from_le_bytes(strings[offset..offset + 4].try_into().ok()?) as usize;
    let start = offset + 4;
    if start + count * 4 > strings.len() {
        return None;
    }
    let mut values = Vec::with_capacity(count);
    for i in 0..count {
        let bits = u32::from_le_bytes(
            strings[start + i * 4..start + i * 4 + 4].try_into().ok()?,
        );
        values.push(f32::from_bits(bits));
    }
    Some(values)
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
    /// `META::SET_COLOR_SCHEME` override (`None` = platform).
    scheme_override: Option<Scheme>,
}

impl Tokens {
    fn set(&mut self, path: &str, value_type: u8, value: TokenValue) {
        if let Some(bare) = path.strip_prefix("dark.") {
            self.dark.insert(bare.to_string(), (value_type, value));
        } else {
            self.base.insert(path.to_string(), (value_type, value));
        }
    }

    /// `META::RESET_THEME`: drop every application override (base + dark).
    fn reset(&mut self) {
        self.base.clear();
        self.dark.clear();
    }

    fn declarations(&self, dark: bool) -> String {
        let map = if dark { &self.dark } else { &self.base };
        map.iter()
            .map(|(path, (vt, value))| {
                format!("{}:{};", token_to_css_var(path), token_css_value(path, *vt, value))
            })
            .collect()
    }

    /// Render the overrides as CSS rules: base tokens as `:root` rules; dark
    /// overrides inside the platform-dark media query (when not forced) and
    /// under the forced-dark attribute selector.
    ///
    /// Every base override also populates the forced-dark variable family
    /// (`--pl-dark-…`) because the dark resolution layer falls through to base
    /// overrides; a dark override is emitted afterwards so it wins.
    fn css(&self) -> String {
        let mut css = String::new();
        let base = self.declarations(false);
        if !base.is_empty() {
            css.push_str(&format!(":root{{{base}}}"));
        }
        if !base.is_empty() || !self.dark.is_empty() {
            // In a dark scheme the dark layer wins first, then the base
            // override falls through; the base declarations are repeated in
            // the dark scope so they outrank the built-in dark defaults.
            let dark = self.declarations(true);
            let scoped = format!("{base}{dark}");
            css.push_str(&format!(
                "@media (prefers-color-scheme: dark){{:root:not([data-pathland-scheme]){{{scoped}}}}}"
            ));
            css.push_str(&format!(
                ":root[data-pathland-scheme=\"dark\"]{{{scoped}}}"
            ));
        }
        // Forced-dark refs: base overrides fall through, then dark overrides
        // win (emitted second).
        let mut forced = String::new();
        for (path, (vt, value)) in &self.base {
            forced.push_str(&format!(
                "{}:{};",
                forced_dark_var(path),
                token_css_value(path, *vt, value)
            ));
        }
        for (path, (vt, value)) in &self.dark {
            forced.push_str(&format!(
                "{}:{};",
                forced_dark_var(path),
                token_css_value(path, *vt, value)
            ));
        }
        if !forced.is_empty() {
            css.push_str(&format!(":root{{{forced}}}"));
        }
        css
    }
}

/// The forced-dark variable for a token path (`--pl-dark-<path>`).
fn forced_dark_var(path: &str) -> String {
    let bare = path.strip_prefix(token_spec::DARK_PREFIX).unwrap_or(path);
    format!("{}{}", token_spec::FORCED_DARK_VAR_PREFIX, css_var_name(bare))
}

/// The canonical CSS custom property for a token path: `--pl-` prefix, `.` →
/// `-`. The `dark.` scheme prefix is stripped — the dark variant overrides the
/// same variable inside the media query (spec/TOKENS.md; the naming rules live
/// in [`token_spec`], which `pathland-ts-codegen` mirrors into the DOM client).
fn token_to_css_var(path: &str) -> String {
    let bare = path.strip_prefix(token_spec::DARK_PREFIX).unwrap_or(path);
    format!("{}{}", token_spec::VAR_PREFIX, css_var_name(bare))
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
/// an explicit `dark.` reference resolves the forced-dark variable family
/// (`--pl-dark-<path>`); every other token resolves to `var(--pl-<path>)`.
/// A missing token falls back to `unset` (the renderer's own default styling).
fn resolve_token_ref(path: &str) -> String {
    let forced_dark = path.strip_prefix(token_spec::DARK_PREFIX);
    let bare = forced_dark.unwrap_or(path);
    if let Some(rest) = bare.strip_prefix(token_spec::SPACE_FAMILY) {
        if rest.parse::<f64>().is_ok() {
            let base = if forced_dark.is_some() {
                "--pl-dark-space-base"
            } else {
                "--pl-space-base"
            };
            return format!("calc(var({base}) * {rest})");
        }
    }
    if forced_dark.is_some() {
        return format!(
            "var({}{}, unset)",
            token_spec::FORCED_DARK_VAR_PREFIX,
            css_var_name(bare)
        );
    }
    format!("var({}, unset)", token_to_css_var(bare))
}

/// The normalized name component of a token path (`.` and other non-CSS-safe
/// characters → `-`), without the `--pl-` prefix.
fn css_var_name(path: &str) -> String {
    path.chars()
        .map(|c| if c.is_ascii_alphanumeric() || c == '-' || c == '_' { c } else { '-' })
        .collect()
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
        parameter::SET_TEXT => {
            if let Some(text) = strings_str(strings, op.b()) {
                if let Some(node) = nodes.get_mut(&op.a()) {
                    node.text = Some(text);
                }
            }
        }
        parameter::SET_PROPERTY => {
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
                } else if vt == value_type::LIST {
                    // A length-prefixed f32 array — today a `FIT_QUERY` threshold
                    // table; stored as its CSV form so the SSR shell can mirror it
                    // in a `data-pathland-fit` attribute for the DOM client, and as
                    // the numeric table for the `@container` band rules.
                    if let Some(csv) = list_csv(strings, op.c()) {
                        node.strings.insert(property, csv);
                    }
                    if let Some(values) = list_f32(strings, op.c()) {
                        node.fit_thresholds = values;
                    }
                } else {
                    node.properties.insert(property, op.c());
                }
            }
        }
        parameter::SET_DATE => {
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
        let body = self.render_node(&nodes, root, None);
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
        // Multi-candidate fit slots (issue #141) get their `@container` band rules
        // in a dedicated `data-pathland-fit` element (the SSR visibility contract;
        // the DOM client drops them on hydration). None in live/single-child
        // frames → no element.
        let fit_rules = fit_band_styles(&nodes);
        let fit_style = if fit_rules.is_empty() {
            String::new()
        } else {
            format!("<style data-pathland-fit>{fit_rules}</style>\n")
        };
        let scheme_attr = match tokens.scheme_override {
            Some(Scheme::Light) => " data-pathland-scheme=\"light\"",
            Some(Scheme::Dark) => " data-pathland-scheme=\"dark\"",
            None => "",
        };
        format!(
            "<!DOCTYPE html>\n<html{scheme_attr}>\n<head>\n<meta charset=\"utf-8\">\n\
             <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n\
             <title>Pathland</title>\n\
             {}{}{}\n</head>\n<body>{body}</body>\n</html>\n",
            css::STYLE,
            token_style,
            fit_style
        )
    }

    /// Render the subtree rooted at `root` as an HTML fragment (no `<html>`).
    /// `SET_DESIGN_TOKEN` overrides are applied to the document head by
    /// `render_document`; a fragment is assumed to be embedded in a document
    /// that already defines the token variables.
    #[must_use]
    pub fn render_fragment(&self, opcodes: &[Opcode], strings: &[u8], root: u32) -> String {
        let (nodes, _) = decode(opcodes, strings);
        self.render_node(&nodes, root, None)
    }

    fn render_node(
        &self,
        nodes: &BTreeMap<u32, Node>,
        id: u32,
        parent_component: Option<u16>,
    ) -> String {
        let Some(node) = nodes.get(&id) else {
            return String::new();
        };
        let children: String = node
            .children
            .iter()
            .map(|&child| self.render_node(nodes, child, Some(node.component)))
            .collect();
        let mut data_id = format!(" data-pathland-id=\"{id}\"");
        data_id.push_str(&slot_attrs(node));
        let mut css = format!("{}{}", node.style_css(), node.border_style());
        // Derived layout (LAYOUT.md), separate from the node's own css so
        // components with hardcoded shells (e.g. DIVIDER) can append it without
        // duplicating their base styles.
        let mut derived = String::new();
        // Cross-axis FILL: a child that is effectively FILL on its parent
        // stack's cross axis stretches via `align-self:stretch` (fills the
        // cross size even when the container's cross size is content-driven).
        if let Some(parent) = parent_component {
            if let Some(main_horizontal) = stack_main_horizontal(parent) {
                if fills_axis(nodes, id, !main_horizontal, false) {
                    derived.push_str("align-self:stretch;");
                }
            }
        }
        // LINE_LIMIT truncation: a positive line limit clamps the text to N lines
        // (mirrors the DOM client's PROP_LINE_LIMIT application, classes.ts).
        // `overflow:hidden` is REQUIRED: -webkit-line-clamp sizes the box to N
        // lines and draws the ellipsis, but does not clip the overflow on its
        // own — without it Safari/WebKit paint the overflowed text below the
        // clamped box (the ellipsis AND the extra line both show).
        let line_limit = node.u32_property(property_id::LINE_LIMIT, 0);
        if line_limit > 0 {
            css.push_str(&format!(
                "display:-webkit-box;overflow:hidden;-webkit-line-clamp:{line_limit};-webkit-box-orient:vertical;"
            ));
        }
        let style = style_attr(&format!("{css}{derived}"));
        let event = event_attrs(node);
        let aria = aria_attrs(node);
        // A semantic role may retag a generic div/span shell (role_spec);
        // control components keep their native element.
        let kind = role_spec::shell_kind(node.component);
        let semantic = role_spec::semantic_tag(kind, node.role_code());

        let element = match node.component {
            component_type::VSTACK => {
                wrap_stack(id, "column", semantic, node, &children, &format!("{css}{}{derived}", fill_propagation(nodes, id, node, false)), &event, &aria)
            }
            component_type::HSTACK => {
                wrap_stack(id, "row", semantic, node, &children, &format!("{css}{}{derived}", fill_propagation(nodes, id, node, true)), &event, &aria)
            }
            component_type::LAZY_VSTACK => {
                wrap_stack(id, "column", semantic, node, &children, &format!("{css}{}{derived}", fill_propagation(nodes, id, node, false)), &event, &aria)
            }
            component_type::LAZY_HSTACK => {
                wrap_stack(id, "row", semantic, node, &children, &format!("{css}{}{derived}", fill_propagation(nodes, id, node, true)), &event, &aria)
            }
            component_type::PROXY_VIEW => {
                // A proxy view (spec/PROXY.md §5): a transparent single-child
                // container whose child subtree is served by another Pathland
                // server. The renderer holds no notion of "remote" — it renders a
                // plain container box and lays out the (id-remapped) origin subtree
                // the proxying server spliced in, exactly like a stack with one child.
                wrap_stack(id, "column", semantic, node, &children, &format!("{css}{}{derived}", fill_propagation(nodes, id, node, false)), &event, &aria)
            }
            component_type::SIZE_THAT_FITS => {
                // A fit slot (spec/PRIMITIVES.md §SizeThatFits): size-taking — it
                // fills the parent's proposal, and that measured width is the unit
                // of fitting. `data-pathland-fit` mirrors the FIT_QUERY threshold
                // table so the DOM client can derive the fit locally (and only
                // report FIT_CHANGED on transitions).
                //
                // A SINGLE child (the live form) renders exactly as before. When the
                // slot carries MULTIPLE children it is the SSR full-snapshot form
                // (issue #141): EVERY candidate is laid out and gated by CSS
                // container queries, so a no-JS first paint already shows the
                // candidate matching the slot's width.
                //
                // The slot is a LAYOUT-TRANSPARENT container: it forwards the
                // parent flex container's flex-direction/align-items/justify-content
                // (`inherit`) to its own box, so its child is laid out exactly
                // as if it sat directly in the parent — e.g. a `VStack(CENTER)` keeps
                // centering a candidate the slot wraps, instead of the slot's
                // `align-self:stretch` box pinning the child to the start. Under a
                // non-flex parent `inherit` degrades to the CSS initial values.
                // `align-self:stretch` + `flex:1 1 auto` keep the slot filling/
                // measuring the proposal. In the snapshot form the slot also becomes
                // the `@container` for the band rules and wraps each candidate in a
                // `display:none`-by-default wrapper carrying the candidate's id + a
                // `data-pathland-fit-cand` index; the head `<style data-pathland-fit>`
                // (built by `fit_band_style`) flips the matching band to
                // `display:contents`.
                let fit_attr = node
                    .strings
                    .get(&property_id::FIT_QUERY)
                    .map(|csv| format!(" data-pathland-fit=\"{}\"", escape(csv)))
                    .unwrap_or_default();
                if fit_candidates_valid(node) {
                    let fit_cands = format!(" data-pathland-fit-cands=\"{}\"", node.children.len());
                    let mut boxed = String::new();
                    for (i, &child) in node.children.iter().enumerate() {
                        // Candidate content keeps its own id (the DOM client's byId
                        // maps property deltas to the correct element); the wrapper is
                        // a pure CSS gate — no id — hidden by default except the
                        // threshold-0 fallback (candidate 0). The head
                        // `<style data-pathland-fit>` `@container` rules (built by
                        // `fit_band_styles`) swap which band is visible.
                        let inner = self.render_node(nodes, child, Some(node.component));
                        let default = if i == 0 { "display:contents" } else { "display:none" };
                        boxed.push_str(&format!(
                            "<div data-pathland-fit-cand=\"{i}\" data-pathland-fit-cand-id=\"{child}\" style=\"{default}\">{inner}</div>"
                        ));
                    }
                    let slot_base = format!(
                        "display:flex;flex-direction:inherit;align-items:inherit;justify-content:inherit;flex:1 1 auto;align-self:stretch;min-width:0;min-height:0;container-type:inline-size;container-name:pf-{id};{css}{derived}"
                    );
                    format!(
                        "<div{data_id} class=\"pathland-ftf\"{fit_attr}{fit_cands}{event}{aria} style=\"{slot_base}\">{boxed}</div>"
                    )
                } else if node.children.len() > 1 {
                    // A multi-child slot whose FIT_QUERY table does not satisfy the
                    // candidate-identity contract (length mismatch / non-ascending)
                    // cannot be gated: degrade to the threshold-0 fallback child
                    // only, never render ungated overlapping candidates.
                    let only = self.render_node(nodes, node.children[0], Some(node.component));
                    format!(
                        "<div{data_id} class=\"pathland-ftf\"{fit_attr}{event}{aria} style=\"display:flex;flex-direction:inherit;align-items:inherit;justify-content:inherit;flex:1 1 auto;align-self:stretch;min-width:0;min-height:0;{css}{derived}\">{only}</div>"
                    )
                } else {
                    format!(
                        "<div{data_id} class=\"pathland-ftf\"{fit_attr}{event}{aria} style=\"display:flex;flex-direction:inherit;align-items:inherit;justify-content:inherit;flex:1 1 auto;align-self:stretch;min-width:0;min-height:0;{css}{derived}\">{children}</div>"
                    )
                }
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
            component_type::ICON => {
                // A semantic icon: the canonical `ICON_NAME` maps to a **filled**
                // Remix glyph (inline SVG, single-sourced). An unknown name renders
                // the fallback glyph. A `LABEL` makes it presentable
                // (`role="img"`); without one it is decorative (`aria-hidden`).
                let icon_name = node
                    .strings
                    .get(&property_id::ICON_NAME)
                    .map(|n| format!(" data-pathland-icon=\"{}\"", escape(n)))
                    .unwrap_or_default();
                let inner = node
                    .strings
                    .get(&property_id::ICON_NAME)
                    .and_then(|n| icons::web_inner(n))
                    .unwrap_or_else(icons::web_fallback_svg);
                let icon_aria = match node.strings.get(&property_id::LABEL) {
                    Some(label) if !label.is_empty() => {
                        format!(" role=\"img\" aria-label=\"{}\"", escape(label))
                    }
                    _ => " aria-hidden=\"true\" focusable=\"false\"".to_string(),
                };
                // Open the svg tag, splice the node attrs before `>`, then the
                // Remix inner markup (`WEB_SVG_OPEN` ends with `>`). The
                // `data-pathland-icon` name is the hook the DOM client's lazy
                // resolver uses to seed a glyph from an already-embedded svg.
                let open = icons::WEB_SVG_OPEN.trim_end_matches('>');
                format!("{open}{data_id}{icon_name}{icon_aria}{event}{style}>{inner}</svg>")
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
            component_type::MEDIA_CONTROLLER => {
                // Non-visual + layout-transparent (spec/MEDIA.md): an off-flow
                // container holding the control-less playback element. It is
                // **position:absolute**, NOT `display:none`: browsers refuse to
                // route a `<video>`'s **audio** to the output when its ancestor is
                // `display:none` (the media pipeline still runs — `currentTime`
                // advances — so the failure is silent). Absolutely positioning it
                // keeps it laid out (audio flows) while contributing no spacing.
                let src = escape(
                    node.strings
                        .get(&property_id::MEDIA_SOURCE)
                        .map(String::as_str)
                        .unwrap_or_default(),
                );
                let media = if src.is_empty() {
                    String::new()
                } else {
                    format!("<video data-pathland-media src=\"{src}\"></video>")
                };
                format!(
                    "<div{data_id} style=\"{MEDIA_CONTROLLER_SHELL_STYLE}\">{media}</div>"
                )
            }
            component_type::VIDEO => {
                // The video surface: renders the video output of the controller it
                // references (`MEDIA_CONTROLLER_ID`); the DOM client resolves the
                // source + transport from the controller (spec/MEDIA.md).
                let controller = node.u32_property(property_id::MEDIA_CONTROLLER_ID, 0);
                let binding = if controller != 0 {
                    format!(" data-pathland-controller=\"{controller}\"")
                } else {
                    String::new()
                };
                if node.children.is_empty() {
                    // Renderer-native controls (the default `VideoStyle`).
                    format!(
                        "<video{data_id}{event}{aria}{binding} controls{style}></video>"
                    )
                } else {
                    // A custom `VideoStyle` body: the children are the control UI.
                    format!(
                        "<div{data_id}{event}{aria} class=\"pathland-media\"{binding}{style}>{children}</div>"
                    )
                }
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
                // Layout-greedy on the cross axis (LAYOUT.md): the separator
                // spans the stack's available cross size unless a size frame
                // overrides it (SwiftUI `Divider()` semantics). The derived css
                // (e.g. cross-axis `align-self:stretch`) is appended so the
                // divider participates in stack layout like any other child.
                format!(
                    "<div{data_id}{event}{aria} style=\"height:0;width:100%;border-top:{width}px solid {color};{derived}\"></div>"
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
                // Explicit rows (spec §GridRow): a grid's children are cells or
                // `GRID_ROW`s; a `GRID_ROW`'s children are one row's cells.
                let explicit_rows = node
                    .children
                    .iter()
                    .any(|&c| nodes.get(&c).is_some_and(|n| n.component == component_type::GRID_ROW));
                // The effective 1fr column count: the `GRID_TRACKS` token count, else
                // `GRID_COLUMNS`, else the widest row when rows are explicit
                // (a short row leaves trailing columns empty — never pulls the
                // next row's cells forward).
                let track_count = grid_tracks(node).map(|t| t.len() as u32);
                let base_columns = grid_columns(node);
                let effective_columns = if let Some(n) = track_count {
                    Some(n)
                } else if explicit_rows {
                    let mut width = base_columns.map(|c| c as usize).unwrap_or(0);
                    let mut run = 0usize;
                    for &child in &node.children {
                        if nodes.get(&child).is_some_and(|n| n.component == component_type::GRID_ROW) {
                            width = width.max(nodes.get(&child).map(|n| n.children.len()).unwrap_or(0));
                            run = 0;
                        } else {
                            run += 1;
                            width = width.max(run);
                        }
                    }
                    base_columns.or(Some((width.max(1)) as u32))
                } else {
                    base_columns
                };
                let grid_css = grid_style(node, effective_columns);
                let combined = format!("{grid_css}{css}");
                let grid_style = style_attr(&combined);
                // Per-cell alignment (spec/PRIMITIVES.md §grid model): each cell
                // keeps its own size (Fixed/Hug) and is positioned by the grid's
                // ALIGNMENT on both axes; a `FILL`-sized cell (or a greedy
                // filler like `COLOR`/`SCROLLVIEW`) stretches to fill its track.
                let align = node.f32_property(property_id::ALIGNMENT, 0.0) as u8;
                let pos_h = |stretch: bool| if stretch { "stretch" } else { align_h(align) };
                let pos_v = |stretch: bool| if stretch { "stretch" } else { align_v(align) };
                let cell_shell = |cell_html: String, row: usize, col: usize, cell: u32| {
                    let placement = if explicit_rows {
                        format!("grid-row:{};grid-column:{};", row + 1, col + 1)
                    } else {
                        String::new()
                    };
                    // The cell's Z_INDEX orders its shell (the grid item) among
                    // overlapping siblings; see the ZSTACK wrapper note.
                    let z = nodes
                        .get(&cell)
                        .map(|cn| cn.f32_property(property_id::Z_INDEX, 0.0))
                        .filter(|z| *z != 0.0)
                        .map(|z| format!("z-index:{};", z as i32))
                        .unwrap_or_default();
                    format!(
                        "<div style=\"{placement}{z}justify-self:{};align-self:{};\">{cell_html}</div>",
                        pos_h(fills_axis(nodes, cell, true, false)),
                        pos_v(fills_axis(nodes, cell, false, false)),
                    )
                };
                let inner: String = if explicit_rows {
                    // Flatten the explicit row structure into (row, col) cells,
                    // keeping each GRID_ROW as a transparent `display:contents`
                    // wrapper (its id hydrates the DOM client's registry).
                    let columns = effective_columns.unwrap_or(1) as usize;
                    let mut out = String::new();
                    let mut row = 0usize;
                    let mut col = 0usize;
                    for &child in &node.children {
                        let Some(cn) = nodes.get(&child) else { continue };
                        if cn.component == component_type::GRID_ROW {
                            // A GRID_ROW starts a new row: advance past an
                            // in-progress bare-cell run (col > 0); a prior
                            // GRID_ROW already advanced `row`.
                            if col > 0 {
                                row += 1;
                            }
                            col = 0;
                            let mut cells = String::new();
                            for &cell in &cn.children {
                                let cell_html = self.render_node(nodes, cell, Some(node.component));
                                if !cell_html.is_empty() {
                                    cells.push_str(&cell_shell(cell_html, row, col, cell));
                                }
                                col += 1;
                            }
                            out.push_str(&format!(
                                "<div data-pathland-id=\"{child}\" style=\"display:contents\">{cells}</div>"
                            ));
                            row += 1;
                            col = 0;
                        } else {
                            if col >= columns {
                                row += 1;
                                col = 0;
                            }
                            let cell_html = self.render_node(nodes, child, Some(node.component));
                            if !cell_html.is_empty() {
                                out.push_str(&cell_shell(cell_html, row, col, child));
                                col += 1;
                            }
                        }
                    }
                    out
                } else {
                    node.children
                        .iter()
                        .filter_map(|&child| {
                            let cell_html = self.render_node(nodes, child, Some(node.component));
                            if cell_html.is_empty() {
                                None
                            } else {
                                Some(cell_shell(cell_html, 0, 0, child))
                            }
                        })
                        .collect()
                };
                format!("<{tag}{data_id}{event}{aria}{grid_style}>{inner}</{tag}>")
            }
            component_type::SCROLLVIEW => {
                let tag = semantic.unwrap_or("div");
                // Layout-greedy on both axes (LAYOUT.md): a scroll region fills
                // the available space (SwiftUI `ScrollView` semantics) unless a
                // size frame overrides it. The **first child** is the content
                // (spec/PRIMITIVES.md §ScrollView); additional children are not
                // rendered.
                let combined = format!("flex:1 1 auto;align-self:stretch;overflow:auto;{css}");
                let scroll_style = style_attr(&combined);
                let content = node
                    .children
                    .first()
                    .map(|&c| self.render_node(nodes, c, Some(node.component)))
                    .unwrap_or_default();
                format!("<{tag}{data_id}{event}{aria}{scroll_style}>{content}</{tag}>")
            }
            component_type::ZSTACK => {
                let tag = semantic.unwrap_or("div");
                // ZStack (SwiftUI `ZStack` / Compose `Box`): children overlap in
                // one box. The container hugs to its largest child by default
                // (grid `max-content` tracks); a Fixed/FILL frame sizes it
                // exactly; a FILL child propagates (`100%`). Each child keeps
                // its own size and is positioned by `ALIGNMENT` on both axes
                // (spec/PRIMITIVES.md §ZStack).
                let align = node.f32_property(property_id::ALIGNMENT, 0.0) as u8;
                let pos_h = |stretch: bool| if stretch { "stretch" } else { align_h(align) };
                let pos_v = |stretch: bool| if stretch { "stretch" } else { align_v(align) };
                let inner: String = node
                    .children
                    .iter()
                    .filter_map(|&child| {
                        let child_html = self.render_node(nodes, child, Some(node.component));
                        if child_html.is_empty() {
                            None
                        } else {
                            // The child's Z_INDEX orders its shell (the grid item)
                            // among the overlapping siblings — z-index on the child
                            // itself is inert (it is not a grid/flex item). EVERY
                            // shell carries an explicit z-index (the child's Z_INDEX,
                            // else 0) so overlapping grid items paint deterministically
                            // in every browser (WebKit paints a non-fill grid item
                            // below a full-area sibling when both are z-index:auto).
                            let z = nodes
                                .get(&child)
                                .map(|cn| cn.f32_property(property_id::Z_INDEX, 0.0) as i32)
                                .unwrap_or(0);
                            Some(format!(
                                "<div style=\"grid-area:1/1;z-index:{z};justify-self:{};align-self:{};\">{child_html}</div>",
                                pos_h(fills_axis(nodes, child, true, false)),
                                pos_v(fills_axis(nodes, child, false, false)),
                            ))
                        }
                    })
                    .collect();
                // Container sizing: Hug → max-content, or `100%` on an axis a
                // FILL child propagates; Fixed/FILL frames come from `css`.
                let mut zcss = format!(
                    "display:grid;isolation:isolate;grid-template-columns:minmax(0,1fr);grid-template-rows:minmax(0,1fr);justify-items:{};align-items:{};",
                    align_h(align),
                    align_v(align)
                );
                for (axis_h, prop, hug_css, fill_css) in [
                    (true, property_id::WIDTH, "width:max-content;", "width:100%;"),
                    (false, property_id::HEIGHT, "height:max-content;", "height:100%;"),
                ] {
                    let hint = node.properties.get(&prop).map(|b| f32::from_bits(*b));
                    let hug = hint.is_none() || hint == Some(size::HUG_CONTENT);
                    if hug {
                        zcss.push_str(if fills_axis(nodes, id, axis_h, false) {
                            fill_css
                        } else {
                            hug_css
                        });
                    }
                }
                let combined = format!("{zcss}{css}{derived}");
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
                        // own TREE/PARAMETER deltas.
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
        // ALIGNMENT positions cross-axis children; the default is hug
        // (flex-start), never CSS `align-items: stretch` — only FILL-sized
        // children stretch (they carry `align-self:stretch`/`100%`), LAYOUT.md.
        let align = match alignment {
            0 => "flex-start",
            1 => "center",
            2 => "flex-end",
            _ => "flex-start",
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

/// Inline grid CSS: `display:grid` + `justify-items`/`align-items` from the
/// grid `ALIGNMENT` (cells position within their tracks; default Leading/start),
/// plus the fixed track templates — a `GRID_TRACKS` spec (per-track sizes) or
/// `columns` (GRID/LAZY_VGRID; the effective count) and `HEIGHT` rows
/// (GRID/LAZY_HGRID) as equal `1fr` tracks. `FILL`/absent count = auto-fit (no
/// template; `LAZY_HGRID` columns auto-flow).
fn grid_style(node: &Node, columns: Option<u32>) -> String {
    let align = node.f32_property(property_id::ALIGNMENT, 0.0) as u8;
    let mut css = format!(
        "display:grid;justify-items:{};align-items:{};",
        align_h(align),
        align_v(align)
    );
    let tracks_css = grid_tracks_css(node);
    match node.component {
        component_type::GRID => {
            if let Some(t) = &tracks_css {
                css.push_str(&format!("grid-template-columns:{t};"));
            } else if let Some(n) = columns {
                css.push_str(&format!("grid-template-columns:repeat({n},minmax(0,1fr));"));
            }
            if let Some(n) = grid_rows(node) {
                css.push_str(&format!("grid-template-rows:repeat({n},minmax(0,1fr));"));
            }
        }
        component_type::LAZY_HGRID => {
            if let Some(t) = &tracks_css {
                css.push_str(&format!("grid-template-rows:{t};"));
            } else if let Some(n) = grid_rows(node) {
                css.push_str(&format!("grid-template-rows:repeat({n},minmax(0,1fr));"));
            }
            css.push_str("grid-auto-flow:column;grid-auto-columns:minmax(0,1fr);");
        }
        _ => {
            if let Some(t) = &tracks_css {
                css.push_str(&format!("grid-template-columns:{t};"));
            } else if let Some(n) = columns {
                css.push_str(&format!("grid-template-columns:repeat({n},minmax(0,1fr));"));
            }
        }
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
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 2, 0, 0));

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
    fn renders_proxy_view_as_a_plain_container_with_its_spliced_subtree() {
        // A PROXY_VIEW(1) whose (id-remapped) origin root VSTACK(2) — with a TEXT(3)
        // child — was spliced in by the proxying server (spec/PROXY.md §5). The
        // renderer holds no notion of "remote": it renders a plain container box and
        // lays out the origin subtree.
        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::PROXY_VIEW as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 3, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 2, 3, pathland_core::APPEND));
        // The proxy attaches the origin's parentless root under itself.
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, pathland_core::APPEND));
        strings.extend_from_slice(&(5u32).to_le_bytes());
        strings.extend_from_slice(b"Hello");
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 3, 0, 0));

        let html = HtmlRenderer::new().render_document(&opcodes, &strings, 1);

        assert!(html.contains("data-pathland-id=\"1\""), "the proxy node renders as a box: {html}");
        assert!(html.contains("data-pathland-id=\"2\""), "the spliced origin root renders: {html}");
        assert!(html.contains("<span data-pathland-id=\"3\">Hello</span>"), "the origin's text: {html}");
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
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 1, 0, 0));
        // border: width 2, opaque red, all edges
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::BORDER_WIDTH as u32,
            2.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::COLOR as u32) << 16) | property_id::BORDER_COLOR as u32,
            0xFFFF_0000,
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 1, 0, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::TOGGLE_STYLE as u32,
            0.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 1, 0, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::TOGGLE_STYLE as u32,
            1.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 1, 0, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::TOGGLE_STYLE as u32,
            2.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 1, 0, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::MIN_VALUE as u32,
            0.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::MAX_VALUE as u32,
            1.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 1, 0, 0));
        // label (STRING property)
        strings.extend_from_slice(&(5u32).to_le_bytes());
        strings.extend_from_slice(b"Name:");
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::STRING as u32) << 16) | property_id::LABEL as u32,
            7, // relative offset of the label entry in the string section
        ));
        // prompt (STRING property)
        strings.extend_from_slice(&(5u32).to_le_bytes());
        strings.extend_from_slice(b"Enter");
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 1, 0, 0));

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
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
    fn renders_semantic_icon_with_filled_remix_markup() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::ICON as u32, 0));
        strings.extend_from_slice(&(4u32).to_le_bytes());
        strings.extend_from_slice(b"play");
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::STRING as u32) << 16) | property_id::ICON_NAME as u32,
            0,
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);
        assert!(html.contains("<svg class=\"pathland-icon\""), "icon svg shell: {}", html);
        assert!(html.contains("fill=\"currentColor\""), "filled shell: {}", html);
        assert!(!html.contains("stroke="), "no stroke on a filled icon: {}", html);
        assert!(html.contains("data-pathland-id=\"1\""), "icon keeps its node id: {}", html);
        assert!(html.contains("aria-hidden=\"true\""), "decorative by default: {}", html);
        assert!(html.contains("d=\"M19.376 12.4161L8.77735 19.4818"), "play remix path: {}", html);
        assert!(html.ends_with("</svg>") || html.contains("</svg>"), "svg closed: {}", html);
    }

    #[test]
    fn labeled_icon_is_presentable_and_unknown_name_falls_back() {
        use pathland_core::value_type;

        // A labeled icon → role="img" + aria-label instead of aria-hidden.
        let icon = |name: &str, label: Option<&str>| -> String {
            let mut opcodes = Vec::new();
            let mut strings = Vec::new();
            opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::ICON as u32, 0));
            let name_offset = 0usize;
            strings.extend_from_slice(&(name.len() as u32).to_le_bytes());
            strings.extend_from_slice(name.as_bytes());
            opcodes.push(Opcode::new(
                category::PARAMETER,
                parameter::SET_PROPERTY,
                0,
                1,
                ((value_type::STRING as u32) << 16) | property_id::ICON_NAME as u32,
                name_offset as u32,
            ));
            let label_offset = 4usize + name.len();
            if let Some(label) = label {
                strings.extend_from_slice(&(label.len() as u32).to_le_bytes());
                strings.extend_from_slice(label.as_bytes());
                opcodes.push(Opcode::new(
                    category::PARAMETER,
                    parameter::SET_PROPERTY,
                    0,
                    1,
                    ((value_type::STRING as u32) << 16) | property_id::LABEL as u32,
                    label_offset as u32,
                ));
            }
            HtmlRenderer::new().render_document(&opcodes, &strings, 1)
        };

        let labeled = icon("music", Some("Playlist"));
        assert!(labeled.contains("role=\"img\""), "labeled icon is an image: {}", labeled);
        assert!(labeled.contains("aria-label=\"Playlist\""), "aria label: {}", labeled);
        assert!(!labeled.contains("aria-hidden"), "no aria-hidden when labeled: {}", labeled);

        let unknown = icon("definitely-not-an-icon", None);
        assert!(unknown.contains("aria-hidden=\"true\""), "unlabeled stays decorative: {}", unknown);
        assert!(unknown.contains("cx=\"12\" cy=\"12\" r=\"8\""), "fallback glyph: {}", unknown);
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
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::STRING as u32) << 16) | property_id::IMAGE_SOURCE as u32,
            0,
        ));
        strings.extend_from_slice(&(4u32).to_le_bytes());
        strings.extend_from_slice(b"Home");
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::STRING as u32) << 16) | property_id::LABEL as u32,
            36,
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::CONTENT_MODE as u32,
            1f32.to_bits(),
        ));
        // VIDEO with renderer-native controls, bound to a controller.
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::VIDEO as u32, 0));
        strings.extend_from_slice(&(30u32).to_le_bytes());
        strings.extend_from_slice(b"https://example.com/sample.mp4");
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            2,
            ((value_type::U32 as u32) << 16) | property_id::MEDIA_CONTROLLER_ID as u32,
            9,
        ));

        let renderer = HtmlRenderer::new();
        let img = renderer.render_fragment(&opcodes, &strings, 1);
        assert!(img.contains("alt=\"Home\""), "image alt from LABEL");
        assert!(img.contains("object-fit:cover;"), "ContentMode Fill -> cover");
        let video = renderer.render_fragment(&opcodes, &strings, 2);
        assert!(
            video.contains("<video data-pathland-id=\"2\" data-pathland-controller=\"9\" controls"),
            "video surface binds its controller and renders native controls: {video}"
        );
    }

    /// The media controller is non-visual + layout-transparent: the renderer emits
    /// an **off-flow** (`position:absolute`, 1×1) container holding the
    /// control-less playback element — the shell the DOM client hydrates. It is
    /// deliberately NOT `display:none`, which would silence the `<video>`'s audio.
    #[test]
    fn media_controller_renders_a_hidden_shell() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::MEDIA_CONTROLLER as u32, 0));
        let mut strings = Vec::new();
        strings.extend_from_slice(&(29u32).to_le_bytes());
        strings.extend_from_slice(b"https://example.com/track.mp3");
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::STRING as u32) << 16) | property_id::MEDIA_SOURCE as u32,
            0,
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_fragment(&opcodes, &strings, 1);
        assert!(
            html.contains(&format!(
                "data-pathland-id=\"1\" style=\"{MEDIA_CONTROLLER_SHELL_STYLE}\""
            )),
            "the controller is off-flow but still rendered (audio flows): {html}"
        );
        assert!(
            !html.contains("style=\"display:none\""),
            "the controller must NOT be display:none (it would silence the audio): {html}"
        );
        assert!(
            html.contains("<video data-pathland-media src=\"https://example.com/track.mp3\"></video>"),
            "the shell holds the control-less playback element: {html}"
        );
        assert!(!html.contains("<audio"), "audio has no view: {html}");
    }

    /// A custom `VideoStyle`: a `VIDEO` surface **with children** renders a
    /// `.pathland-media` wrapper holding the surface's control children, bound to
    /// its controller via `data-pathland-controller` (spec/MEDIA.md).
    #[test]
    fn video_surface_with_custom_children_renders_media_wrapper() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VIDEO as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::HSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 3, component_type::BUTTON as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 2, 3, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::U32 as u32) << 16) | property_id::MEDIA_CONTROLLER_ID as u32,
            9,
        ));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_fragment(&opcodes, &[], 1);
        assert!(
            html.contains("class=\"pathland-media\""),
            "a custom video body renders the media wrapper, not a bare <video>: {html}"
        );
        assert!(
            html.contains("data-pathland-controller=\"9\""),
            "the wrapper binds its controller: {html}"
        );
        assert!(
            html.contains("data-pathland-id=\"2\""),
            "the style body (control children) renders inside the wrapper: {html}"
        );
    }

    #[test]
    fn renders_grid_with_columns() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::GRID as u32, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::GRID_COLUMNS as u32,
            2.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, 0));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("grid-template-columns:repeat(2,minmax(0,1fr))"), "grid inline");
        assert!(!html.contains("width:2px"), "GRID_COLUMNS is a count, never a pixel box: {html}");
        assert!(html.contains("justify-items:start;align-items:start"), "default cell alignment");
        assert!(html.contains("justify-self:start;align-self:start;"), "cell wrapper positions per ALIGNMENT");
    }

    #[test]
    fn grid_rows_and_lazy_hgrid_tracks() {
        use pathland_core::value_type;
        use pathland_core::size;

        let build = |component: u16, columns: f32, rows: f32| {
            let mut opcodes = Vec::new();
            opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component as u32, 0));
            if columns != pathland_core::size::HUG_CONTENT {
                opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_PROPERTY, 0, 1,
                    ((value_type::F32 as u32) << 16) | property_id::GRID_COLUMNS as u32, columns.to_bits()));
            }
            if rows != pathland_core::size::HUG_CONTENT {
                opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_PROPERTY, 0, 1,
                    ((value_type::F32 as u32) << 16) | property_id::GRID_ROWS as u32, rows.to_bits()));
            }
            let renderer = HtmlRenderer::new();
            renderer.render_document(&opcodes, &[], 1)
        };

        // GRID with both counts: columns from GRID_COLUMNS, rows from GRID_ROWS.
        let g = build(component_type::GRID, 2.0, 3.0);
        assert!(g.contains("grid-template-columns:repeat(2,minmax(0,1fr))"), "grid columns: {g}");
        assert!(g.contains("grid-template-rows:repeat(3,minmax(0,1fr))"), "grid rows: {g}");
        assert!(!g.contains("width:2px") && !g.contains("height:3px"), "counts never pixels: {g}");

        // LAZY_HGRID: the fixed track is GRID_ROWS; columns auto-flow; GRID_COLUMNS is ignored.
        let h = build(component_type::LAZY_HGRID, 2.0, 3.0);
        assert!(h.contains("grid-template-rows:repeat(3,minmax(0,1fr))"), "hgrid rows: {h}");
        assert!(h.contains("grid-auto-flow:column;grid-auto-columns:minmax(0,1fr)"), "hgrid auto columns: {h}");
        assert!(!h.contains("grid-template-columns:repeat(2,minmax(0,1fr))"), "hgrid ignores GRID_COLUMNS: {h}");

        // FILL count = auto-fit (no template); a FILL WIDTH frame still expands (100%).
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::GRID as u32, 0));
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_PROPERTY, 0, 1,
            ((value_type::F32 as u32) << 16) | property_id::GRID_COLUMNS as u32, size::FILL.to_bits()));
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_PROPERTY, 0, 1,
            ((value_type::F32 as u32) << 16) | property_id::WIDTH as u32, size::FILL.to_bits()));
        let renderer = HtmlRenderer::new();
        let f = renderer.render_document(&opcodes, &[], 1);
        assert!(!f.contains("grid-template-columns:repeat"), "FILL count = auto-fit: {f}");
        assert!(f.contains("width:100%"), "FILL WIDTH frame expands: {f}");
    }

    #[test]
    fn grid_rows_flatten_explicit_rows() {
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::GRID as u32, 0));
        // Row 0: a 2-cell GRID_ROW.
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::GRID_ROW as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 3, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 4, component_type::TEXT as u32, 0));
        // Row 1: a short 1-cell GRID_ROW.
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 5, component_type::GRID_ROW as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 6, component_type::TEXT as u32, 0));
        // A bare cell auto-flows into row 2.
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 7, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, u32::MAX));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 2, 3, u32::MAX));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 2, 4, u32::MAX));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 5, u32::MAX));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 5, 6, u32::MAX));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 7, u32::MAX));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        // The widest row (2 cells) defines the equal-1fr columns.
        assert!(html.contains("grid-template-columns:repeat(2,minmax(0,1fr))"), "widest row defines columns: {html}");
        // Explicit placements: row 0 = [A,B], row 1 = [C], row 2 = [D].
        assert!(html.contains("grid-row:1;grid-column:1;"), "cell at (0,0): {html}");
        assert!(html.contains("grid-row:1;grid-column:2;"), "cell at (0,1): {html}");
        assert!(html.contains("grid-row:2;grid-column:1;"), "short row's cell at (1,0): {html}");
        assert!(html.contains("grid-row:3;grid-column:1;"), "bare cell auto-flows to row 2: {html}");
        // The GRID_ROW nodes themselves render nothing.
        assert!(!html.contains("GRID_ROW"), "GRID_ROW is structural: {html}");
    }

    #[test]
    fn grid_tracks_render_per_track_template() {
        use pathland_core::value_type;

        let mut strings = Vec::new();
        strings.extend_from_slice(&(25u32).to_le_bytes());
        strings.extend_from_slice(b"flex,fixed:80,adaptive:50");
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::GRID as u32, 0));
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_PROPERTY, 0, 1,
            ((value_type::STRING as u32) << 16) | property_id::GRID_TRACKS as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, u32::MAX));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);
        assert!(html.contains("grid-template-columns:1fr 80px minmax(50px,1fr);"), "track spec: {html}");
        assert!(!html.contains("grid-template-columns:repeat"), "tracks override the count: {html}");
    }

    #[test]
    fn zstack_2d_alignment_splits_h_and_v() {
        use pathland_core::value_type;
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::ZSTACK as u32, 0));
        // topTrailing = 7 → horizontal end, vertical start.
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_PROPERTY, 0, 1,
            ((value_type::F32 as u32) << 16) | property_id::ALIGNMENT as u32, 7.0f32.to_bits()));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, u32::MAX));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("justify-self:end;align-self:start;"), "topTrailing splits h/v: {html}");
    }

    #[test]
    fn grid_cells_stretch_when_fill_or_greedy() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::GRID as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_PROPERTY, 0, 2,
            ((value_type::F32 as u32) << 16) | property_id::WIDTH as u32, pathland_core::size::FILL.to_bits()));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 3, component_type::COLOR as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 4, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, u32::MAX));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 3, u32::MAX));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 4, u32::MAX));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        // A FILL-width cell stretches horizontally but keeps content height
        // (start vertically); a greedy COLOR stretches on both axes; a Hug cell
        // keeps its size and is positioned at start on both axes.
        assert!(html.contains("justify-self:stretch;align-self:stretch;"), "greedy COLOR stretches: {html}");
        assert!(html.contains("justify-self:stretch;align-self:start;"), "FILL-width cell: {html}");
        assert!(html.contains("justify-self:start;align-self:start;"), "Hug cell positioned: {html}");
    }

    #[test]
    fn scrollview_renders_only_the_first_child() {
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::SCROLLVIEW as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 3, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, u32::MAX));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 3, u32::MAX));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("data-pathland-id=\"2\""), "first child is the content: {html}");
        assert!(!html.contains("data-pathland-id=\"3\""), "extra children not rendered: {html}");
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
        assert!(zstack.contains("display:grid"), "zstack grid inline");
        assert!(zstack.contains("isolation:isolate"), "zstack scopes shell z-indexes: {zstack}");
        assert!(zstack.contains("width:max-content"), "zstack hugs to its largest child");
        assert!(zstack.contains("grid-area:1/1;z-index:0;"), "shell carries an explicit default z-index: {zstack}");
        assert!(zstack.contains("justify-self:start"), "zstack child positioned per ALIGNMENT");
        assert!(zstack.contains("grid-template-columns:minmax(0,1fr);grid-template-rows:minmax(0,1fr);"),
            "zstack tracks shrink below content min-height (minmax(0,1fr)): {zstack}");
    }

    #[test]
    fn zstack_child_z_index_lands_on_the_shell() {
        use pathland_core::value_type;
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::ZSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 3, component_type::TEXT as u32, 0));
        // The FIRST child has z-index 5: its shell must carry it so it draws
        // above the later sibling (draw-order override, spec/PRIMITIVES.md §ZStack).
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_PROPERTY, 0, 2,
            ((value_type::F32 as u32) << 16) | property_id::Z_INDEX as u32, 5.0f32.to_bits()));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, u32::MAX));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 3, u32::MAX));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("grid-area:1/1;z-index:5;"), "z-index on the child shell: {html}");
        assert!(html.contains("z-index:5"), "the child keeps its own z-index: {html}");
        // A sibling without Z_INDEX still carries an explicit default z-index.
        assert!(html.contains("grid-area:1/1;z-index:0;"), "sibling shell defaults to 0: {html}");
    }

    #[test]
    fn renders_progress_view_and_spinner() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::PROGRESS_VIEW as u32, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::PROGRESS as u32,
            0.5f32.to_bits(),
        ));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::PROGRESS_VIEW as u32, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_DATE, 0, 1, 19_723, 0));

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
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 2, 0, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::COLOR as u32) << 16) | property_id::COLOR_VALUE as u32,
            0xFF00_00FF,
        ));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::COLOR as u32, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 2, 0, 0));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);
        assert!(html.contains("<button data-pathland-id=\"1\" class=\"pathland-button\">"), "button uses the design-system class");
        assert!(html.contains("<span data-pathland-id=\"2\">A</span>"));
    }

    #[test]
    fn line_limit_clamps_text_to_n_lines() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::U32 as u32) << 16) | property_id::LINE_LIMIT as u32,
            1,
        ));
        let mut strings = Vec::new();
        strings.extend_from_slice(&(2u32).to_le_bytes());
        strings.extend_from_slice(b"Hi");
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 1, 0, 0));

        let html = HtmlRenderer::new().render_document(&opcodes, &strings, 1);
        assert!(
            html.contains("style=\"display:-webkit-box;overflow:hidden;-webkit-line-clamp:1;-webkit-box-orient:vertical;\""),
            "a positive LINE_LIMIT clamps the text to N lines: {html}"
        );
        // 0 = unlimited: no clamp style (the unset case above is unchanged).
        let mut no_limit = Vec::new();
        no_limit.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::TEXT as u32, 0));
        no_limit.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 1, 0, 0));
        assert!(
            !HtmlRenderer::new()
                .render_document(&no_limit, &strings, 1)
                .contains("line-clamp"),
            "no LINE_LIMIT means unlimited (no clamp)"
        );
    }

    #[test]
    fn content_fitting_contract_c7_c10() {
        use pathland_core::value_type;
        // C7 (wrap at a Fixed width), C8 (LINE_LIMIT → line-clamp tail ellipsis),
        // C9 (TRUNCATION_MODE positions the ellipsis under a clamp, never forces
        // single-line by itself), C10 (CLIPS_TO_BOUNDS → overflow:hidden).
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, pathland_core::APPEND));
        // Fixed width → wraps at the box (C7).
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            2,
            ((value_type::F32 as u32) << 16) | property_id::WIDTH as u32,
            120.0f32.to_bits(),
        ));
        // LINE_LIMIT=2 → clamp to 2 lines with a tail ellipsis (C8).
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            2,
            ((value_type::U32 as u32) << 16) | property_id::LINE_LIMIT as u32,
            2,
        ));
        // TRUNCATION_MODE=Head → still a 2-line clamp, no nowrap/ellipsis CSS
        // (CSS line-clamp tail-ellipsizes; head/middle are renderer-owned — C9).
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            2,
            ((value_type::F32 as u32) << 16) | property_id::TRUNCATION_MODE as u32,
            0.0f32.to_bits(),
        ));
        // CLIPS_TO_BOUNDS=1 → overflow:hidden (C10).
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            2,
            ((value_type::U8 as u32) << 16) | property_id::CLIPS_TO_BOUNDS as u32,
            1,
        ));
        let html = HtmlRenderer::new().render_document(&opcodes, &[], 1);
        assert!(html.contains("width:120px"), "C7 fixed-width wraps: {html}");
        assert!(html.contains("line-clamp:2"), "C8 LINE_LIMIT clamps: {html}");
        assert!(html.contains("overflow:hidden"), "C10 clips to bounds: {html}");
        assert!(!html.contains("white-space:nowrap"), "C9 no forced single line");
        assert!(!html.contains("text-overflow"), "C9 no standalone ellipsis");

        // TRUNCATION_MODE ALONE (no LINE_LIMIT) has no observable effect.
        let mut trunc_alone = Vec::new();
        trunc_alone.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::TEXT as u32, 0));
        trunc_alone.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::TRUNCATION_MODE as u32,
            0.0f32.to_bits(),
        ));
        let html = HtmlRenderer::new().render_document(&trunc_alone, &[], 1);
        assert!(!html.contains("line-clamp"), "TRUNCATION alone does not clamp");
        assert!(!html.contains("white-space:nowrap"), "TRUNCATION alone does not force single-line");
        assert!(!html.contains("text-overflow"), "TRUNCATION alone emits no ellipsis");
    }

    #[test]
    fn event_attrs_and_aria_are_emitted() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::BUTTON as u32, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::U32 as u32) << 16) | property_id::EVENT_LISTENERS as u32,
            pathland_core::listener::POINTER_DOWN | pathland_core::listener::POINTER_UP,
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::U32 as u32) << 16) | property_id::ACTION_ID as u32,
            42,
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
    fn layout_default_cross_axis_is_hug_not_stretch() {
        // A stack with NO ALIGNMENT must default to hug (flex-start), never
        // CSS `align-items: stretch` (LAYOUT.md conformance C5).
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("align-items:flex-start"), "default is hug: {}", html);
        assert!(!html.contains("align-items:stretch"), "no implicit stretch");
    }

    #[test]
    fn layout_fill_alignment_is_hug_positioning() {
        use pathland_core::value_type;

        // ALIGNMENT=Fill(3) means default (hug) positioning, not stretch.
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::ALIGNMENT as u32,
            3.0f32.to_bits(),
        ));
        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("align-items:flex-start"), "Fill=3 → hug: {}", html);
    }

    #[test]
    fn fit_slot_forwards_parent_alignment_into_its_box() {
        use pathland_core::value_type;

        // A `VStack(CENTER)` > SizeThatFits > HSTACK: the slot is a layout-transparent
        // container, so it must forward the parent's cross-axis alignment (`inherit`)
        // into its own box — otherwise its `align-self:stretch` box pins the candidate
        // to the start, regressing the centered player-bar groups (the reasons the
        // player bar moved its centered controls behind a fit slot). It keeps the
        // size-taking `flex:1 1 auto;align-self:stretch` + `min-width/height:0`
        // (the fitted width is measured from this box).
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        // HorizontalAlignment.CENTER = 1.
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::ALIGNMENT as u32,
            1.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::SIZE_THAT_FITS as u32, 0));
        // FIT_QUERY (LIST) → arena `[count=2][0][640]` → `data-pathland-fit="0,640"`.
        let mut strings = Vec::new();
        strings.extend_from_slice(&2u32.to_le_bytes());
        strings.extend_from_slice(&0.0f32.to_le_bytes());
        strings.extend_from_slice(&640.0f32.to_le_bytes());
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            2,
            ((value_type::LIST as u32) << 16) | property_id::FIT_QUERY as u32,
            0,
        ));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 3, component_type::HSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 2, 3, 0));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);
        assert!(html.contains("align-items:center"), "parent VStack centers: {}", html);
        assert!(html.contains("data-pathland-fit=\"0,640\""), "fit mirror rides the slot: {}", html);
        let slot = "class=\"pathland-ftf\"";
        let pos_before = html.find(slot).expect("slot div");
        let snippet = &html[pos_before..html[pos_before..].find('>').unwrap() + pos_before + 1];
        assert!(
            snippet.contains("display:flex;")
                && snippet.contains("flex-direction:inherit;")
                && snippet.contains("align-items:inherit;")
                && snippet.contains("justify-content:inherit;")
                && snippet.contains("flex:1 1 auto;")
                && snippet.contains("align-self:stretch;")
                && snippet.contains("min-width:0;")
                && snippet.contains("min-height:0;"),
            "slot forwards the parent flex settings while staying size-taking: {snippet}"
        );
        assert!(!snippet.contains("align-items:stretch;"), "forwarded, not stretch-only: {snippet}");
        assert!(html.contains("display:flex;flex-direction:row;"), "the candidate HSTACK stays a row");
    }

    #[test]
    fn snapshot_fit_slot_renders_all_candidates_with_container_query_rules() {
        use pathland_core::value_type;

        // The SSR full-snapshot form (issue #141): a `SIZE_THAT_FITS` carrying
        // MULTIPLE children (every candidate) renders each branch in a
        // `data-pathland-fit-cand`-gated wrapper, makes the slot a `@container`,
        // and emits the band rules in a `<style data-pathland-fit>` element — so a
        // no-JS first paint shows the candidate matching the slot's width. The
        // child at position `i` is candidate `i` (child order = FIT_QUERY order).
        // The single-child (live) form stays a plain `pathland-ftf` (tested above).
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::SIZE_THAT_FITS as u32, 0));
        // FIT_QUERY (LIST) → `[count=2][0][640]` → `data-pathland-fit="0,640"`.
        let mut strings = Vec::new();
        strings.extend_from_slice(&2u32.to_le_bytes());
        strings.extend_from_slice(&0.0f32.to_le_bytes());
        strings.extend_from_slice(&640.0f32.to_le_bytes());
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::LIST as u32) << 16) | property_id::FIT_QUERY as u32,
            0,
        ));
        // Two candidate branches: HSTACK(2) "wide" and VSTACK(3) "compact".
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::HSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 3, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 3, 1));

        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &strings, 1);
        // The slot becomes a container with a unique name.
        assert!(html.contains("container-type:inline-size;"), "slot is a container: {}", html);
        assert!(html.contains("container-name:pf-1;"), "slot has a unique container name: {}", html);
        // Both candidates ride the snapshot (gated, mirroring the fit table + count).
        assert!(html.contains("data-pathland-fit-cands=\"2\""), "candidate count mirrors: {}", html);
        assert!(html.contains("data-pathland-fit-cand=\"0\""), "candidate 0 wrapper: {}", html);
        assert!(html.contains("data-pathland-fit-cand=\"1\""), "candidate 1 wrapper: {}", html);
        assert!(html.contains("data-pathland-fit-cand-id=\"2\""), "wrapper carries candidate id 2: {}", html);
        assert!(html.contains("data-pathland-fit-cand-id=\"3\""), "wrapper carries candidate id 3: {}", html);
        // Candidate 0 (threshold 0) is the default-visible fallback; candidate 1 hidden.
        assert!(html.contains("data-pathland-fit-cand=\"0\" data-pathland-fit-cand-id=\"2\" style=\"display:contents\""));
        assert!(html.contains("data-pathland-fit-cand=\"1\" data-pathland-fit-cand-id=\"3\" style=\"display:none\""));
        // The candidate content keeps its own id (property deltas land correctly).
        assert!(html.contains("<div data-pathland-id=\"2\" class=") || html.contains("<div data-pathland-id=\"2\""),
            "candidate content keeps its id: {}", html);
        // The band rules live in a dedicated head element.
        let fit_style = html.find("<style data-pathland-fit>").expect("band rules style element");
        let style_close = html[fit_style..].find("</style>").unwrap() + fit_style;
        let rules = &html[fit_style..style_close];
        assert!(rules.contains("@container pf-1 (min-width: 640px)"), "band rule for candidate 1: {rules}");
        // The winner's band hides the OTHER candidates only (never the winner).
        assert!(rules.contains("[data-pathland-id=\"1\"] > [data-pathland-fit-cand=\"0\"] { display: none !important; }"), "{rules}");
        assert!(rules.contains("[data-pathland-id=\"1\"] > [data-pathland-fit-cand=\"1\"] { display: contents !important; }"), "{rules}");
        assert!(!rules.contains("[data-pathland-fit-cand] { display: none"), "the hide selector never targets all candidates: {rules}");
        // The live/single-child form still produces NO band rules.
        let mut single = Vec::new();
        single.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::SIZE_THAT_FITS as u32, 0));
        single.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 3, 0));
        single.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 3, component_type::VSTACK as u32, 0));
        let html_single = renderer.render_document(&single, &strings, 1);
        assert!(!html_single.contains("data-pathland-fit-cands"), "single-child form unchanged: {}", html_single);
        assert!(!html_single.contains("data-pathland-fit-cand"), "single-child form unchanged: {}", html_single);
        assert!(!html_single.contains("<style data-pathland-fit>"), "no band rules for a single child");
    }

    #[test]
    fn snapshot_fit_slot_three_candidates_one_visible_ascending_bands() {
        use pathland_core::value_type;

        // Three candidates with thresholds [0, 600, 900] (ascending): exactly one
        // band per distinct non-fallback threshold, each showing its candidate and
        // hiding the other two; no band for T_0 (the inline fallback).
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::SIZE_THAT_FITS as u32, 0));
        let mut strings = Vec::new();
        strings.extend_from_slice(&3u32.to_le_bytes());
        strings.extend_from_slice(&0.0f32.to_le_bytes());
        strings.extend_from_slice(&600.0f32.to_le_bytes());
        strings.extend_from_slice(&900.0f32.to_le_bytes());
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_PROPERTY, 0, 1, ((value_type::LIST as u32) << 16) | property_id::FIT_QUERY as u32, 0));
        for (child, idx) in [(2u32, 0u32), (3, 1), (4, 2)] {
            opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, child, component_type::VSTACK as u32, 0));
            opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, child, idx));
        }

        let html = HtmlRenderer::new().render_document(&opcodes, &strings, 1);
        // Wrapper order = FIT_QUERY order; only candidate 0 is default-visible.
        assert!(html.contains("data-pathland-fit-cands=\"3\""), "{html}");
        assert!(html.contains("data-pathland-fit-cand=\"0\" data-pathland-fit-cand-id=\"2\" style=\"display:contents\""), "{html}");
        assert!(html.contains("data-pathland-fit-cand=\"1\" data-pathland-fit-cand-id=\"3\" style=\"display:none\""), "{html}");
        assert!(html.contains("data-pathland-fit-cand=\"2\" data-pathland-fit-cand-id=\"4\" style=\"display:none\""), "{html}");

        let fit_style = html.find("<style data-pathland-fit>").expect("band style element");
        let close = html[fit_style..].find("</style>").unwrap() + fit_style;
        let rules = &html[fit_style..close];
        assert!(rules.contains("@container pf-1 (min-width: 600px)"), "{rules}");
        assert!(rules.contains("@container pf-1 (min-width: 900px)"), "{rules}");
        assert!(!rules.contains("min-width: 0px"), "no band for T_0: {rules}");
        assert!(
            rules.find("min-width: 600px").unwrap() < rules.find("min-width: 900px").unwrap(),
            "bands ascending: {rules}"
        );
        // Band 600: show candidate 1, hide 0 and 2.
        assert!(rules.contains("[data-pathland-id=\"1\"] > [data-pathland-fit-cand=\"1\"] { display: contents !important; }"), "{rules}");
        assert!(rules.contains("[data-pathland-id=\"1\"] > [data-pathland-fit-cand=\"0\"] { display: none !important; }"), "{rules}");
        assert!(rules.contains("[data-pathland-id=\"1\"] > [data-pathland-fit-cand=\"2\"] { display: none !important; }"), "{rules}");
        // Band 900: show candidate 2, hide 0 and 1.
        assert!(rules.contains("[data-pathland-id=\"1\"] > [data-pathland-fit-cand=\"2\"] { display: contents !important; }"), "{rules}");
        assert!(!rules.contains("[data-pathland-fit-cand] { display: none"), "never a hide-all selector: {rules}");
    }

    #[test]
    fn snapshot_fit_slot_invalid_table_degrades_to_fallback_child() {
        use pathland_core::value_type;

        // A multi-child slot whose FIT_QUERY table violates the candidate-identity
        // contract (here: one threshold for two children) cannot be gated. The
        // renderer must NOT emit wrappers/bands and must render the threshold-0
        // fallback child only — never ungated overlapping candidates.
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::SIZE_THAT_FITS as u32, 0));
        let mut strings = Vec::new();
        strings.extend_from_slice(&1u32.to_le_bytes());
        strings.extend_from_slice(&0.0f32.to_le_bytes());
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_PROPERTY, 0, 1, ((value_type::LIST as u32) << 16) | property_id::FIT_QUERY as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 3, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 3, 1));

        let html = HtmlRenderer::new().render_document(&opcodes, &strings, 1);
        assert!(!html.contains("data-pathland-fit-cands"), "no gating: {html}");
        assert!(!html.contains("data-pathland-fit-cand"), "no wrappers: {html}");
        assert!(!html.contains("<style data-pathland-fit>"), "no bands: {html}");
        assert!(html.contains("data-pathland-id=\"2\""), "fallback child renders: {html}");
        assert!(!html.contains("data-pathland-id=\"3\""), "invalid candidates are not rendered ungated: {html}");
    }

    #[test]
    fn fixed_child_keeps_points_box_in_hug_container() {
        use pathland_core::value_type;

        // C2: a 220×220 image in a vertical stack keeps its exact points box
        // (width/height in px), positioned (flex-start) — never stretched.
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::IMAGE as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, pathland_core::APPEND));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            2,
            ((value_type::F32 as u32) << 16) | property_id::WIDTH as u32,
            220.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            2,
            ((value_type::F32 as u32) << 16) | property_id::HEIGHT as u32,
            220.0f32.to_bits(),
        ));
        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("align-items:flex-start"), "hug container");
        assert!(html.contains("width:220px;height:220px"), "exact box: {}", html);
    }

    #[test]
    fn divider_spans_cross_axis_and_scrollview_is_greedy() {
        // DIVIDER is layout-greedy on the cross axis (width:100%); SCROLLVIEW
        // is greedy on both axes (flex + align-self:stretch) — LAYOUT.md.
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::DIVIDER as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, pathland_core::APPEND));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 3, component_type::SCROLLVIEW as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 3, pathland_core::APPEND));
        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("width:100%"), "divider greedy: {}", html);
        assert!(
            html.contains("flex:1 1 auto;align-self:stretch;overflow:auto"),
            "scrollview greedy: {}",
            html
        );
    }

    #[test]
    fn fill_propagates_through_a_hug_stack() {
        use pathland_core::{size, value_type};
        // A Hug VStack with a FILL-width child: the child stretches on the
        // cross axis (`align-self:stretch`) and the stack itself becomes
        // full-width (`width:100%` propagation) — SwiftUI/Compose parity.
        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::BUTTON as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, pathland_core::APPEND));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            2,
            ((value_type::F32 as u32) << 16) | property_id::WIDTH as u32,
            size::FILL.to_bits(),
        ));
        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&opcodes, &[], 1);
        assert!(html.contains("width:100%"), "fill propagation: {}", html);
        assert!(html.contains("align-self:stretch"), "cross-axis fill: {}", html);
        // A Hug stack with no FILL child does NOT propagate.
        let mut hug = Vec::new();
        hug.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        hug.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        hug.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, pathland_core::APPEND));
        let html_hug = renderer.render_document(&hug, &[], 1);
        assert!(!html_hug.contains("width:100%"), "no propagation: {}", html_hug);
    }

    #[test]
    fn text_field_secure_uses_password_input() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::TEXT_FIELD as u32, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::COLOR as u32) << 16) | property_id::COLOR as u32,
            0xFF11_2233,
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::FONT_SIZE as u32,
            18.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
    fn shadow_renders_with_a_renderer_default_color_when_only_radius_is_set() {
        use pathland_core::value_type;

        // `shadow(radius)` carries only `SHADOW_RADIUS` (the color is
        // renderer-owned), so the SSR must still draw a box-shadow with the
        // default color — mirroring the DOM client's `rgba(0,0,0,0.3)`.
        let render_f32 = |props: &[(u16, f32)]| {
            let mut opcodes = vec![Opcode::new(
                category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0,
            )];
            for &(prop, value) in props {
                opcodes.push(Opcode::new(
                    category::PARAMETER, parameter::SET_PROPERTY, 0, 1,
                    ((value_type::F32 as u32) << 16) | prop as u32, value.to_bits(),
                ));
            }
            HtmlRenderer::new().render_fragment(&opcodes, &[], 1)
        };

        // Radius only → the renderer default color.
        let html = render_f32(&[(property_id::SHADOW_RADIUS, 10.0)]);
        assert!(
            html.contains("box-shadow:rgba(0,0,0,0.3) 0px 0px 10px;"),
            "radius-only shadow draws with the default color: {html}"
        );
        // An offset alone also draws a shadow.
        assert!(
            render_f32(&[(property_id::SHADOW_X, 4.0), (property_id::SHADOW_Y, 4.0)]).contains("box-shadow:"),
            "an offset alone draws a shadow"
        );
        // Unset / zero radius → no shadow.
        assert!(!render_f32(&[]).contains("box-shadow:"), "no shadow when unset");
        assert!(
            !render_f32(&[(property_id::SHADOW_RADIUS, 0.0)]).contains("box-shadow:"),
            "radius 0 → no shadow"
        );

        // An explicit `SHADOW_COLOR` wins over the default.
        let mut opcodes = vec![Opcode::new(
            category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0,
        )];
        opcodes.push(Opcode::new(
            category::PARAMETER, parameter::SET_PROPERTY, 0, 1,
            ((value_type::F32 as u32) << 16) | property_id::SHADOW_RADIUS as u32, 8.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER, parameter::SET_PROPERTY, 0, 1,
            ((value_type::COLOR as u32) << 16) | property_id::SHADOW_COLOR as u32, 0xFF00_0000,
        ));
        let explicit = HtmlRenderer::new().render_fragment(&opcodes, &[], 1);
        assert!(explicit.contains("box-shadow:rgba(0,0,0,1) 0px 0px 8px"), "explicit color wins: {explicit}");
    }

    #[test]
    fn text_style_applies_default_typography_and_explicit_overrides_win() {
        use pathland_core::text_style;
        use pathland_core::value_type;

        let render = |props: &[(u16, f32)]| {
            let mut opcodes = Vec::new();
            opcodes.push(Opcode::new(
                category::TREE,
                tree::CREATE_NODE,
                0,
                1,
                component_type::TEXT as u32,
                0,
            ));
            for (prop, value) in props {
                opcodes.push(Opcode::new(
                    category::PARAMETER,
                    parameter::SET_PROPERTY,
                    0,
                    1,
                    ((value_type::F32 as u32) << 16) | *prop as u32,
                    value.to_bits(),
                ));
            }
            HtmlRenderer::new().render_document(&opcodes, &[], 1)
        };

        // A predefined typography emits its renderer-owned size/weight.
        let html = render(&[(property_id::TEXT_STYLE, text_style::TITLE2 as f32)]);
        assert!(html.contains("<h3"), "TITLE2 is a heading: {}", html);
        assert!(html.contains("font-size:22px"), "style size: {}", html);
        assert!(html.contains("font-weight:700"), "style weight: {}", html);

        // An explicit FONT_SIZE overrides the typography's size.
        let html = render(&[
            (property_id::TEXT_STYLE, text_style::TITLE2 as f32),
            (property_id::FONT_SIZE, 11.0),
        ]);
        assert!(html.contains("font-size:11px"), "explicit size wins: {}", html);
    }

    #[test]
    fn style_css_expands_fill_hints_to_percent() {
        use pathland_core::size;

        let mut opcodes = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::WIDTH as u32,
            size::FILL.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::SPACING as u32,
            4.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
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
        // System font stack (no external font loading; the family resolves to
        // the platform's UI font).
        assert!(css.contains("font-feature-settings: 'liga' 1, 'calt' 1"), "Chrome ligature fix");
        assert!(css.contains("--pl-font-body-family: system-ui, -apple-system"), "system font stack");
        assert!(!css.contains("Inter"), "no bundled/external font family");
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
        assert_eq!(count(css, "--pl-space-base"), 3, "space.base has light + dark (platform + forced) defaults");
        // Canonical typography tokens (spec/TOKENS.md font.body.*).
        for var in [
            "--pl-font-body-size",
            "--pl-font-body-weight",
            "--pl-font-body-family",
        ] {
            assert!(css.contains(var), "missing {var}");
        }
        // Canonical control/button/input + per-component tokens so app
        // overrides retheme components.
        for var in [
            "--pl-toggle-accent",
            "--pl-slider-accent",
            "--pl-progress-fill",
            "--pl-gauge-fill",
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
    fn document_head_loads_no_external_fonts() {
        let renderer = HtmlRenderer::new();
        let html = renderer.render_document(&[], &[], 0);
        assert!(!html.contains("rsms.me"), "no font CDN link: {html}");
        assert!(!html.contains("preconnect"), "no preconnect: {html}");
        assert!(
            !html.contains("<link rel=\"stylesheet\""),
            "no external stylesheets (fonts are system-native): {html}"
        );
        // The built-in <style> block still arrives before the body.
        let style = html.find("<style>").unwrap();
        let body = html.find("<body>").unwrap();
        assert!(style < body, "style block before body");
    }

    #[test]
    fn renders_stack_with_gap_and_structural_child_inline() {
        use pathland_core::value_type;

        let mut opcodes = Vec::new();
        let mut strings = Vec::new();
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            1,
            ((value_type::F32 as u32) << 16) | property_id::SPACING as u32,
            12.0f32.to_bits(),
        ));
        opcodes.push(Opcode::new(category::TREE, tree::CREATE_NODE, 0, 2, component_type::TEXT as u32, 0));
        opcodes.push(Opcode::new(category::TREE, tree::INSERT_CHILD, 0, 1, 2, 0));
        opcodes.push(Opcode::new(
            category::PARAMETER,
            parameter::SET_PROPERTY,
            0,
            2,
            ((value_type::F32 as u32) << 16) | property_id::PADDING as u32,
            8.0f32.to_bits(),
        ));
        strings.extend_from_slice(&(2u32).to_le_bytes());
        strings.extend_from_slice(b"Hi");
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 2, 0, 0));

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
                category::PARAMETER,
                parameter::SET_PROPERTY,
                0,
                1,
                ((vt as u32) << 16) | prop as u32,
                value,
            ));
        }
        strings.extend_from_slice(&(2u32).to_le_bytes());
        strings.extend_from_slice(b"Hi");
        opcodes.push(Opcode::new(category::PARAMETER, parameter::SET_TEXT, 0, 1, 0, 0));

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
            Opcode::new(category::PARAMETER, parameter::SET_DESIGN_TOKEN, 0, 0, value_type::COLOR as u32, 0xFF_2563EB),
            Opcode::new(
                category::PARAMETER,
                parameter::SET_DESIGN_TOKEN,
                0,
                dark_offset,
                value_type::COLOR as u32,
                0xFF_60A5FA,
            ),
        ];
        let html = HtmlRenderer::new().render_document(&opcodes, &strings, 1);
        assert!(html.contains(":root{--pl-color-primary:rgba(37,99,235,1);}"), "base override: {html}");
        assert!(
            html.contains("@media (prefers-color-scheme: dark){:root:not([data-pathland-scheme]){--pl-color-primary:rgba(37,99,235,1);--pl-color-primary:rgba(96,165,250,1);}}"),
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
            Opcode::new(category::PARAMETER, parameter::SET_DESIGN_TOKEN, 0, 0, value_type::COLOR as u32, 0xFF_2563EB),
            Opcode::new(
                category::PARAMETER,
                parameter::SET_DESIGN_TOKEN,
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
            html.contains(":root[data-pathland-scheme=\"dark\"]{--pl-color-primary:rgba(37,99,235,1);--pl-color-primary:rgba(96,165,250,1);}"),
            "dark override scoped under the forced-dark attribute: {html}"
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
            category::PARAMETER,
            parameter::SET_DESIGN_TOKEN,
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
            category::PARAMETER,
            parameter::SET_DESIGN_TOKEN,
            0,
            0,
            value_type::STRING as u32,
            20,
        )];
        let html = HtmlRenderer::new().render_document(&opcodes, &strings, 1);
        assert!(html.contains(":root{--pl-font-body-family:'Inter';}"), "string token: {html}");
    }

    #[test]
    fn set_color_scheme_sets_the_document_attr_and_skips_the_platform_query() {
        let opcodes = vec![Opcode::new(
            category::META,
            pathland_core::meta::SET_COLOR_SCHEME,
            0,
            pathland_core::color_scheme_mode::DARK,
            0,
            0,
        )];
        let html = HtmlRenderer::new().render_document(&opcodes, &[], 0);
        assert!(html.contains("<html data-pathland-scheme=\"dark\">"), "forced-dark attr: {html}");
        let light = vec![Opcode::new(
            category::META,
            pathland_core::meta::SET_COLOR_SCHEME,
            0,
            pathland_core::color_scheme_mode::LIGHT,
            0,
            0,
        )];
        let html = HtmlRenderer::new().render_document(&light, &[], 0);
        assert!(html.contains("<html data-pathland-scheme=\"light\">"), "forced-light attr: {html}");
    }

    #[test]
    fn explicit_dark_token_ref_uses_the_forced_dark_variable() {
        let strings = string_section(&["dark.color.surface"]);
        let opcodes = vec![
            Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::TEXT as u32, 0),
            Opcode::new(
                category::PARAMETER,
                parameter::SET_PROPERTY,
                0,
                1,
                ((value_type::DESIGN_TOKEN as u32) << 16) | property_id::COLOR as u32,
                0,
            ),
        ];
        let html = HtmlRenderer::new().render_fragment(&opcodes, &strings, 1);
        assert!(html.contains("var(--pl-dark-color-surface, unset)"), "forced dark ref: {html}");
    }

    #[test]
    fn reset_theme_drops_earlier_overrides() {
        let strings = string_section(&["color.primary"]);
        let opcodes = vec![
            Opcode::new(category::PARAMETER, parameter::SET_DESIGN_TOKEN, 0, 0, value_type::COLOR as u32, 0xFF_2563EB),
            Opcode::new(category::META, pathland_core::meta::RESET_THEME, 0, 0, 0, 0),
        ];
        let html = HtmlRenderer::new().render_document(&opcodes, &strings, 0);
        assert!(!html.contains("data-pathland-tokens"), "no override style element after reset: {html}");
    }

    #[test]
    fn design_token_property_ref_renders_var() {
        let strings = string_section(&["color.primary"]);
        let opcodes = vec![
            Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::TEXT as u32, 0),
            Opcode::new(
                category::PARAMETER,
                parameter::SET_PROPERTY,
                0,
                1,
                ((value_type::DESIGN_TOKEN as u32) << 16) | property_id::COLOR as u32,
                0,
            ),
        ];
        let html = HtmlRenderer::new().render_fragment(&opcodes, &strings, 1);
        assert!(html.contains("color:var(--pl-color-primary, unset);"), "token ref: {html}");
    }

    #[test]
    fn generative_space_ref_renders_calc() {
        let strings = string_section(&["space.2"]);
        let opcodes = vec![
            Opcode::new(category::TREE, tree::CREATE_NODE, 0, 1, component_type::VSTACK as u32, 0),
            Opcode::new(
                category::PARAMETER,
                parameter::SET_PROPERTY,
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
