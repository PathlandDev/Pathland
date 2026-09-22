//! Debug-mode HTML comments describing each rendered node.
//!
//! When enabled (`HtmlRenderer::with_debug_comments(true)`), every rendered
//! node is prefixed with an HTML comment naming its component type and the
//! modifiers (style properties) applied to it:
//!
//! ```html
//! <!-- #1 VStack: spacing=4, alignment=Fill, width=FILL -->
//! ```
//!
//! This is a renderer debugging aid — a pure function of the decoded node,
//! never protocol surface. The comments are valid HTML: string content is
//! scrubbed so a comment never contains `--` or ends with `-`.

use std::fmt::Write as _;

use pathland_core::{component_type, property_id, size, value_type, value_type_for};

use crate::Node;

/// A stable, human-friendly name for a component type id.
#[must_use]
pub fn component_name(component: u16) -> &'static str {
    match component {
        component_type::TEXT => "Text",
        component_type::IMAGE => "Image",
        component_type::COLOR => "Color",
        component_type::SHAPE => "Shape",
        component_type::DIVIDER => "Divider",
        component_type::SPACER => "Spacer",
        component_type::PROGRESS_VIEW => "ProgressView",
        component_type::GAUGE => "Gauge",
        component_type::VSTACK => "VStack",
        component_type::HSTACK => "HStack",
        component_type::ZSTACK => "ZStack",
        component_type::GRID => "Grid",
        component_type::SCROLLVIEW => "ScrollView",
        component_type::LAZY_VGRID => "LazyVGrid",
        component_type::LAZY_HGRID => "LazyHGrid",
        component_type::LAZY_VSTACK => "LazyVStack",
        component_type::LAZY_HSTACK => "LazyHStack",
        component_type::BUTTON => "Button",
        component_type::TEXT_FIELD => "TextField",
        component_type::TEXT_EDITOR => "TextEditor",
        component_type::TOGGLE => "Toggle",
        component_type::SLIDER => "Slider",
        component_type::STEPPER => "Stepper",
        component_type::DATE_PICKER => "DatePicker",
        component_type::PICKER => "Picker",
        component_type::MENU => "Menu",
        component_type::COLOR_PICKER => "ColorPicker",
        component_type::COMMENT => "Comment",
        _ => "Unknown",
    }
}

