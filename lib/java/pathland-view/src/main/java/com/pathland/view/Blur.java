package com.pathland.view;

import java.util.function.Consumer;

/**
 * Gaussian blur radius.
 */
public final class Blur implements ViewModifier {

    /** {@link Blur} values. */
    public static final class Config {

        private float value;

        /** Set the blur radius. */
        public Config radius(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private Blur(float value) {
        this.value = value;
    }

    public static Blur of(float value) {
        return new Blur(value);
    }

    /** Configure the blur. */
    public static Blur with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Blur(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.BLUR_RADIUS, value));
    }
}
