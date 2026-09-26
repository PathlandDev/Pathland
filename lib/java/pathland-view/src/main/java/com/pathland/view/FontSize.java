package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

/**
 * text font size in points ({@code FONT_SIZE}); a single {@link Signal<Float>} —
 * a static size is sugar for a constant signal.
 */
public final class FontSize implements ViewModifier {

    private final Signal<Float> signal;

    private FontSize(Signal<Float> signal) {
        this.signal = signal;
    }

    public static FontSize of(float size) {
        return new FontSize(Signals.constant(size));
    }

    public static FontSize of(Signal<Float> signal) {
        return new FontSize(signal);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.FONT_SIZE, signal));
    }
}