/// A stable, human-friendly name for a property id (SwiftUI-style modifier
/// name where one exists).
#[must_use]
pub fn property_name(prop: u16) -> &'static str {
    match prop {
        property_id::SPACING => "spacing",
        property_id::ALIGNMENT => "alignment",
        property_id::CONTENT_MARGINS => "contentMargins",
        property_id::TEXT => "text",
        property_id::LINE_LIMIT => "lineLimit",
        property_id::TEXT_ALIGNMENT => "textAlignment",
        property_id::TRUNCATION_MODE => "truncationMode",
        property_id::SHAPE_KIND => "shapeKind",
        property_id::OFFSET_X => "offsetX",
        property_id::OFFSET_Y => "offsetY",
        property_id::POSITION_X => "positionX",
        property_id::POSITION_Y => "positionY",
        property_id::MIN_WIDTH => "minWidth",
        property_id::IDEAL_WIDTH => "idealWidth",
        property_id::MAX_WIDTH => "maxWidth",
        property_id::MIN_HEIGHT => "minHeight",
        property_id::IDEAL_HEIGHT => "idealHeight",
        property_id::MAX_HEIGHT => "maxHeight",
        property_id::FIXED_SIZE_HORIZONTAL => "fixedSizeHorizontal",
        property_id::FIXED_SIZE_VERTICAL => "fixedSizeVertical",
        property_id::LAYOUT_PRIORITY => "layoutPriority",
        property_id::ASPECT_RATIO => "aspectRatio",
        property_id::CONTENT_MODE => "contentMode",
        property_id::MINIMUM_SCALE_FACTOR => "minimumScaleFactor",
        property_id::BACKGROUND_COLOR => "backgroundColor",
        property_id::IMAGE_SOURCE => "imageSource",
        property_id::BORDER_WIDTH => "borderWidth",
        property_id::BORDER_COLOR => "borderColor",
        property_id::BORDER_RADIUS => "borderRadius",
        property_id::PADDING_STYLE => "paddingStyle",
        property_id::FONT_SIZE => "fontSize",
        property_id::FONT_WEIGHT => "fontWeight",
        property_id::FONT_FAMILY => "fontFamily",
        property_id::COLOR => "color",
        property_id::WIDTH => "width",
        property_id::HEIGHT => "height",
        property_id::OPACITY => "opacity",
        property_id::VISIBLE => "visible",
        property_id::Z_INDEX => "zIndex",
        property_id::CLIPS_TO_BOUNDS => "clipsToBounds",
        property_id::PADDING => "padding",
        property_id::PADDING_TOP => "paddingTop",
        property_id::PADDING_RIGHT => "paddingRight",
        property_id::PADDING_BOTTOM => "paddingBottom",
        property_id::PADDING_LEFT => "paddingLeft",
        property_id::BORDER_EDGES => "borderEdges",
        property_id::FONT_STYLE => "fontStyle",
        property_id::FONT_DESIGN => "fontDesign",
        property_id::FONT_WIDTH => "fontWidth",
        property_id::KERNING => "kerning",
        property_id::TRACKING => "tracking",
        property_id::BASELINE_OFFSET => "baselineOffset",
        property_id::LINE_SPACING => "lineSpacing",
        property_id::TEXT_CASE => "textCase",
        property_id::UNDERLINE => "underline",
        property_id::STRIKETHROUGH => "strikethrough",
        property_id::SHADOW_COLOR => "shadowColor",
        property_id::SHADOW_RADIUS => "shadowRadius",
        property_id::SHADOW_X => "shadowX",
        property_id::SHADOW_Y => "shadowY",
        property_id::BLUR_RADIUS => "blurRadius",
        property_id::SATURATION => "saturation",
        property_id::CONTRAST => "contrast",
        property_id::BRIGHTNESS => "brightness",
        property_id::GRAYSCALE => "grayscale",
        property_id::HUE_ROTATION => "hueRotation",
        property_id::COLOR_MULTIPLY => "colorMultiply",
        property_id::COLOR_INVERT => "colorInvert",
        property_id::ROTATION_DEGREES => "rotation",
        property_id::SCALE => "scale",
        property_id::ALLOWS_HIT_TESTING => "allowsHitTesting",
        property_id::TINT => "tint",
        property_id::TRANSITION => "transition",
        property_id::ROLE => "role",
        property_id::STATE => "state",
        property_id::ENABLED => "enabled",
        property_id::SELECTED => "selected",
        property_id::EVENT_LISTENERS => "eventListeners",
        property_id::VALUE => "value",
        property_id::MIN_VALUE => "minValue",
        property_id::MAX_VALUE => "maxValue",
        property_id::LABEL => "label",
        property_id::PROMPT => "prompt",
        property_id::STEP_VALUE => "step",
        property_id::CONTROL_SIZE => "controlSize",
        property_id::IS_SECURE => "isSecure",
        property_id::PROGRESS => "progress",
        property_id::IS_INDETERMINATE => "isIndeterminate",
        property_id::SELECTION => "selection",
        property_id::COLOR_VALUE => "colorValue",
        property_id::DATE_PICKER_MODE => "datePickerMode",
        property_id::PICKER_STYLE => "pickerStyle",
        property_id::ACTION_ID => "actionId",
        property_id::BINDING_ID => "bindingId",
        property_id::TOGGLE_STYLE => "toggleStyle",
        property_id::ROUTE => "route",
        property_id::NAV_DEPTH => "navDepth",
        property_id::NAV_CHROME => "navChrome",
        _ => "unknown",
    }
}

/// The debug comment for a node: its stable id, component type, and (when
/// present) the modifiers applied, e.g.
/// `<!-- #1 VStack: spacing=4, alignment=Fill, width=FILL -->`.
///
/// Only the *content* is scrubbed (never the `-->` terminator), so a comment
/// always stays valid HTML.
#[must_use]
pub fn node_comment(id: u32, node: &Node) -> String {
    let mut content = format!(" #{} {}", id, component_name(node.component));
    if let Some(text) = node.text.as_deref() {
        if !text.is_empty() {
            content.push(' ');
            let _ = write!(content, "{text:?}");
        }
    }
    if !node.properties.is_empty() {
        let props: Vec<String> = node
            .properties
            .keys()
            .map(|prop| format!("{}={}", property_name(*prop), format_value(node, *prop)))
            .collect();
        let _ = write!(content, ": {}", props.join(", "));
    }
    format!("<!--{} -->", scrub(&content))
}

