package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * uniform scale after layout.
 */
public final class ScaleEffect implements ViewModifier {

    /** {@link ScaleEffect} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static scale factor. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the scale factor to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private ScaleEffect(Config config) {
        this.config = config;
    }

    public static ScaleEffect of(float value) {
        return new ScaleEffect(new Config().value(value));
    }

    /** Configure the scale. */
    public static ScaleEffect with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ScaleEffect(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(1f);
        return Modified.props(content, Modified.prop(Properties.SCALE, value));
    }
}
