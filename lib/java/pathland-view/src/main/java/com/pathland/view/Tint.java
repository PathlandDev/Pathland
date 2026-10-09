package com.pathland.view;

import java.util.function.Consumer;

/**
 * accent/tint color ({@code TINT}).
 */
public final class Tint implements ViewModifier {

    /** {@link Tint} values. */
    public static final class Config {

        private Color color;

        /** Set the tint color. */
        public Config color(Color color) {
            this.color = color;
            return this;
        }
    }

    private final Color color;

    private Tint(Color color) {
        this.color = color;
    }

    /** The accent/tint color. */
    public static Tint of(Color color) {
        return new Tint(color);
    }

    /** Configure the tint. */
    public static Tint with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Tint(config.color);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.TINT, color));
    }
}
