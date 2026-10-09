package com.pathland.view;

import java.util.function.Consumer;

/**
 * the image source (a {@code STRING} property).
 */
public final class ImageSource implements ViewModifier {

    /** {@link ImageSource} values. */
    public static final class Config {

        private String value;

        /** Set the image source. */
        public Config source(String value) {
            this.value = value;
            return this;
        }
    }

    private final String value;

    private ImageSource(String value) {
        this.value = value;
    }

    public static ImageSource of(String value) {
        return new ImageSource(value);
    }

    /** Configure the image source. */
    public static ImageSource with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ImageSource(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.IMAGE_SOURCE, value));
    }
}
