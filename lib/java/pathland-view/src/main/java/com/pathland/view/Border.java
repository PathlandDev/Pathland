package com.pathland.view;

/**
 * a border ({@code BORDER_COLOR} + {@code BORDER_WIDTH} + optional {@code BORDER_RADIUS}).
 */
public final class Border implements ViewModifier {

    private final Color color;
    private final float width;
    private final Float radius;

    private Border(Color color, float width, Float radius) {
        this.color = color;
        this.width = width;
        this.radius = radius;
    }

    /** A border of {@code color} and {@code width} ({@code .border(_:width:)}). */
    public static Border of(Color color, float width) {
        return new Border(color, width, null);
    }

    /** A border of {@code color}, {@code width}, and {@code radius}. */
    public static Border of(Color color, float width, float radius) {
        return new Border(color, width, radius);
    }

    @Override
    public View body(View content) {
        if (radius == null) {
            return Modified.props(content,
                    Modified.prop(Properties.BORDER_WIDTH, width),
                    Modified.prop(Properties.BORDER_COLOR, color));
        }
        return Modified.props(content,
                Modified.prop(Properties.BORDER_WIDTH, width),
                Modified.prop(Properties.BORDER_COLOR, color),
                Modified.prop(Properties.BORDER_RADIUS, radius));
    }
}
