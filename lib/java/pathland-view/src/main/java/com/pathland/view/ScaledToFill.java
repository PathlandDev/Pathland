package com.pathland.view;

/**
 * fills its bounds, cropping overflow ({@code CONTENT_MODE=Fill}).
 */
public final class ScaledToFill implements ViewModifier {

    /** Fill. */
    public static ScaledToFill of() {
        return new ScaledToFill();
    }

    /** Fill. */
    public static ScaledToFill with() {
        return new ScaledToFill();
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.CONTENT_MODE, (float) ContentMode.FILL.wire()));
    }
}
