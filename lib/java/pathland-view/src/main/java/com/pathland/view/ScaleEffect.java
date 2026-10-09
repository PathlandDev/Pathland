package com.pathland.view;

import java.util.function.Consumer;

/**
 * uniform scale after layout.
 */
public final class ScaleEffect implements ViewModifier {

    /** {@link ScaleEffect} values. */
    public static final class Config {

        private float value;

        /** Set the scale factor. */
        public Config value(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private ScaleEffect(float value) {
        this.value = value;
    }

    public static ScaleEffect of(float value) {
        return new ScaleEffect(value);
    }

    /** Configure the scale. */
    public static ScaleEffect with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ScaleEffect(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.SCALE, value));
    }
}
