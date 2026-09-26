package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

/**
 * background color ({@code BACKGROUND_COLOR}); a single {@link Signal<Color>} —
 * a static color is sugar for a constant signal.
 */
public final class Background implements ViewModifier {

    private final Signal<Color> signal;

    private Background(Signal<Color> signal) {
        this.signal = signal;
    }

    public static Background of(Color value) {
        return new Background(Signals.constant(value));
    }

    public static Background of(Signal<Color> signal) {
        return new Background(signal);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.BACKGROUND_COLOR, signal));
    }
}