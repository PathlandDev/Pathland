package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

/**
 * foreground style/color ({@code COLOR}); a single {@link Signal<Color>} — a
 * static color is sugar for a constant signal. Never a {@code .color()} modifier.
 */
public final class ForegroundStyle implements ViewModifier {

    private final Signal<Color> signal;

    private ForegroundStyle(Signal<Color> signal) {
        this.signal = signal;
    }

    public static ForegroundStyle of(Color value) {
        return new ForegroundStyle(Signals.constant(value));
    }

    public static ForegroundStyle of(Signal<Color> signal) {
        return new ForegroundStyle(signal);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.COLOR, signal));
    }
}