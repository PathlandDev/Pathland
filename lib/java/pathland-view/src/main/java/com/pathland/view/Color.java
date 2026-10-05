package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/**
 * An sRGB color packed as {@code 0xAARRGGBB} (the protocol's literal color encoding,
 * D65 white point), or a **design-token reference**. Renderers resolve the exact
 * visual appearance.
 *
 * <p>{@code Color} has a dual identity: it is both a <b>View</b> (a
 * layout-greedy solid-color fill — a {@code COLOR} node) and a <b>Data Type</b> passed
 * into style modifiers ({@code foregroundStyle}, {@code background}, {@code border},
 * {@code tint}). It is never a modifier itself.
 *
 * <p>{@link #token(String)} references a design token path (e.g. {@code color.primary},
 * {@code dark.color.primary}); the emitter sends it as the {@code DESIGN_TOKEN} value
 * type and the renderer resolves it against the active scheme (spec/TOKENS.md).
 */
public record Color(int argb, String token) implements View {

    public static final Color CLEAR = new Color(0x00000000, null);
    public static final Color BLACK = new Color(0xFF000000, null);
    public static final Color WHITE = new Color(0xFFFFFFFF, null);
    public static final Color RED = new Color(0xFFFF0000, null);
    public static final Color BLUE = new Color(0xFF2196F3, null);

    /** A fully opaque sRGB color. */
    public static Color rgb(int r, int g, int b) {
        return new Color(0xFF000000 | (r << 16) | (g << 8) | b, null);
    }

    /** A color from its packed {@code 0xAARRGGBB} value. */
    public static Color of(int argb) {
        return new Color(argb, null);
    }

    /** An sRGB color with explicit alpha ({@code 0..255}). */
    public static Color argb(int a, int r, int g, int b) {
        return new Color((a << 24) | (r << 16) | (g << 8) | b, null);
    }

    /** A reference to a design token path (e.g. {@code color.primary}). */
    public static Color token(String path) {
        return new Color(0, path);
    }

    /** Whether this is a design-token reference rather than a literal color. */
    public boolean isToken() {
        return token != null;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.COLOR);
        node.properties.put(Properties.COLOR, this);
        return node;
    }
}