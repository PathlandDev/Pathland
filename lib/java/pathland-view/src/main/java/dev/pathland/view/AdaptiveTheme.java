package dev.pathland.view;

import dev.pathland.view.emit.OpcodeSink;

/**
 * An **adaptive theme**: a light ({@code Theme}) and a dark ({@code Theme})
 * pair. Emitting it writes the light theme's overrides as base tokens and the
 * dark theme's overrides with the {@code dark.} scheme prefix, so the renderer
 * resolves each against the platform's effective color scheme (spec/TOKENS.md).
 *
 * <pre>{@code
 * Theme light = new Theme().color("color.primary", 0xFF2563EB);
 * Theme dark  = new Theme().color("color.primary", 0xFF60A5FA);
 * new AdaptiveTheme(light, dark).emit(sink);
 * }</pre>
 */
public final class AdaptiveTheme implements ThemeData {

    private final Theme light;
    private final Theme dark;

    /** Compose a light and a dark theme. */
    public AdaptiveTheme(Theme light, Theme dark) {
        this.light = light;
        this.dark = dark;
    }

    /** The light (base) theme. */
    public Theme light() {
        return light;
    }

    /** The dark theme (emitted with the {@code dark.} scheme prefix). */
    public Theme dark() {
        return dark;
    }

    @Override
    public void emitInto(OpcodeSink sink) {
        light.emitInto(sink);
        for (Theme.TokenOverride o : dark.overrides()) {
            sink.setDesignToken(dark(o.path()), o.valueType(), o.value());
        }
    }

    private static String dark(String path) {
        return path.startsWith("dark.") ? path : "dark." + path;
    }
}