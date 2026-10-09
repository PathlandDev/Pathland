package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * opacity ({@code 0..1}).
 */
public final class Opacity implements ViewModifier {

    /** {@link Opacity} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static opacity ({@code 0..1}). */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the opacity ({@code 0..1}) to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private Opacity(Config config) {
        this.config = config;
    }

    public static Opacity of(float value) {
        return new Opacity(new Config().value(value));
    }

    /** Configure the opacity. */
    public static Opacity with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Opacity(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(1f);
        return Modified.props(content, Modified.prop(Properties.OPACITY, value));
    }
}
