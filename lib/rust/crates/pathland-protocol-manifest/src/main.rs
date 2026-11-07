//! Emits `spec/protocol.tsv` — the target-neutral wire protocol manifest.
//!
//! This binary knows only the canonical Rust constants and a neutral tabular
//! format. It does not contain Java (or any other target-language) source,
//! package names, class structure, or formatting. Language bindings consume the
//! manifest with their own generator/validator.
//!
//! Columns: `kind<TAB>group<TAB>name<TAB>value`.
//!
//! Regenerate from `lib/rust`:
//!
//! ```text
//! cargo run -p pathland-protocol-manifest
//! ```

use pathland_core::{
    category, color_scheme_mode, component_type, environment, event, flag, listener, meta,
    parameter, property_id, size, tree, value_type, value_type_for, window_field,
};
use std::fmt::Write;
use std::io;
use std::path::{Path, PathBuf};

fn manifest_path() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../../../../spec/protocol.tsv")
}

fn hex_u8(v: u8) -> String {
    format!("{v:#04X}")
}

fn hex_u16(v: u16) -> String {
    format!("{v:#06X}")
}

fn hex_u32(v: u32) -> String {
    format!("{v:#010X}")
}

fn row(out: &mut String, kind: &str, group: &str, name: &str, value: &str) {
    writeln!(out, "{kind}\t{group}\t{name}\t{value}").unwrap();
}

/// Value-type names are symbolic in the manifest so each target can map them to
/// its own vocabulary without sharing numeric enum names by accident.
fn value_type_name(vt: u8) -> &'static str {
    match vt {
        value_type::U8 => "U8",
        value_type::U32 => "U32",
        value_type::I32 => "I32",
        value_type::F32 => "F32",
        value_type::STRING => "STRING",
        value_type::ENUM => "ENUM",
        value_type::COLOR => "COLOR",
        value_type::DESIGN_TOKEN => "DESIGN_TOKEN",
        value_type::LIST => "LIST",
        _ => "F32",
    }
}

