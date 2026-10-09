package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * background color ({@code BACKGROUND_COLOR}); a single {@link Signal<Color>} —
 * a static color is sugar for a constant signal.
 */
public final class Background implements ViewModifier {

    /** {@link Background} values. */
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

    private Background(Signal<Color> signal) {
        this.signal = signal;
    }

    public static Background of(Color value) {
        return new Background(Signals.constant(value));
    }

    public static Background of(Signal<Color> signal) {
        return new Background(signal);
    }

    /** Configure the background. */
    public static Background with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Background(config.signal);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.BACKGROUND_COLOR, signal));
    }
}
