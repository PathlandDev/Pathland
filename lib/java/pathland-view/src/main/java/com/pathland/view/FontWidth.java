package com.pathland.view;

import java.util.function.Consumer;

/**
 * font width (0.5–1.5, 1.0 default).
 */
public final class FontWidth implements ViewModifier {

    /** {@link FontWidth} values. */
    public static final class Config {

        private float value;

        /** Set the font width. */
        public Config value(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private FontWidth(float value) {
        this.value = value;
    }

    public static FontWidth of(float value) {
        return new FontWidth(value);
    }

    /** Configure the font width. */
    public static FontWidth with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new FontWidth(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.FONT_WIDTH, value));
    }
}
