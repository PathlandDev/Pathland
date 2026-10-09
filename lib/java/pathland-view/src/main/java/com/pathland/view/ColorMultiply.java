package com.pathland.view;

import java.util.function.Consumer;

/**
 * multiplies the view's color ({@code COLOR_MULTIPLY}).
 */
public final class ColorMultiply implements ViewModifier {

    /** {@link ColorMultiply} values. */
    public static final class Config {

        private Color color;

        /** Set the multiply color. */
        public Config color(Color color) {
            this.color = color;
            return this;
        }
    }

    private final Color color;

    private ColorMultiply(Color color) {
        this.color = color;
    }

    public static ColorMultiply of(Color color) {
        return new ColorMultiply(color);
    }

    /** Configure the color multiply. */
    public static ColorMultiply with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ColorMultiply(config.color);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.COLOR_MULTIPLY, color));
    }
}
