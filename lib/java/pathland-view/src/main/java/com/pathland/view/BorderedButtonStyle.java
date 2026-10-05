package com.pathland.view;

/**
 * A bordered, tinted button style: the label padded onto a colored background with a
 * rounded border. The {@link Button} control wires the action on the native button
 * node, so this style only decorates the content.
 */
public final class BorderedButtonStyle implements ButtonStyle {

    public static final BorderedButtonStyle INSTANCE = new BorderedButtonStyle();

    private final float padding;
    private final Color background;
    private final Color borderColor;
    private final float radius;

    private BorderedButtonStyle() {
        this(10f, Color.rgb(33, 150, 243), Color.rgb(25, 118, 210), 6f);
    }

    private BorderedButtonStyle(float padding, Color background, Color borderColor, float radius) {
        this.padding = padding;
        this.background = background;
        this.borderColor = borderColor;
        this.radius = radius;
    }

    /** The default bordered style. */
    public static BorderedButtonStyle of() {
        return new BorderedButtonStyle();
    }

    /** A bordered style with explicit padding, background, border color, and radius. */
    public static BorderedButtonStyle of(float padding, Color background, Color borderColor, float radius) {
        return new BorderedButtonStyle(padding, background, borderColor, radius);
    }

    @Override
    public View makeBody(Configuration config) {
        return config.label()
                .with(
                        ForegroundStyle.of(Color.WHITE),
                        Padding.of(padding),
                        Background.of(background),
                        Border.of(borderColor, 1f, radius));
    }
}