package com.pathland.view;

import java.util.function.Consumer;

/**
 * hue rotation in degrees.
 */
public final class HueRotation implements ViewModifier {

    /** {@link HueRotation} values. */
    public static final class Config {

        private float value;

        /** Set the hue rotation in degrees. */
        public Config degrees(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private HueRotation(float value) {
        this.value = value;
    }

    public static HueRotation of(float value) {
        return new HueRotation(value);
    }

    /** Configure the hue rotation. */
    public static HueRotation with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new HueRotation(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.HUE_ROTATION, value));
    }
}
