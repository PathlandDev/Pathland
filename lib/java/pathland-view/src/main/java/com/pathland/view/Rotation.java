package com.pathland.view;

import java.util.function.Consumer;

/**
 * rotation after layout, in degrees (counter-clockwise).
 */
public final class Rotation implements ViewModifier {

    /** {@link Rotation} values. */
    public static final class Config {

        private float value;

        /** Set the rotation in degrees. */
        public Config degrees(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private Rotation(float value) {
        this.value = value;
    }

    public static Rotation of(float value) {
        return new Rotation(value);
    }

    /** Configure the rotation. */
    public static Rotation with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Rotation(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.ROTATION_DEGREES, value));
    }
}
