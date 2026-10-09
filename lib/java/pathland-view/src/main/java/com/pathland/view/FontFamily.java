package com.pathland.view;

import java.util.function.Consumer;

/**
 * font family (a {@code STRING} property).
 */
public final class FontFamily implements ViewModifier {

    /** {@link FontFamily} values. */
    public static final class Config {

        private String value;

        /** Set the font family name. */
        public Config family(String value) {
            this.value = value;
            return this;
        }
    }

    private final String value;

    private FontFamily(String value) {
        this.value = value;
    }

    public static FontFamily of(String value) {
        return new FontFamily(value);
    }

    /** Configure the font family. */
    public static FontFamily with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new FontFamily(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.FONT_FAMILY, value));
    }
}
