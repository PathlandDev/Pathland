//! Design-token resolution (spec/TOKENS.md) — the reference implementation.
//!
//! Renderers own **scheme detection** and **default values**; the resolution
//! algorithm itself — `dark.*` layer → override → default → parent-fallback →
//! fallback, plus the generative `space.<N>` family — is protocol-level and is
//! conformance-tested here (see the "Design-Token Resolution Conformance" table
//! in `spec/CONFORMANCE.md`).
//!
//! This module is pure (`no_std` + `alloc`): it stores no state. A renderer
//! keeps its own override/default tables and calls [`resolve`] at apply time.

use alloc::collections::BTreeMap;
use alloc::string::String;
use alloc::vec::Vec;

/// The effective color scheme a renderer derives from the platform. Never
/// carried by the protocol.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Scheme {
    Light,
    Dark,
}

impl Default for Scheme {
    fn default() -> Self {
        Scheme::Light
    }
}

/// A resolved token value. Mirrors the wire value types; `Str` is the resolved
/// form of a `STRING`-valued token (the wire carries an arena ref instead).
#[derive(Debug, Clone, PartialEq)]
pub enum TokenValue {
    Color(u32),
    F32(f32),
    U32(u32),
    U8(u8),
    I32(i32),
    Str(String),
}

impl TokenValue {
    /// The wire `value_type` for this value.
    pub fn value_type(&self) -> u8 {
        match self {
            TokenValue::Color(_) => crate::value_type::COLOR,
            TokenValue::F32(_) => crate::value_type::F32,
            TokenValue::U32(_) => crate::value_type::U32,
            TokenValue::U8(_) => crate::value_type::U8,
            TokenValue::I32(_) => crate::value_type::I32,
            TokenValue::Str(_) => crate::value_type::STRING,
        }
    }

    /// Decode a wire `(value_type, value)` into a token value. `STRING` returns
    /// `None` — the wire carries an arena ref there; resolve it and build
    /// [`TokenValue::Str`] directly.
    pub fn from_wire(value_type: u8, value: u32) -> Option<Self> {
        match value_type {
            crate::value_type::COLOR => Some(TokenValue::Color(value)),
            crate::value_type::F32 => Some(TokenValue::F32(f32::from_bits(value))),
            crate::value_type::U32 => Some(TokenValue::U32(value)),
            crate::value_type::U8 => Some(TokenValue::U8((value & 0xFF) as u8)),
            crate::value_type::I32 => Some(TokenValue::I32(value as i32)),
            _ => None,
        }
    }

    /// The value as an `F32`, if this token is numeric.
    pub fn as_f32(&self) -> Option<f32> {
        match self {
            TokenValue::F32(v) => Some(*v),
            TokenValue::U32(v) => Some(*v as f32),
            TokenValue::U8(v) => Some(f32::from(*v)),
            TokenValue::I32(v) => Some(*v as f32),
            _ => None,
        }
    }

    /// The packed wire value (`C` field) for non-string values. `Str` returns
    /// `None` — the wire carries an arena ref there.
    pub fn to_bits(&self) -> Option<u32> {
        match self {
            TokenValue::Color(v) => Some(*v),
            TokenValue::F32(v) => Some(v.to_bits()),
            TokenValue::U32(v) => Some(*v),
            TokenValue::U8(v) => Some(u32::from(*v)),
            TokenValue::I32(v) => Some(*v as u32),
            TokenValue::Str(_) => None,
        }
    }
}

