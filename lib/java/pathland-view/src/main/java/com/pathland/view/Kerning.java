package com.pathland.view;

import java.util.function.Consumer;

/**
 * per-glyph kerning in points.
 */
public final class Kerning implements ViewModifier {

    /** {@link Kerning} values. */
    public static final class Config {

        private float value;

        /** Set the kerning. */
        public Config value(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private Kerning(float value) {
        this.value = value;
    }

    public static Kerning of(float value) {
        return new Kerning(value);
    }

    /** Configure the kerning. */
    public static Kerning with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Kerning(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.KERNING, value));
    }
}
