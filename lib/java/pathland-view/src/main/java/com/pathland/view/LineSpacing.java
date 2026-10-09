package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * line spacing in points.
 */
public final class LineSpacing implements ViewModifier {

    /** {@link LineSpacing} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static line spacing. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the line spacing to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private LineSpacing(Config config) {
        this.config = config;
    }

    public static LineSpacing of(float value) {
        return new LineSpacing(new Config().value(value));
    }

    /** Configure the line spacing. */
    public static LineSpacing with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new LineSpacing(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.LINE_SPACING, value));
    }
}