/// The **Tier-1** standard token catalog (spec/TOKENS.md) — the paths every
/// renderer MUST provide light + dark defaults for. Composite tokens whose
/// value would be a multi-part CSS shorthand (`button.padding`) are not scalar
/// token values and are excluded; renderers own such component chrome.
pub const TIER1_TOKENS: &[&str] = &[
    // Colors.
    "color.primary",
    "color.secondary",
    "color.background",
    "color.surface",
    "color.text.primary",
    "color.text.secondary",
    "color.text.onPrimary",
    "color.border",
    "color.accent",
    // Typography.
    "font.body.size",
    "font.body.weight",
    "font.body.family",
    // Spacing + radii + borders.
    "space.base",
    "radius.xs",
    "radius.sm",
    "radius.md",
    "radius.lg",
    "radius.xl",
    "radius.full",
    "border.width.thin",
    // Shared control core.
    "control.background",
    "control.foreground",
    "control.border",
    "control.border.width",
    "control.radius",
    "control.padding.horizontal",
    "control.padding.vertical",
    "control.height",
    "control.font.size",
    "control.font.weight",
    "control.accent",
    // Buttons.
    "button.background",
    "button.foreground",
    "button.border",
    "button.radius",
    "button.height",
    "button.font.size",
    "button.font.weight",
    "button.accent",
    // Inputs.
    "input.background",
    "input.foreground",
    "input.placeholder",
    "input.border",
    "input.radius",
    "input.font.size",
    // Toggle / slider / progress / gauge.
    "toggle.accent",
    "toggle.track.off",
    "toggle.track.on",
    "toggle.thumb",
    "slider.accent",
    "slider.track",
    "slider.thumb",
    "progress.track",
    "progress.fill",
    "gauge.track",
    "gauge.fill",
];

/// The four lookup tables a renderer consults, in resolution order:
/// app overrides (base + `dark.*`) and renderer defaults (base + `dark.*`).
///
/// Overrides and defaults are keyed by **full path** in the base maps and by
/// **bare path** (the `dark.` prefix stripped) in the `dark` maps.
#[derive(Debug, Clone, Copy)]
pub struct TokenTables<'a> {
    pub overrides: &'a BTreeMap<String, TokenValue>,
    pub dark_overrides: &'a BTreeMap<String, TokenValue>,
    pub defaults: &'a BTreeMap<String, TokenValue>,
    pub dark_defaults: &'a BTreeMap<String, TokenValue>,
}

/// Resolve a token path against the given tables and scheme.
///
/// Resolution (spec/TOKENS.md):
/// 1. The **generative `space.<N>` family** resolves `space.base` (scheme-aware,
///    with the same fallback chain) and multiplies by the numeric leaf.
/// 2. Walk the candidate chain — for the dark scheme, `dark.<path>` ancestors
///    first, then the base ancestors; for light, base ancestors only.
/// 3. The first **app override** in the chain wins; otherwise the first
///    **renderer default**; otherwise `None` (the renderer falls back).
pub fn resolve(
    path: &str,
    scheme: Scheme,
    tables: &TokenTables<'_>,
) -> Option<TokenValue> {
    // Generative spacing family: `space.<N>` = `space.base` × N.
    if let Some(rest) = path.strip_prefix("space.") {
        if let Ok(n) = rest.parse::<f32>() {
            if let Some(base) = resolve("space.base", scheme, tables) {
                if let Some(base) = base.as_f32() {
                    return Some(TokenValue::F32(base * n));
                }
            }
        }
    }

    let chain = parent_chain(path);

    // Override pass: the `dark.*` layer first (when the scheme is dark), then
    // the base layer — each consulted against its own override table.
    if scheme == Scheme::Dark {
        for &cand in &chain {
            if let Some(v) = tables.dark_overrides.get(cand) {
                return Some(v.clone());
            }
        }
    }
    for &cand in &chain {
        if let Some(v) = tables.overrides.get(cand) {
            return Some(v.clone());
        }
    }

    // Default pass: same layer order against the renderer defaults.
    if scheme == Scheme::Dark {
        for &cand in &chain {
            if let Some(v) = tables.dark_defaults.get(cand) {
                return Some(v.clone());
            }
        }
    }
    for &cand in &chain {
        if let Some(v) = tables.defaults.get(cand) {
            return Some(v.clone());
        }
    }
    None
}

