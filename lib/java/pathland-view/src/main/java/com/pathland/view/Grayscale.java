package com.pathland.view;

import java.util.function.Consumer;

/**
 * grayscale filter ({@code 0..1}).
 */
public final class Grayscale implements ViewModifier {

    /** {@link Grayscale} values. */
    public static final class Config {

        private float value;

        /** Set the grayscale amount. */
        public Config value(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private Grayscale(float value) {
        this.value = value;
    }

    public static Grayscale of(float value) {
        return new Grayscale(value);
    }

    /** Configure the grayscale. */
    public static Grayscale with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Grayscale(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.GRAYSCALE, value));
    }
}
