package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * grayscale filter ({@code 0..1}).
 */
public final class Grayscale implements ViewModifier {

    /** {@link Grayscale} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static grayscale amount. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the grayscale amount to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private Grayscale(Config config) {
        this.config = config;
    }

    public static Grayscale of(float value) {
        return new Grayscale(new Config().value(value));
    }

    /** Configure the grayscale. */
    public static Grayscale with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Grayscale(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.GRAYSCALE, value));
    }
}
