package com.pathland.view;

import java.util.function.Consumer;

/**
 * baseline offset in points.
 */
public final class BaselineOffset implements ViewModifier {

    /** {@link BaselineOffset} values. */
    public static final class Config {

        private float value;

        /** Set the baseline offset. */
        public Config value(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private BaselineOffset(float value) {
        this.value = value;
    }

    public static BaselineOffset of(float value) {
        return new BaselineOffset(value);
    }

    /** Configure the baseline offset. */
    public static BaselineOffset with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new BaselineOffset(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.BASELINE_OFFSET, value));
    }
}