/// All property ids, in protocol allocation order.
fn properties() -> Vec<(&'static str, u16)> {
    vec![
        ("SPACING", property_id::SPACING),
        ("ALIGNMENT", property_id::ALIGNMENT),
        ("CONTENT_MARGINS", property_id::CONTENT_MARGINS),
        ("SHAPE_KIND", property_id::SHAPE_KIND),
        ("TEXT", property_id::TEXT),
        ("LINE_LIMIT", property_id::LINE_LIMIT),
        ("TEXT_ALIGNMENT", property_id::TEXT_ALIGNMENT),
        ("TRUNCATION_MODE", property_id::TRUNCATION_MODE),
        ("OFFSET_X", property_id::OFFSET_X),
        ("OFFSET_Y", property_id::OFFSET_Y),
        ("POSITION_X", property_id::POSITION_X),
        ("POSITION_Y", property_id::POSITION_Y),
        ("MIN_WIDTH", property_id::MIN_WIDTH),
        ("MAX_WIDTH", property_id::MAX_WIDTH),
        ("MIN_HEIGHT", property_id::MIN_HEIGHT),
        ("MAX_HEIGHT", property_id::MAX_HEIGHT),
        ("FIXED_SIZE_HORIZONTAL", property_id::FIXED_SIZE_HORIZONTAL),
        ("FIXED_SIZE_VERTICAL", property_id::FIXED_SIZE_VERTICAL),
        ("LAYOUT_PRIORITY", property_id::LAYOUT_PRIORITY),
        ("ASPECT_RATIO", property_id::ASPECT_RATIO),
        ("CONTENT_MODE", property_id::CONTENT_MODE),
        ("MINIMUM_SCALE_FACTOR", property_id::MINIMUM_SCALE_FACTOR),
        ("GRID_COLUMNS", property_id::GRID_COLUMNS),
        ("GRID_ROWS", property_id::GRID_ROWS),
        ("GRID_TRACKS", property_id::GRID_TRACKS),
        ("SCROLL_AXIS", property_id::SCROLL_AXIS),
        ("BACKGROUND_COLOR", property_id::BACKGROUND_COLOR),
        ("IMAGE_SOURCE", property_id::IMAGE_SOURCE),
        ("PLAYBACK_STATE", property_id::PLAYBACK_STATE),
        ("MEDIA_POSITION", property_id::MEDIA_POSITION),
        ("MEDIA_VOLUME", property_id::MEDIA_VOLUME),
        ("MEDIA_CONTROLLER_ID", property_id::MEDIA_CONTROLLER_ID),
        ("MEDIA_SOURCE", property_id::MEDIA_SOURCE),
        ("MEDIA_MUTED", property_id::MEDIA_MUTED),
        ("MEDIA_LOOP", property_id::MEDIA_LOOP),
        ("MEDIA_RATE", property_id::MEDIA_RATE),
        ("ICON_NAME", property_id::ICON_NAME),
        ("BORDER_WIDTH", property_id::BORDER_WIDTH),
        ("BORDER_COLOR", property_id::BORDER_COLOR),
        ("BORDER_RADIUS", property_id::BORDER_RADIUS),
        ("PADDING_STYLE", property_id::PADDING_STYLE),
        ("FONT_SIZE", property_id::FONT_SIZE),
        ("FONT_WEIGHT", property_id::FONT_WEIGHT),
        ("FONT_FAMILY", property_id::FONT_FAMILY),
        ("COLOR", property_id::COLOR),
        ("WIDTH", property_id::WIDTH),
        ("HEIGHT", property_id::HEIGHT),
        ("OPACITY", property_id::OPACITY),
        ("VISIBLE", property_id::VISIBLE),
        ("Z_INDEX", property_id::Z_INDEX),
        ("CLIPS_TO_BOUNDS", property_id::CLIPS_TO_BOUNDS),
        ("PADDING", property_id::PADDING),
        ("PADDING_TOP", property_id::PADDING_TOP),
        ("PADDING_RIGHT", property_id::PADDING_RIGHT),
        ("PADDING_BOTTOM", property_id::PADDING_BOTTOM),
        ("PADDING_LEFT", property_id::PADDING_LEFT),
        ("BORDER_EDGES", property_id::BORDER_EDGES),
        ("FONT_STYLE", property_id::FONT_STYLE),
        ("FONT_DESIGN", property_id::FONT_DESIGN),
        ("FONT_WIDTH", property_id::FONT_WIDTH),
        ("KERNING", property_id::KERNING),
        ("TRACKING", property_id::TRACKING),
        ("BASELINE_OFFSET", property_id::BASELINE_OFFSET),
        ("LINE_SPACING", property_id::LINE_SPACING),
        ("TEXT_CASE", property_id::TEXT_CASE),
        ("UNDERLINE", property_id::UNDERLINE),
        ("STRIKETHROUGH", property_id::STRIKETHROUGH),
        ("SHADOW_COLOR", property_id::SHADOW_COLOR),
        ("SHADOW_RADIUS", property_id::SHADOW_RADIUS),
        ("SHADOW_X", property_id::SHADOW_X),
        ("SHADOW_Y", property_id::SHADOW_Y),
        ("BLUR_RADIUS", property_id::BLUR_RADIUS),
        ("SATURATION", property_id::SATURATION),
        ("CONTRAST", property_id::CONTRAST),
        ("BRIGHTNESS", property_id::BRIGHTNESS),
        ("GRAYSCALE", property_id::GRAYSCALE),
        ("HUE_ROTATION", property_id::HUE_ROTATION),
        ("COLOR_MULTIPLY", property_id::COLOR_MULTIPLY),
        ("COLOR_INVERT", property_id::COLOR_INVERT),
        ("ROTATION_DEGREES", property_id::ROTATION_DEGREES),
        ("SCALE", property_id::SCALE),
        ("ALLOWS_HIT_TESTING", property_id::ALLOWS_HIT_TESTING),
        ("TINT", property_id::TINT),
        ("TRANSITION", property_id::TRANSITION),
        ("FIT_QUERY", property_id::FIT_QUERY),
        ("TEXT_STYLE", property_id::TEXT_STYLE),
        ("ROLE", property_id::ROLE),
        ("STATE", property_id::STATE),
        ("ENABLED", property_id::ENABLED),
        ("SELECTED", property_id::SELECTED),
        ("EVENT_LISTENERS", property_id::EVENT_LISTENERS),
        ("VALUE", property_id::VALUE),
        ("MIN_VALUE", property_id::MIN_VALUE),
        ("MAX_VALUE", property_id::MAX_VALUE),
        ("LABEL", property_id::LABEL),
        ("PROMPT", property_id::PROMPT),
        ("STEP_VALUE", property_id::STEP_VALUE),
        ("CONTROL_SIZE", property_id::CONTROL_SIZE),
        ("IS_SECURE", property_id::IS_SECURE),
        ("PROGRESS", property_id::PROGRESS),
        ("IS_INDETERMINATE", property_id::IS_INDETERMINATE),
        ("SELECTION", property_id::SELECTION),
        ("COLOR_VALUE", property_id::COLOR_VALUE),
        ("DATE_PICKER_MODE", property_id::DATE_PICKER_MODE),
        ("PICKER_STYLE", property_id::PICKER_STYLE),
        ("ACTION_ID", property_id::ACTION_ID),
        ("BINDING_ID", property_id::BINDING_ID),
        ("TOGGLE_STYLE", property_id::TOGGLE_STYLE),
        ("ROUTE", property_id::ROUTE),
        ("NAV_DEPTH", property_id::NAV_DEPTH),
        ("NAV_CHROME", property_id::NAV_CHROME),
    ]
}

