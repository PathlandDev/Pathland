package com.pathland.demo;

import com.pathland.view.AdaptiveTheme;
import com.pathland.view.Theme;

/**
 * The demo's theme: a **light** {@link Theme} and a **dark** {@link Theme},
 * composed via {@link AdaptiveTheme} (spec/TOKENS.md). The renderer resolves
 * each token against the platform's color scheme, so the HTML/JS DOM demo
 * re-themes under {@code prefers-color-scheme: dark} and a native GTK renderer
 * under its dark variant — with no re-emission.
 */
public final class DemoTheme {

    /** Light (base) values. */
    private static final Theme LIGHT = new Theme()
            .color("color.primary", 0xFF2563EB)
            .color("color.surface", 0xFFFFFFFF)
            .color("color.accent", 0xFF2563EB)
            .f32("space.base", 4.0f)
            .color("control.background", 0xFFF9FAFB)
            .color("control.foreground", 0xFF111827)
            .color("control.accent", 0xFF2563EB);

    /** Dark values (emitted with the {@code dark.} scheme prefix). */
    private static final Theme DARK = new Theme()
            .color("color.primary", 0xFF60A5FA)
            .color("color.surface", 0xFF111827)
            .color("color.accent", 0xFF60A5FA)
            .color("control.background", 0xFF1F2937)
            .color("control.foreground", 0xFFF9FAFB)
            .color("control.accent", 0xFF60A5FA);

    private DemoTheme() {}

    /** The light (base) theme. */
    public static Theme light() {
        return LIGHT;
    }

    /** The dark theme. */
    public static Theme dark() {
        return DARK;
    }

    /** The demo's adaptive theme (per-app, global overrides; safe to share across sessions). */
    public static AdaptiveTheme adaptive() {
        return new AdaptiveTheme(LIGHT, DARK);
    }
}