/// Resolve a `DESIGN_TOKEN` property **reference** (spec/TOKENS.md).
///
/// A reference whose path starts with `dark.` is an **explicit dark-variant
/// reference**: the prefix is stripped and the bare path resolves against the
/// dark layer regardless of the active scheme. Every other reference resolves
/// normally under the effective scheme.
pub fn resolve_ref(
    path: &str,
    scheme: Scheme,
    tables: &TokenTables<'_>,
) -> Option<TokenValue> {
    match path.strip_prefix("dark.") {
        Some(bare) => resolve(bare, Scheme::Dark, tables),
        None => resolve(path, scheme, tables),
    }
}

/// The parent-fallback chain for a path: `font.body.size` →
/// `["font.body.size", "font.body", "font", ""]`.
fn parent_chain(path: &str) -> Vec<&str> {
    let mut chain = Vec::new();
    chain.push(path);
    let mut cur = path;
    while let Some(idx) = cur.rfind('.') {
        cur = &cur[..idx];
        chain.push(cur);
    }
    chain.push("");
    chain
}

#[cfg(test)]
mod tests {
    use super::*;
    use alloc::string::ToString;

    fn map(pairs: &[(&str, TokenValue)]) -> BTreeMap<String, TokenValue> {
        pairs
            .iter()
            .map(|(k, v)| ((*k).to_string(), v.clone()))
            .collect()
    }

    fn tables(
        overrides: &[(&str, TokenValue)],
        dark_overrides: &[(&str, TokenValue)],
        defaults: &[(&str, TokenValue)],
        dark_defaults: &[(&str, TokenValue)],
    ) -> (BTreeMap<String, TokenValue>, BTreeMap<String, TokenValue>, BTreeMap<String, TokenValue>, BTreeMap<String, TokenValue>) {
        (map(overrides), map(dark_overrides), map(defaults), map(dark_defaults))
    }

    fn resolve_with(
        overrides: &[(&str, TokenValue)],
        dark_overrides: &[(&str, TokenValue)],
        defaults: &[(&str, TokenValue)],
        path: &str,
        scheme: Scheme,
    ) -> Option<TokenValue> {
        let (o, d, de, dde) = tables(overrides, dark_overrides, defaults, &[]);
        let t = TokenTables {
            overrides: &o,
            dark_overrides: &d,
            defaults: &de,
            dark_defaults: &dde,
        };
        resolve(path, scheme, &t)
    }

    /// The full "Design-Token Resolution Conformance" table (spec/CONFORMANCE.md).
    #[test]
    fn conformance_resolution_table() {
        let (o, d, de, dde) = tables(
            &[
                ("color.primary", TokenValue::Color(0xFF2563EB)),
                ("space.base", TokenValue::F32(4.0)),
            ],
            &[("color.primary", TokenValue::Color(0xFF60A5FA))],
            &[("font.body.size", TokenValue::F32(16.0)), ("space.base", TokenValue::F32(4.0))],
            &[],
        );
        let t = TokenTables {
            overrides: &o,
            dark_overrides: &d,
            defaults: &de,
            dark_defaults: &dde,
        };

        assert_eq!(
            resolve("color.primary", Scheme::Dark, &t),
            Some(TokenValue::Color(0xFF60A5FA)),
            "dark.color.primary override wins (dark layer)"
        );
        assert_eq!(
            resolve("color.primary", Scheme::Light, &t),
            Some(TokenValue::Color(0xFF2563EB)),
            "dark layer skipped; base override wins"
        );
        assert_eq!(
            resolve("color.primary", Scheme::Dark, &TokenTables {
                overrides: &o,
                dark_overrides: &BTreeMap::new(),
                defaults: &de,
                dark_defaults: &dde,
            }),
            Some(TokenValue::Color(0xFF2563EB)),
            "base override falls through when the dark layer is empty"
        );
        assert_eq!(
            resolve("font.body.size", Scheme::Light, &t),
            Some(TokenValue::F32(16.0)),
            "no override; defaults are renderer-owned"
        );
        assert_eq!(
            resolve_with(&[("font.body", TokenValue::F32(18.0))], &[], &[], "font.body.size", Scheme::Light),
            Some(TokenValue::F32(18.0)),
            "parent-path fallback"
        );
        assert_eq!(
            resolve("space.2", Scheme::Light, &t),
            Some(TokenValue::F32(8.0)),
            "generative family: space.base (4.0) × 2"
        );
        assert_eq!(
            resolve("space.0.5", Scheme::Light, &t),
            Some(TokenValue::F32(2.0)),
            "generative family: fractional leaf"
        );
        assert_eq!(
            resolve_with(&[], &[], &[("space.base", TokenValue::F32(4.0))], "space.2", Scheme::Light),
            Some(TokenValue::F32(8.0)),
            "generative base falls back to renderer defaults"
        );
        assert_eq!(
            resolve("missing.token", Scheme::Light, &t),
            None,
            "no override, no default, no fallback -> None"
        );
    }