/// Format a property value for a comment, decoded by its protocol value type.
/// A `DESIGN_TOKEN` reference renders as its token path; everything else is
/// decoded from the stored bits.
fn format_value(node: &Node, prop: u16) -> String {
    if let Some(path) = node.token_refs.get(&prop) {
        return path.clone();
    }
    let Some(bits) = node.properties.get(&prop).copied() else {
        return "?".to_string();
    };
    match value_type_for(prop) {
        value_type::STRING => node
            .strings
            .get(&prop)
            .map(|s| format!("{s:?}"))
            .unwrap_or_else(|| "?".to_string()),
        value_type::COLOR => format!("#{bits:08X}"),
        value_type::U8 => {
            if is_bool_prop(prop) {
                (bits != 0).to_string()
            } else {
                bits.to_string()
            }
        }
        value_type::U32 => {
            if is_mask_prop(prop) {
                format!("0x{bits:08X}")
            } else {
                bits.to_string()
            }
        }
        _ => format_f32(f32::from_bits(bits), prop),
    }
}

/// Format an `F32` property value: `WIDTH`/`HEIGHT` sentinels decode to
/// `FILL`/`HUG`; well-known enum codes decode to their names; otherwise a
/// trimmed number.
fn format_f32(v: f32, prop: u16) -> String {
    if prop == property_id::WIDTH || prop == property_id::HEIGHT {
        if v == size::FILL {
            return "FILL".to_string();
        }
        if v == size::HUG_CONTENT {
            return "HUG".to_string();
        }
    }
    if let Some(name) = enum_name(prop, v) {
        return name.to_string();
    }
    format!("{v}")
}

/// Decode a well-known enum property (its numeric code as an `F32`) to a
/// friendly name; `None` when the property isn't a known enum or the code is
/// unknown (the caller then shows the raw number).
fn enum_name(prop: u16, v: f32) -> Option<&'static str> {
    let code = v.round() as i32;
    match prop {
        property_id::ALIGNMENT => match code {
            0 => Some("Leading"),
            1 => Some("Center"),
            2 => Some("Trailing"),
            3 => Some("Fill"),
            _ => None,
        },
        property_id::TEXT_ALIGNMENT => match code {
            0 => Some("Leading"),
            1 => Some("Center"),
            2 => Some("Trailing"),
            _ => None,
        },
        property_id::TRUNCATION_MODE => match code {
            0 => Some("Head"),
            1 => Some("Middle"),
            2 => Some("Tail"),
            _ => None,
        },
        property_id::SHAPE_KIND => match code {
            0 => Some("Circle"),
            1 => Some("Rectangle"),
            2 => Some("RoundedRectangle"),
            3 => Some("Capsule"),
            4 => Some("Ellipse"),
            5 => Some("Path"),
            _ => None,
        },
        property_id::CONTENT_MODE => match code {
            0 => Some("Fit"),
            1 => Some("Fill"),
            _ => None,
        },
        property_id::TRANSITION => match code {
            0 => Some("None"),
            1 => Some("PlatformDefault"),
            2 => Some("Fade"),
            3 => Some("Slide"),
            4 => Some("Scale"),
            _ => None,
        },
        property_id::NAV_CHROME => match code {
            0 => Some("PlatformDefault"),
            1 => Some("Custom"),
            _ => None,
        },
        property_id::TOGGLE_STYLE => match code {
            0 => Some("Switch"),
            1 => Some("Checkbox"),
            2 => Some("Button"),
            _ => None,
        },
        property_id::CONTROL_SIZE => match code {
            0 => Some("Small"),
            1 => Some("Regular"),
            2 => Some("Large"),
            _ => None,
        },
        property_id::DATE_PICKER_MODE => match code {
            0 => Some("Date"),
            1 => Some("Time"),
            2 => Some("DateAndTime"),
            _ => None,
        },
        property_id::PICKER_STYLE => match code {
            0 => Some("Menu"),
            1 => Some("Segmented"),
            2 => Some("Wheel"),
            3 => Some("RadioGroup"),
            _ => None,
        },
        property_id::FONT_STYLE => match code {
            0 => Some("Normal"),
            1 => Some("Italic"),
            _ => None,
        },
        property_id::FONT_DESIGN => match code {
            0 => Some("Default"),
            1 => Some("Serif"),
            2 => Some("Rounded"),
            3 => Some("Monospaced"),
            _ => None,
        },
        property_id::TEXT_CASE => match code {
            0 => Some("None"),
            1 => Some("Uppercase"),
            2 => Some("Lowercase"),
            _ => None,
        },
        _ => None,
    }
}

