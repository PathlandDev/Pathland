package com.pathland.view;

/**
 * fits the content within its bounds ({@code CONTENT_MODE=Fit}).
 */
public final class ScaledToFit implements ViewModifier {

    /** Fit. */
    public static ScaledToFit of() {
        return new ScaledToFit();
    }

    /** Fit. */
    public static ScaledToFit with() {
        return new ScaledToFit();
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.CONTENT_MODE, (float) ContentMode.FIT.wire()));
    }
}
