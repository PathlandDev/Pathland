package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * uniform letter spacing in points.
 */
public final class Tracking implements ViewModifier {

    /** {@link Tracking} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static tracking. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the tracking to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private Tracking(Config config) {
        this.config = config;
    }

    public static Tracking of(float value) {
        return new Tracking(new Config().value(value));
    }

    /** Configure the tracking. */
    public static Tracking with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Tracking(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.TRACKING, value));
    }
}
