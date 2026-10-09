package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * corner radius ({@code BORDER_RADIUS}).
 */
public final class CornerRadius implements ViewModifier {

    /** {@link CornerRadius} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static corner radius. */
        public Config radius(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the corner radius to a signal. */
        public Config radius(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private CornerRadius(Config config) {
        this.config = config;
    }

    public static CornerRadius of(float value) {
        return new CornerRadius(new Config().radius(value));
    }

    /** Configure the corner radius. */
    public static CornerRadius with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new CornerRadius(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.BORDER_RADIUS, value));
    }
}
