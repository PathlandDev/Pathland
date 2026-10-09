package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * hue rotation in degrees.
 */
public final class HueRotation implements ViewModifier {

    /** {@link HueRotation} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static hue rotation in degrees. */
        public Config degrees(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the hue rotation (degrees) to a signal. */
        public Config degrees(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private HueRotation(Config config) {
        this.config = config;
    }

    public static HueRotation of(float value) {
        return new HueRotation(new Config().degrees(value));
    }

    /** Configure the hue rotation. */
    public static HueRotation with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new HueRotation(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.HUE_ROTATION, value));
    }
}
