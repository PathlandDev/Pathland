package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * text font size in points ({@code FONT_SIZE}); a single {@link Signal<Float>} —
 * a static size is sugar for a constant signal.
 */
public final class FontSize implements ViewModifier {

    /** {@link FontSize} values. */
    public static final class Config {

        private Signal<Float> signal;

        /** Set a static size. */
        public Config size(float size) {
            this.signal = Signals.constant(size);
            return this;
        }

        /** Bind the size to a signal. */
        public Config size(Signal<Float> signal) {
            this.signal = signal;
            return this;
        }
    }

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

    /** Configure the font size. */
    public static FontSize with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new FontSize(config.signal);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.FONT_SIZE, signal));
    }
}
