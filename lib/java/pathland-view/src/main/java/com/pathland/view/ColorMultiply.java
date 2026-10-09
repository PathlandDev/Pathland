package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * multiplies the view's color ({@code COLOR_MULTIPLY}).
 */
public final class ColorMultiply implements ViewModifier {

    /** {@link ColorMultiply} values. */
    public static final class Config {

        private Signal<Color> color;

        /** Set a static multiply color. */
        public Config color(Color color) {
            this.color = Signals.constant(color);
            return this;
        }

        /** Bind the multiply color to a signal. */
        public Config color(Signal<Color> color) {
            this.color = color;
            return this;
        }
    }

    private final Config config;

    private ColorMultiply(Config config) {
        this.config = config;
    }

    public static ColorMultiply of(Color color) {
        return new ColorMultiply(new Config().color(color));
    }

    /** Configure the color multiply. */
    public static ColorMultiply with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ColorMultiply(config);
    }

    @Override
    public View body(View content) {
        Signal<Color> color = config.color != null ? config.color : Signals.constant(Color.WHITE);
        return Modified.props(content, Modified.prop(Properties.COLOR_MULTIPLY, color));
    }
}
