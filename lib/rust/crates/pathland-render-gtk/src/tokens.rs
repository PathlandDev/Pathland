//! GTK-native design-token defaults + color-scheme detection (spec/TOKENS.md).
//!
//! The renderer owns default token values and the effective color scheme.
//! Tier-1 defaults start as **concrete platform-appropriate fallbacks**
//! ([`concrete_default_tables`]); when a real widget exists, the tokens the
//! current GTK theme can resolve are replaced with **native colors** via
//! `gtk::StyleContext::lookup_color` of the theme's named colors (`@theme_*`,
//! `@borders`, `@success_color`, …). Headless / theme-missing → pure concrete
//! fallback, which keeps tests deterministic.

use std::collections::BTreeMap;

use gtk::prelude::*;
use pathland_core::tokens::{Scheme, TokenValue};

use pathland_host::concrete_default_tables;

/// GTK theme named color → canonical Tier-1 token path. A resolver that returns
/// `None` for a name keeps the concrete fallback for that token.
const NAMED_COLORS: &[(&str, &str)] = &[
    ("theme_bg_color", "color.background"),
    ("theme_base_color", "color.surface"),
    ("theme_fg_color", "color.text.primary"),
    ("theme_unfocused_fg_color", "color.text.secondary"),
    ("theme_selected_bg_color", "color.accent"),
    ("theme_selected_bg_color", "color.primary"),
    ("theme_selected_fg_color", "color.text.onPrimary"),
    ("borders", "color.border"),
    ("success_color", "color.success"),
    ("warning_color", "color.warning"),
    ("error_color", "color.danger"),
];

/// Derive the effective scheme from a `GtkSettings` object.
#[allow(deprecated)] // gtk_application_prefer_dark_theme is deprecated; the 4.14 `gtk-color-scheme` isn't in this binding
pub fn scheme_from_settings(settings: &gtk::Settings) -> Scheme {
    let dark = settings.is_gtk_application_prefer_dark_theme()
        || settings
            .gtk_theme_name()
            .as_deref()
            .map_or(false, |name| name.ends_with("-dark"));
    if dark {
        Scheme::Dark
    } else {
        Scheme::Light
    }
}

/// Build the Tier-1 default tables (light + dark), replacing the tokens the
/// `resolve` closure can map (a widget's style-context theme lookup) with
/// native colors **for the given scheme**. GTK exposes one theme at a time, so
/// only the *active* scheme's defaults are native; the other scheme keeps its
/// concrete fallback.
pub fn enrich_defaults<F>(
    resolve: &F,
    scheme: Scheme,
) -> (BTreeMap<String, TokenValue>, BTreeMap<String, TokenValue>)
where
    F: Fn(&str) -> Option<u32>,
{
    let (mut base, mut dark) = concrete_default_tables();
    let target = match scheme {
        Scheme::Dark => &mut dark,
        Scheme::Light => &mut base,
    };
    for (name, path) in NAMED_COLORS {
        if let Some(argb) = resolve(name) {
            target.insert((*path).to_string(), TokenValue::Color(argb));
        }
    }
    (base, dark)
}

/// Resolve a GTK theme named color (`@theme_*`, `@borders`, …) to
/// `0xAARRGGBB` via a style context, or `None` when the theme doesn't define it.
pub fn lookup_color(ctx: &gtk::StyleContext, name: &str) -> Option<u32> {
    ctx.lookup_color(name).map(|rgba| {
        let a = (rgba.alpha() * 255.0).round().clamp(0.0, 255.0) as u32;
        let r = (rgba.red() * 255.0).round() as u32;
        let g = (rgba.green() * 255.0).round() as u32;
        let b = (rgba.blue() * 255.0).round() as u32;
        (a << 24) | (r << 16) | (g << 8) | b
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn named_color_mapping_covers_core_tokens() {
        let paths: Vec<&str> = NAMED_COLORS.iter().map(|(_, p)| *p).collect();
        for core in [
            "color.background",
            "color.surface",
            "color.text.primary",
            "color.accent",
            "color.primary",
            "color.border",
            "color.success",
            "color.warning",
            "color.danger",
        ] {
            assert!(paths.contains(&core), "missing GTK-native mapping for {core}");
        }
    }

    #[test]
    fn enrich_defaults_keeps_fallback_when_theme_resolves_nothing() {
        let (base, dark) = enrich_defaults(&|_| None, Scheme::Light);
        assert_eq!(
            base.get("color.primary"),
            Some(&TokenValue::Color(0xFF_2563EB)),
            "fallback primary kept"
        );
        assert_eq!(
            dark.get("color.primary"),
            Some(&TokenValue::Color(0xFF_60A5FA)),
            "dark fallback primary kept"
        );
        assert!(base.contains_key("space.base"), "space.base fallback present");
    }

    #[test]
    fn enrich_defaults_overwrites_only_the_active_scheme() {
        let resolve = |name: &str| {
            if name == "theme_selected_bg_color" {
                Some(0xFF_112233)
            } else {
                None
            }
        };
        // Dark active: the dark map gets the native accent, the light map keeps fallback.
        let (base, dark) = enrich_defaults(&resolve, Scheme::Dark);
        assert_eq!(dark.get("color.accent"), Some(&TokenValue::Color(0xFF_112233)));
        assert_eq!(base.get("color.accent"), Some(&TokenValue::Color(0xFF_2563EB)));
    }
}