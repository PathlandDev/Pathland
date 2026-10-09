package com.pathland.view;

/**
 * clips the view to its bounds ({@code CLIPS_TO_BOUNDS}).
 */
public final class Clipped implements ViewModifier {

    /** Clip. */
    public static Clipped of() {
        return new Clipped();
    }

    /** Clip. */
    public static Clipped with() {
        return new Clipped();
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.CLIPS_TO_BOUNDS, 1));
    }
}
