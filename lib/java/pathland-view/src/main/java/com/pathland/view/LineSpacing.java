package com.pathland.view;

import java.util.function.Consumer;

/**
 * line spacing in points.
 */
public final class LineSpacing implements ViewModifier {

    /** {@link LineSpacing} values. */
    public static final class Config {

        private float value;

        /** Set the line spacing. */
        public Config value(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private LineSpacing(float value) {
        this.value = value;
    }

    public static LineSpacing of(float value) {
        return new LineSpacing(value);
    }

    /** Configure the line spacing. */
    public static LineSpacing with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new LineSpacing(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.LINE_SPACING, value));
    }
}
