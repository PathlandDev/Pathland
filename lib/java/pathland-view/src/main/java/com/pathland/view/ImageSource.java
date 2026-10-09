package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * the image source (a {@code STRING} property).
 */
public final class ImageSource implements ViewModifier {

    /** {@link ImageSource} values. */
    public static final class Config {

        private Signal<String> value;

        /** Set a static image source. */
        public Config source(String value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the image source to a signal. */
        public Config source(Signal<String> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private ImageSource(Config config) {
        this.config = config;
    }

    public static ImageSource of(String value) {
        return new ImageSource(new Config().source(value));
    }

    /** Configure the image source. */
    public static ImageSource with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ImageSource(config);
    }

    @Override
    public View body(View content) {
        Signal<String> value = config.value != null ? config.value : Signals.constant("");
        return Modified.props(content, Modified.prop(Properties.IMAGE_SOURCE, value));
    }
}
