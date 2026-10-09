package com.pathland.view;

import java.util.function.Consumer;

/**
 * corner radius ({@code BORDER_RADIUS}).
 */
public final class CornerRadius implements ViewModifier {

    /** {@link CornerRadius} values. */
    public static final class Config {

        private float value;

        /** Set the corner radius. */
        public Config radius(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private CornerRadius(float value) {
        this.value = value;
    }

    public static CornerRadius of(float value) {
        return new CornerRadius(value);
    }

    /** Configure the corner radius. */
    public static CornerRadius with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new CornerRadius(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.BORDER_RADIUS, value));
    }
}
