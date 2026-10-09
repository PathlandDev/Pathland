package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * a shadow ({@code SHADOW_COLOR} + {@code SHADOW_RADIUS} + {@code SHADOW_X} + {@code SHADOW_Y}).
 */
public final class Shadow implements ViewModifier {

    /** {@link Shadow} values. */
    public static final class Config {

        private Signal<Color> color;
        private Signal<Float> radius;
        private Signal<Float> x;
        private Signal<Float> y;

        /** Set a static shadow color. */
        public Config color(Color color) {
            this.color = Signals.constant(color);
            return this;
        }

        /** Bind the shadow color to a signal. */
        public Config color(Signal<Color> color) {
            this.color = color;
            return this;
        }

        /** Set a static blur radius. */
        public Config radius(float radius) {
            this.radius = Signals.constant(radius);
            return this;
        }

        /** Bind the blur radius to a signal. */
        public Config radius(Signal<Float> radius) {
            this.radius = radius;
            return this;
        }

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

    private Shadow(Config config) {
        this.config = config;
    }

    /** A shadow with color, radius, and offset. */
    public static Shadow of(Color color, float radius, float x, float y) {
        return new Shadow(new Config().color(color).radius(radius).x(x).y(y));
    }

    /** A shadow with just a radius (renderer-token color). */
    public static Shadow of(float radius) {
        return new Shadow(new Config().radius(radius));
    }

    /** Configure the shadow. */
    public static Shadow with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Shadow(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> radius = config.radius != null ? config.radius : Signals.constant(0f);
        if (config.color == null) {
            return Modified.props(content, Modified.prop(Properties.SHADOW_RADIUS, radius));
        }
        Signal<Float> x = config.x != null ? config.x : Signals.constant(0f);
        Signal<Float> y = config.y != null ? config.y : Signals.constant(0f);
        return Modified.props(content,
                Modified.prop(Properties.SHADOW_COLOR, config.color),
                Modified.prop(Properties.SHADOW_RADIUS, radius),
                Modified.prop(Properties.SHADOW_X, x),
                Modified.prop(Properties.SHADOW_Y, y));
    }
}
