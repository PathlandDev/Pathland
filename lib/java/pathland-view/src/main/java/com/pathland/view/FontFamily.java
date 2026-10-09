package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * font family (a {@code STRING} property).
 */
public final class FontFamily implements ViewModifier {

    /** {@link FontFamily} values. */
    public static final class Config {

        private Signal<String> value;

        /** Set a static font family name. */
        public Config family(String value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the font family name to a signal. */
        public Config family(Signal<String> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private FontFamily(Config config) {
        this.config = config;
    }

    public static FontFamily of(String value) {
        return new FontFamily(new Config().family(value));
    }

    /** Configure the font family. */
    public static FontFamily with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new FontFamily(config);
    }

    @Override
    public View body(View content) {
        Signal<String> value = config.value != null ? config.value : Signals.constant("");
        return Modified.props(content, Modified.prop(Properties.FONT_FAMILY, value));
    }
}
