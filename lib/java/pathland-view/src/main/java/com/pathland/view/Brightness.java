package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * brightness filter ({@code -1..1}).
 */
public final class Brightness implements ViewModifier {

    /** {@link Brightness} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static brightness. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the brightness to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private Brightness(Config config) {
        this.config = config;
    }

    public static Brightness of(float value) {
        return new Brightness(new Config().value(value));
    }

    /** Configure the brightness. */
    public static Brightness with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Brightness(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.BRIGHTNESS, value));
    }
}
