package com.pathland.view;

import java.util.function.Consumer;

/**
 * saturation filter ({@code 0..1}).
 */
public final class Saturation implements ViewModifier {

    /** {@link Saturation} values. */
    public static final class Config {

        private float value;

        /** Set the saturation. */
        public Config value(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private Saturation(float value) {
        this.value = value;
    }

    public static Saturation of(float value) {
        return new Saturation(value);
    }

    /** Configure the saturation. */
    public static Saturation with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Saturation(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.SATURATION, value));
    }
}
