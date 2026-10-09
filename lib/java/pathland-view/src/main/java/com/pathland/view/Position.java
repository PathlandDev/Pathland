package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * absolute placement within the parent ({@code POSITION_X} / {@code POSITION_Y}).
 */
public final class Position implements ViewModifier {

    /** {@link Position} values. */
    public static final class Config {

        private Signal<Float> x;
        private Signal<Float> y;

        /** Set a static horizontal position. */
        public Config x(float x) {
            this.x = Signals.constant(x);
            return this;
        }

        /** Bind the horizontal position to a signal. */
        public Config x(Signal<Float> x) {
            this.x = x;
            return this;
        }

        /** Set a static vertical position. */
        public Config y(float y) {
            this.y = Signals.constant(y);
            return this;
        }

        /** Bind the vertical position to a signal. */
        public Config y(Signal<Float> y) {
            this.y = y;
            return this;
        }
    }

    private final Config config;

    private Position(Config config) {
        this.config = config;
    }

    public static Position of(float x, float y) {
        return new Position(new Config().x(x).y(y));
    }

    /** Configure the position. */
    public static Position with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Position(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> x = config.x != null ? config.x : Signals.constant(0f);
        Signal<Float> y = config.y != null ? config.y : Signals.constant(0f);
        return Modified.props(content,
                Modified.prop(Properties.POSITION_X, x),
                Modified.prop(Properties.POSITION_Y, y));
    }
}
