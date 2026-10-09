package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * a post-layout translation ({@code OFFSET_X} / {@code OFFSET_Y}).
 */
public final class Offset implements ViewModifier {

    /** {@link Offset} values. */
    public static final class Config {

        private Signal<Float> x;
        private Signal<Float> y;

        /** Set a static horizontal offset. */
        public Config x(float x) {
            this.x = Signals.constant(x);
            return this;
        }

        /** Bind the horizontal offset to a signal. */
        public Config x(Signal<Float> x) {
            this.x = x;
            return this;
        }

        /** Set a static vertical offset. */
        public Config y(float y) {
            this.y = Signals.constant(y);
            return this;
        }

        /** Bind the vertical offset to a signal. */
        public Config y(Signal<Float> y) {
            this.y = y;
            return this;
        }
    }

    private final Config config;

    private Offset(Config config) {
        this.config = config;
    }

    public static Offset of(float x, float y) {
        return new Offset(new Config().x(x).y(y));
    }

    /** Configure the offset. */
    public static Offset with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Offset(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> x = config.x != null ? config.x : Signals.constant(0f);
        Signal<Float> y = config.y != null ? config.y : Signals.constant(0f);
        return Modified.props(content,
                Modified.prop(Properties.OFFSET_X, x),
                Modified.prop(Properties.OFFSET_Y, y));
    }
}