fn emit_manifest() -> String {
    let mut out = String::new();
    out.push_str(
        "# Generated by `cargo run -p pathland-protocol-manifest` (lib/rust). Do not edit.\n\
         # Target-neutral Pathland wire protocol. Columns: kind, group, name, value.\n\
         # Values are hex integers, decimal floats, or symbolic value-type names.\n\
         #\n\
         # Consumers own their target-language representation and formatting.\n\n",
    );

    for (name, value) in [
        ("TREE", category::TREE),
        ("PARAMETER", category::PARAMETER),
        ("EVENT", category::EVENT),
        ("META", category::META),
    ] {
        row(&mut out, "category", "", name, &hex_u8(value));
    }
    out.push('\n');

    for (name, value) in [
        ("TEXT", component_type::TEXT),
        ("IMAGE", component_type::IMAGE),
        ("COLOR", component_type::COLOR),
        ("SHAPE", component_type::SHAPE),
        ("DIVIDER", component_type::DIVIDER),
        ("SPACER", component_type::SPACER),
        ("PROGRESS_VIEW", component_type::PROGRESS_VIEW),
        ("GAUGE", component_type::GAUGE),
        ("VIDEO", component_type::VIDEO),
        ("ICON", component_type::ICON),
        ("VSTACK", component_type::VSTACK),
        ("HSTACK", component_type::HSTACK),
        ("ZSTACK", component_type::ZSTACK),
        ("GRID", component_type::GRID),
        ("SCROLLVIEW", component_type::SCROLLVIEW),
        ("LAZY_VGRID", component_type::LAZY_VGRID),
        ("LAZY_HGRID", component_type::LAZY_HGRID),
        ("SIZE_THAT_FITS", component_type::SIZE_THAT_FITS),
        ("LAZY_VSTACK", component_type::LAZY_VSTACK),
        ("LAZY_HSTACK", component_type::LAZY_HSTACK),
        ("GRID_ROW", component_type::GRID_ROW),
        ("PROXY_VIEW", component_type::PROXY_VIEW),
        ("BUTTON", component_type::BUTTON),
        ("TEXT_FIELD", component_type::TEXT_FIELD),
        ("TEXT_EDITOR", component_type::TEXT_EDITOR),
        ("TOGGLE", component_type::TOGGLE),
        ("SLIDER", component_type::SLIDER),
        ("STEPPER", component_type::STEPPER),
        ("DATE_PICKER", component_type::DATE_PICKER),
        ("PICKER", component_type::PICKER),
        ("MENU", component_type::MENU),
        ("COLOR_PICKER", component_type::COLOR_PICKER),
        ("MEDIA_CONTROLLER", component_type::MEDIA_CONTROLLER),
        ("COMMENT", component_type::COMMENT),
    ] {
        row(&mut out, "component", "", name, &hex_u16(value));
    }
    out.push('\n');

    for (name, value) in [
        ("CREATE_NODE", tree::CREATE_NODE),
        ("DELETE_NODE", tree::DELETE_NODE),
        ("INSERT_CHILD", tree::INSERT_CHILD),
        ("REMOVE_CHILD", tree::REMOVE_CHILD),
        ("MOVE_CHILD", tree::MOVE_CHILD),
    ] {
        row(&mut out, "command", "Tree", name, &hex_u8(value));
    }
    for (name, value) in [
        ("SET_PROPERTY", parameter::SET_PROPERTY),
        ("SET_DESIGN_TOKEN", parameter::SET_DESIGN_TOKEN),
        ("SET_TEXT", parameter::SET_TEXT),
        ("SET_DATE", parameter::SET_DATE),
        ("SET_ATTRIBUTED_TEXT", parameter::SET_ATTRIBUTED_TEXT),
    ] {
        row(&mut out, "command", "Parameter", name, &hex_u8(value));
    }
    for (name, value) in [
        ("POINTER_DOWN", event::POINTER_DOWN),
        ("POINTER_MOVE", event::POINTER_MOVE),
        ("POINTER_UP", event::POINTER_UP),
        ("KEY_DOWN", event::KEY_DOWN),
        ("KEY_UP", event::KEY_UP),
        ("VALUE_CHANGED", event::VALUE_CHANGED),
        ("TEXT_CHANGED", event::TEXT_CHANGED),
        ("FOCUS_CHANGED", event::FOCUS_CHANGED),
        ("EDITING_CHANGED", event::EDITING_CHANGED),
        ("SUBMIT", event::SUBMIT),
        ("SCROLL", event::SCROLL),
        ("WHEEL", event::WHEEL),
        ("DATE_CHANGED", event::DATE_CHANGED),
        ("NAVIGATE", event::NAVIGATE),
        ("MEDIA_PLAY_STATE_CHANGED", event::MEDIA_PLAY_STATE_CHANGED),
        ("MEDIA_TIME_UPDATED", event::MEDIA_TIME_UPDATED),
        ("MEDIA_ENDED", event::MEDIA_ENDED),
        ("MEDIA_VOLUME_CHANGED", event::MEDIA_VOLUME_CHANGED),
        ("FIT_CHANGED", event::FIT_CHANGED),
        ("MEDIA_DURATION_CHANGED", event::MEDIA_DURATION_CHANGED),
    ] {
        row(&mut out, "command", "Event", name, &hex_u8(value));
    }
    for (name, value) in [
        ("RESET", meta::RESET),
        ("ENVIRONMENT", meta::ENVIRONMENT),
        ("RESYNC", meta::RESYNC),
        ("PING", meta::PING),
        ("PONG", meta::PONG),
        ("SET_COLOR_SCHEME", meta::SET_COLOR_SCHEME),
        ("RESET_THEME", meta::RESET_THEME),
        ("SET_WINDOW_METADATA", meta::SET_WINDOW_METADATA),
    ] {
        row(&mut out, "command", "Meta", name, &hex_u8(value));
    }
    out.push('\n');

    for (name, value) in [
        ("TITLE", window_field::TITLE),
        ("DESCRIPTION", window_field::DESCRIPTION),
        ("LOCALE", window_field::LOCALE),
    ] {
        row(&mut out, "field", "WindowFields", name, &hex_u16(value));
    }
    for (name, value) in [
        ("PLATFORM", color_scheme_mode::PLATFORM),
        ("LIGHT", color_scheme_mode::LIGHT),
        ("DARK", color_scheme_mode::DARK),
    ] {
        row(&mut out, "field", "ColorSchemeMode", name, &hex_u32(value));
    }
    for (name, value) in [
        ("VIEWPORT_WIDTH", environment::VIEWPORT_WIDTH),
        ("VIEWPORT_HEIGHT", environment::VIEWPORT_HEIGHT),
        ("ROUTE", environment::ROUTE),
    ] {
        row(&mut out, "field", "Environment", name, &hex_u16(value));
    }
    out.push('\n');

    for (name, value) in [
        ("POINTER_SECONDARY", flag::POINTER_SECONDARY),
        ("HOVER_ENTER", flag::HOVER_ENTER),
        ("HOVER_LEAVE", flag::HOVER_LEAVE),
        ("KEY_REPEAT", flag::KEY_REPEAT),
        ("NAVIGATE_URL", flag::NAVIGATE_URL),
    ] {
        row(&mut out, "flag", "Flags", name, &hex_u16(value));
    }
    out.push('\n');

    for (name, value) in [
        ("POINTER_DOWN", listener::POINTER_DOWN),
        ("POINTER_MOVE", listener::POINTER_MOVE),
        ("POINTER_UP", listener::POINTER_UP),
        ("KEY_DOWN", listener::KEY_DOWN),
        ("KEY_UP", listener::KEY_UP),
        ("FOCUS", listener::FOCUS),
        ("EDITING", listener::EDITING),
        ("SUBMIT", listener::SUBMIT),
        ("SCROLL", listener::SCROLL),
        ("WHEEL", listener::WHEEL),
        ("POINTER", listener::POINTER),
    ] {
        row(&mut out, "listener", "Listeners", name, &hex_u32(value));
    }
    out.push('\n');

    for (name, value) in [("FILL", size::FILL), ("HUG_CONTENT", size::HUG_CONTENT)] {
        row(&mut out, "size", "Size", name, &value.to_string());
    }
    out.push('\n');

    for (name, value) in [
        ("U8", value_type::U8),
        ("U32", value_type::U32),
        ("I32", value_type::I32),
        ("F32", value_type::F32),
        ("STRING", value_type::STRING),
        ("ENUM", value_type::ENUM),
        ("COLOR", value_type::COLOR),
        ("DESIGN_TOKEN", value_type::DESIGN_TOKEN),
        ("LIST", value_type::LIST),
    ] {
        row(&mut out, "value_type", "", name, &hex_u8(value));
    }
    out.push('\n');

    for (name, value) in properties() {
        row(&mut out, "property", "", name, &hex_u16(value));
    }
    out.push('\n');

    for (name, value) in properties() {
        row(
            &mut out,
            "property_value_type",
            "",
            name,
            value_type_name(value_type_for(value)),
        );
    }

    out
}

fn main() -> io::Result<()> {
    let path = manifest_path();
    std::fs::write(&path, emit_manifest())?;
    println!("wrote {}", path.display());
    Ok(())
}
