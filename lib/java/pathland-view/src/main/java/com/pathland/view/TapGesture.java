package com.pathland.view;

/**
 * attaches a tap gesture ({@code onTapGesture}).
 */
public final class TapGesture implements ViewModifier {

    private final Runnable action;

    private TapGesture(Runnable action) {
        this.action = action;
    }

    public static TapGesture of(Runnable action) {
        return new TapGesture(action);
    }

    @Override
    public View body(View content) {
        return new TapGestureView(content, action);
    }
}
