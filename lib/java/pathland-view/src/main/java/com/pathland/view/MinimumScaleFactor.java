package com.pathland.view;

import java.util.function.Consumer;

/**
 * minimum scale factor for text shrinking.
 */
public final class MinimumScaleFactor implements ViewModifier {

    /** {@link MinimumScaleFactor} values. */
    public static final class Config {

        private float value;

        /** Set the minimum scale factor. */
        public Config value(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private MinimumScaleFactor(float value) {
        this.value = value;
    }

    public static MinimumScaleFactor of(float value) {
        return new MinimumScaleFactor(value);
    }

    /** Configure the minimum scale factor. */
    public static MinimumScaleFactor with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new MinimumScaleFactor(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.MINIMUM_SCALE_FACTOR, value));
    }
}
