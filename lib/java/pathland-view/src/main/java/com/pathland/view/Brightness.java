package com.pathland.view;

import java.util.function.Consumer;

/**
 * brightness filter ({@code -1..1}).
 */
public final class Brightness implements ViewModifier {

    /** {@link Brightness} values. */
    public static final class Config {

        private float value;

        /** Set the brightness. */
        public Config value(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private Brightness(float value) {
        this.value = value;
    }

    public static Brightness of(float value) {
        return new Brightness(value);
    }

    /** Configure the brightness. */
    public static Brightness with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Brightness(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.BRIGHTNESS, value));
    }
}
