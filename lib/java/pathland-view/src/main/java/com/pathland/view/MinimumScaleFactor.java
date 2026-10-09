package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * minimum scale factor for text shrinking.
 */
public final class MinimumScaleFactor implements ViewModifier {

    /** {@link MinimumScaleFactor} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static minimum scale factor. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the minimum scale factor to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private MinimumScaleFactor(Config config) {
        this.config = config;
    }

    public static MinimumScaleFactor of(float value) {
        return new MinimumScaleFactor(new Config().value(value));
    }

    /** Configure the minimum scale factor. */
    public static MinimumScaleFactor with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new MinimumScaleFactor(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(1f);
        return Modified.props(content, Modified.prop(Properties.MINIMUM_SCALE_FACTOR, value));
    }
}
