package com.pathland.view;

/**
 * inverts the view's colors ({@code COLOR_INVERT}).
 */
public final class ColorInvert implements ViewModifier {

    /** Invert. */
    public static ColorInvert of() {
        return new ColorInvert();
    }

    /** Invert. */
    public static ColorInvert with() {
        return new ColorInvert();
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.COLOR_INVERT, 1));
    }
}
