package dev.pathland.view;

import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

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

    static ImageSource with(String value) {
        return Configurators.modifier(Config::new, ImageSource::new, c -> c.source(value));
    }

    static ImageSource with(Signal<String> value) {
        return Configurators.modifier(Config::new, ImageSource::new, c -> c.source(value));
    }

    /** Configure the image source. */
    static ImageSource with(Consumer<Config> configure) {
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
