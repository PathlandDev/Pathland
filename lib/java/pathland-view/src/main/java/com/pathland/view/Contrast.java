package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * contrast filter ({@code 0..1}).
 */
public final class Contrast implements ViewModifier {

    /** {@link Contrast} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static contrast. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the contrast to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private Contrast(Config config) {
        this.config = config;
    }

    public static Contrast of(float value) {
        return new Contrast(new Config().value(value));
    }

    /** Configure the contrast. */
    public static Contrast with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Contrast(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(1f);
        return Modified.props(content, Modified.prop(Properties.CONTRAST, value));
    }
}
