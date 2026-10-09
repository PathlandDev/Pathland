package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * foreground style/color ({@code COLOR}); a single {@link Signal<Color>} — a
 * static color is sugar for a constant signal. Never a {@code .color()} modifier.
 */
public final class ForegroundStyle implements ViewModifier {

    /** {@link ForegroundStyle} values. */
    public static final class Config {

        private Signal<Color> signal;

        /** Set a static color. */
        public Config color(Color value) {
            this.signal = Signals.constant(value);
            return this;
        }

        /** Bind the color to a signal. */
        public Config color(Signal<Color> signal) {
            this.signal = signal;
            return this;
        }
    }

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

    /** Configure the foreground style. */
    public static ForegroundStyle with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ForegroundStyle(config.signal);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.COLOR, signal));
    }
}
