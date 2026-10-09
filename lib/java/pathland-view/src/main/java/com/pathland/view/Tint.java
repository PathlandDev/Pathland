package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * accent/tint color ({@code TINT}).
 */
public final class Tint implements ViewModifier {

    /** {@link Tint} values. */
    public static final class Config {

        private Signal<Color> color;

        /** Set a static tint color. */
        public Config color(Color color) {
            this.color = Signals.constant(color);
            return this;
        }

        /** Bind the tint color to a signal. */
        public Config color(Signal<Color> color) {
            this.color = color;
            return this;
        }
    }

    private final Config config;

    private Tint(Config config) {
        this.config = config;
    }

    /** The accent/tint color. */
    public static Tint of(Color color) {
        return new Tint(new Config().color(color));
    }

    /** Configure the tint. */
    public static Tint with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Tint(config);
    }

    @Override
    public View body(View content) {
        Signal<Color> color = config.color != null ? config.color : Signals.constant(Color.CLEAR);
        return Modified.props(content, Modified.prop(Properties.TINT, color));
    }
}
