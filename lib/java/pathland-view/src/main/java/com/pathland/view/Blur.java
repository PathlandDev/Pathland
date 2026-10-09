package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * Gaussian blur radius.
 */
public final class Blur implements ViewModifier {

    /** {@link Blur} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static blur radius. */
        public Config radius(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the blur radius to a signal. */
        public Config radius(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private Blur(Config config) {
        this.config = config;
    }

    public static Blur of(float value) {
        return new Blur(new Config().radius(value));
    }

    /** Configure the blur. */
    public static Blur with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Blur(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.BLUR_RADIUS, value));
    }
}