fn is_bool_prop(prop: u16) -> bool {
    matches!(
        prop,
        property_id::SELECTED
            | property_id::IS_SECURE
            | property_id::IS_INDETERMINATE
            | property_id::VISIBLE
            | property_id::ENABLED
            | property_id::CLIPS_TO_BOUNDS
            | property_id::UNDERLINE
            | property_id::STRIKETHROUGH
            | property_id::COLOR_INVERT
            | property_id::ALLOWS_HIT_TESTING
            | property_id::FIXED_SIZE_HORIZONTAL
            | property_id::FIXED_SIZE_VERTICAL
    )
}

fn is_mask_prop(prop: u16) -> bool {
    prop == property_id::EVENT_LISTENERS || prop == property_id::BORDER_EDGES
}

/// Ensure a comment never contains `--` (which would terminate it) and never
/// ends with `-` (which would make the closing `-->` ambiguous).
fn scrub(s: &str) -> String {
    let mut out = s.replace("--", "- -");
    if out.ends_with('-') {
        out.push(' ');
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn node(component: u16, props: &[(u16, u32)], strings: &[(u16, &str)]) -> Node {
        Node {
            component,
            text: None,
            properties: props.iter().copied().collect(),
            strings: strings
                .iter()
                .map(|(k, v)| (*k, v.to_string()))
                .collect(),
            token_refs: Default::default(),
            date: None,
            children: Vec::new(),
        }
    }

    fn f32_bits(v: f32) -> u32 {
        v.to_bits()
    }

    #[test]
    fn component_names_cover_all_ids() {
        assert_eq!(component_name(component_type::VSTACK), "VStack");
        assert_eq!(component_name(component_type::TEXT), "Text");
        assert_eq!(component_name(component_type::TEXT_FIELD), "TextField");
        assert_eq!(component_name(component_type::BUTTON), "Button");
        assert_eq!(component_name(0x9999), "Unknown");
    }

    #[test]
    fn comment_names_component_and_decodes_modifiers() {
        let n = node(
            component_type::VSTACK,
            &[
                (property_id::SPACING, f32_bits(4.0)),
                (property_id::ALIGNMENT, f32_bits(3.0)),
                (property_id::WIDTH, f32_bits(-1.0)),
                (property_id::HEIGHT, f32_bits(-2.0)),
                (property_id::VISIBLE, 0),
            ],
            &[],
        );
        let comment = node_comment(7, &n);
        assert_eq!(
            comment,
            "<!-- #7 VStack: spacing=4, alignment=Fill, width=FILL, height=HUG, visible=false -->"
        );
    }

    #[test]
    fn comment_includes_text_and_colors_and_strings() {
        let n = node(
            component_type::TEXT,
            &[
                (property_id::COLOR, 0xFF_2563EB),
                (property_id::FONT_SIZE, f32_bits(18.0)),
            ],
            &[(property_id::LABEL, "Email")],
        );
        let mut with_text = n.clone();
        with_text.text = Some("Hello -- world-".to_string());
        let comment = node_comment(2, &with_text);
        assert_eq!(
            comment,
            "<!-- #2 Text \"Hello - - world-\": fontSize=18, color=#FF2563EB -->"
        );
        // The content between the `<!--` / ` -->` delimiters never contains `--`
        // and never ends with `-`, so the comment stays valid HTML.
        let inner = &comment[4..comment.len() - 4];
        assert!(!inner.contains("--"));
        assert!(!inner.ends_with('-'));
    }
}