package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * font width (0.5–1.5, 1.0 default).
 */
public final class FontWidth implements ViewModifier {

    /** {@link FontWidth} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static font width. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the font width to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private FontWidth(Config config) {
        this.config = config;
    }

    public static FontWidth of(float value) {
        return new FontWidth(new Config().value(value));
    }

    /** Configure the font width. */
    public static FontWidth with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new FontWidth(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(1f);
        return Modified.props(content, Modified.prop(Properties.FONT_WIDTH, value));
    }
}
