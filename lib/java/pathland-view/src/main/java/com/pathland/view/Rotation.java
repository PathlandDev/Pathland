package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * rotation after layout, in degrees (counter-clockwise).
 */
public final class Rotation implements ViewModifier {

    /** {@link Rotation} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static rotation in degrees. */
        public Config degrees(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the rotation (degrees) to a signal. */
        public Config degrees(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private Rotation(Config config) {
        this.config = config;
    }

    public static Rotation of(float value) {
        return new Rotation(new Config().degrees(value));
    }

    /** Configure the rotation. */
    public static Rotation with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Rotation(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.ROTATION_DEGREES, value));
    }
}
