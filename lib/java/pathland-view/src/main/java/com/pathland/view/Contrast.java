package com.pathland.view;

import java.util.function.Consumer;

/**
 * contrast filter ({@code 0..1}).
 */
public final class Contrast implements ViewModifier {

    /** {@link Contrast} values. */
    public static final class Config {

        private float value;

        /** Set the contrast. */
        public Config value(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private Contrast(float value) {
        this.value = value;
    }

    public static Contrast of(float value) {
        return new Contrast(value);
    }

    /** Configure the contrast. */
    public static Contrast with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Contrast(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.CONTRAST, value));
    }
}
