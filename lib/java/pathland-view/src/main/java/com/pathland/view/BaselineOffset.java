package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * baseline offset in points.
 */
public final class BaselineOffset implements ViewModifier {

    /** {@link BaselineOffset} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static baseline offset. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the baseline offset to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private BaselineOffset(Config config) {
        this.config = config;
    }

    public static BaselineOffset of(float value) {
        return new BaselineOffset(new Config().value(value));
    }

    /** Configure the baseline offset. */
    public static BaselineOffset with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new BaselineOffset(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.BASELINE_OFFSET, value));
    }
}
