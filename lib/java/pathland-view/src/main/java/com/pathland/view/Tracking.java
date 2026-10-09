package com.pathland.view;

import java.util.function.Consumer;

/**
 * uniform letter spacing in points.
 */
public final class Tracking implements ViewModifier {

    /** {@link Tracking} values. */
    public static final class Config {

        private float value;

        /** Set the tracking. */
        public Config value(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private Tracking(float value) {
        this.value = value;
    }

    public static Tracking of(float value) {
        return new Tracking(value);
    }

    /** Configure the tracking. */
    public static Tracking with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Tracking(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.TRACKING, value));
    }
}
