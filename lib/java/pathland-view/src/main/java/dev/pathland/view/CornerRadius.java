package dev.pathland.view;

import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * corner radius ({@code BORDER_RADIUS}).
 */
public final class CornerRadius implements ViewModifier {

    /** {@link CornerRadius} values. */
    public static final class Config {

        private Signal<Float> value;
        private Length token;

        /** Set a static corner radius. */
        public Config radius(float value) {
            this.value = Signals.constant(value);
            this.token = null;
            return this;
        }

        /** Bind the corner radius to a signal. */
        public Config radius(Signal<Float> value) {
            this.value = value;
            this.token = null;
            return this;
        }

        /** Reference a radius design token (e.g. {@code radius.lg}). */
        public Config radius(Length token) {
            this.token = token;
            this.value = null;
            return this;
        }
    }

    private final Config config;

    private CornerRadius(Config config) {
        this.config = config;
    }

    static CornerRadius with(float value) {
        return Configurators.modifier(Config::new, CornerRadius::new, c -> c.radius(value));
    }

    static CornerRadius with(Signal<Float> value) {
        return Configurators.modifier(Config::new, CornerRadius::new, c -> c.radius(value));
    }

    static CornerRadius with(Length token) {
        return Configurators.modifier(Config::new, CornerRadius::new, c -> c.radius(token));
    }

    /** Configure the corner radius. */
    static CornerRadius with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new CornerRadius(config);
    }

    @Override
    public View body(View content) {
        if (config.token != null) {
            return Modified.props(content, Modified.prop(Properties.BORDER_RADIUS, config.token));
        }
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.BORDER_RADIUS, value));
    }
}
