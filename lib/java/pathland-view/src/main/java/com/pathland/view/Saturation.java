package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * saturation filter ({@code 0..1}).
 */
public final class Saturation implements ViewModifier {

    /** {@link Saturation} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static saturation. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the saturation to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private Saturation(Config config) {
        this.config = config;
    }

    public static Saturation of(float value) {
        return new Saturation(new Config().value(value));
    }

    /** Configure the saturation. */
    public static Saturation with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Saturation(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(1f);
        return Modified.props(content, Modified.prop(Properties.SATURATION, value));
    }
}
