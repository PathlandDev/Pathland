package com.pathland.view;

/**
 * hides the view ({@code VISIBLE}=0).
 */
public final class Hidden implements ViewModifier {

    /** Hidden. */
    static Hidden with() {
        return new Hidden();
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.VISIBLE, 0));
    }
}