    #[test]
    fn parent_fallback_walks_ancestors_and_root() {
        assert_eq!(parent_chain("color.primary"), ["color.primary", "color", ""]);
        assert_eq!(
            parent_chain("font.body.size"),
            ["font.body.size", "font.body", "font", ""]
        );
    }

    #[test]
    fn explicit_dark_reference_forces_the_dark_layer() {
        // App overrides: light blue, dark amber. A `dark.` reference must use
        // the dark value in BOTH schemes; a regular reference follows the scheme.
        let (o, d, de, dde) = tables(
            &[("color.primary", TokenValue::Color(0xFF_2563EB))],
            &[("color.primary", TokenValue::Color(0xFF_F59E0B))],
            &[],
            &[],
        );
        let t = TokenTables {
            overrides: &o,
            dark_overrides: &d,
            defaults: &de,
            dark_defaults: &dde,
        };
        assert_eq!(
            resolve_ref("dark.color.primary", Scheme::Light, &t),
            Some(TokenValue::Color(0xFF_F59E0B)),
            "explicit dark reference ignores the light scheme"
        );
        assert_eq!(
            resolve_ref("dark.color.primary", Scheme::Dark, &t),
            Some(TokenValue::Color(0xFF_F59E0B))
        );
        assert_eq!(
            resolve_ref("color.primary", Scheme::Light, &t),
            Some(TokenValue::Color(0xFF_2563EB))
        );
        assert_eq!(
            resolve_ref("color.primary", Scheme::Dark, &t),
            Some(TokenValue::Color(0xFF_F59E0B))
        );
    }

    #[test]
    fn explicit_dark_reference_falls_back_to_dark_defaults() {
        let (o, d, de, dde) = tables(
            &[],
            &[],
            &[("color.primary", TokenValue::Color(0xFF_2563EB))],
            &[("color.primary", TokenValue::Color(0xFF_111827))],
        );
        let t = TokenTables {
            overrides: &o,
            dark_overrides: &d,
            defaults: &de,
            dark_defaults: &dde,
        };
        assert_eq!(
            resolve_ref("dark.color.primary", Scheme::Light, &t),
            Some(TokenValue::Color(0xFF_111827)),
            "dark defaults, not light defaults"
        );
    }

    #[test]
    fn dark_layer_takes_precedence_over_base() {
        // Same path overridden in both layers: dark wins only under the dark scheme.
        let (o, d, de, dde) = tables(
            &[("color.accent", TokenValue::Color(0xFF0000FF))],
            &[("color.accent", TokenValue::Color(0xFFFF0000))],
            &[],
            &[],
        );
        let t = TokenTables {
            overrides: &o,
            dark_overrides: &d,
            defaults: &de,
            dark_defaults: &dde,
        };
        assert_eq!(resolve("color.accent", Scheme::Dark, &t), Some(TokenValue::Color(0xFFFF0000)));
        assert_eq!(resolve("color.accent", Scheme::Light, &t), Some(TokenValue::Color(0xFF0000FF)));
    }
}