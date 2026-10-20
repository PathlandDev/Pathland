//! Global design-token theme overrides (`PARAMETER::SET_DESIGN_TOKEN`).
//!
//! A [`Theme`] is a batch of **global, renderer-wide token overrides** for a
//! single scheme — every builder defines **one value** (spec/TOKENS.md). An
//! [`AdaptiveTheme`] composes a light + dark [`Theme`] pair; the dark one is
//! emitted with the `dark.` scheme prefix. Themes are emitted once at mount via
//! [`Engine::apply_theme`](crate::Engine::apply_theme) /
//! [`Engine::apply_adaptive_theme`](crate::Engine::apply_adaptive_theme);
//! overrides are renderer state, so they **never re-emit nodes** and never
//! participate in the diff.

use alloc::string::{String, ToString};
use alloc::vec::Vec;

use pathland_core::tokens::TokenValue;

/// A batch of global design-token overrides for a single scheme (light/base).
#[derive(Debug, Clone, Default)]
pub struct Theme {
    overrides: Vec<(String, TokenValue)>,
}

impl Theme {
    /// An empty theme.
    pub fn new() -> Self {
        Self::default()
    }

    /// Override a token with a packed sRGB `0xAARRGGBB` color.
    pub fn color(mut self, path: &str, argb: u32) -> Self {
        self.push(path, TokenValue::Color(argb));
        self
    }

    /// Override a token with an `F32` value (lengths are in device pixels).
    pub fn f32(mut self, path: &str, value: f32) -> Self {
        self.push(path, TokenValue::F32(value));
        self
    }

    /// Override a token with a `U32` value.
    pub fn u32(mut self, path: &str, value: u32) -> Self {
        self.push(path, TokenValue::U32(value));
        self
    }

    /// Override a token with a `U8` value.
    pub fn u8(mut self, path: &str, value: u8) -> Self {
        self.push(path, TokenValue::U8(value));
        self
    }

    /// Override a token with a `STRING` value (e.g. `font.body.family`). Rides
    /// the arena as a `STRING`-valued override.
    pub fn string(mut self, path: &str, value: &str) -> Self {
        self.push(path, TokenValue::Str(value.to_string()));
        self
    }

    /// Append a raw override with an explicit full path.
    pub fn override_token(mut self, path: &str, value: TokenValue) -> Self {
        self.overrides.push((path.to_string(), value));
        self
    }

    fn push(&mut self, path: &str, value: TokenValue) {
        self.overrides.push((path.to_string(), value));
    }

    /// The accumulated overrides (path, value) in insertion order.
    pub fn iter(&self) -> impl Iterator<Item = (&str, &TokenValue)> {
        self.overrides.iter().map(|(p, v)| (p.as_str(), v))
    }
}

/// An **adaptive theme**: a light [`Theme`] and a dark [`Theme`] pair. When
/// applied via [`Engine::apply_adaptive_theme`](crate::Engine::apply_adaptive_theme),
/// the light theme's overrides are emitted as base tokens and the dark theme's
/// with the `dark.` scheme prefix, so the renderer resolves each against the
/// platform's effective color scheme (spec/TOKENS.md).
#[derive(Debug, Clone, Default)]
pub struct AdaptiveTheme {
    pub light: Theme,
    pub dark: Theme,
}

impl AdaptiveTheme {
    /// Compose a light and a dark theme.
    pub fn new(light: Theme, dark: Theme) -> Self {
        Self { light, dark }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use alloc::vec;
    use crate::Engine;
    use pathland_core::{init_memory, tokens::TokenValue, value_type, Guest, Host, MemoryLayout};

    #[test]
    fn theme_builders_define_single_values() {
        let theme = Theme::new()
            .color("color.primary", 0xFF2563EB)
            .string("font.body.family", "Inter");
        let pairs: Vec<_> = theme.iter().collect();
        assert_eq!(pairs[0], ("color.primary", &TokenValue::Color(0xFF2563EB)));
        assert_eq!(pairs[1], ("font.body.family", &TokenValue::Str("Inter".into())));
    }

    #[test]
    fn apply_adaptive_theme_emits_light_and_dark_prefixed_opcodes() {
        let layout = MemoryLayout::default();
        let mut mem = vec![0u8; layout.total_bytes()];
        init_memory(&mut mem, &layout);

        let mut guest = Guest::new(&mut mem, &layout);
        guest.begin_frame();
        let mut engine = Engine::new();
        let adaptive = AdaptiveTheme::new(
            Theme::new()
                .color("color.primary", 0xFF2563EB)
                .string("font.body.family", "Inter"),
            Theme::new().color("color.primary", 0xFF60A5FA),
        );
        engine.apply_adaptive_theme(&adaptive, &mut guest).unwrap();
        guest.end_frame();

        let mut host = Host::new(&mut mem, &layout);
        let frame = &host.frames()[0];
        let ops: Vec<_> = frame.opcodes().collect();
        assert_eq!(ops.len(), 3, "one SET_DESIGN_TOKEN per override");
        for op in &ops {
            assert_eq!(op.category(), pathland_core::category::PARAMETER);
            assert_eq!(op.command(), pathland_core::parameter::SET_DESIGN_TOKEN);
        }
        // Light color, light string, then the dark.* color.
        assert_eq!(ops[0].b(), u32::from(value_type::COLOR));
        assert_eq!(ops[0].c(), 0xFF2563EB);
        assert_eq!(ops[1].b(), u32::from(value_type::STRING));
        assert_eq!(frame.arena_str(ops[1].c()).unwrap(), "Inter");
        assert_eq!(ops[2].b(), u32::from(value_type::COLOR));
        assert_eq!(ops[2].c(), 0xFF60A5FA);
        // Paths resolve from the arena.
        assert_eq!(frame.arena_str(ops[0].a()).unwrap(), "color.primary");
        assert_eq!(frame.arena_str(ops[1].a()).unwrap(), "font.body.family");
        assert_eq!(frame.arena_str(ops[2].a()).unwrap(), "dark.color.primary");
    }
}