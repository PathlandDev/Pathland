package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * per-glyph kerning in points.
 */
public final class Kerning implements ViewModifier {

    /** {@link Kerning} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static kerning. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the kerning to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private Kerning(Config config) {
        this.config = config;
    }

    public static Kerning of(float value) {
        return new Kerning(new Config().value(value));
    }

    /** Configure the kerning. */
    public static Kerning with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Kerning(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.KERNING, value));
    }
}
