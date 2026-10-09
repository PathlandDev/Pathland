package com.pathland.view;

import java.util.function.Consumer;

/**
 * a shadow ({@code SHADOW_COLOR} + {@code SHADOW_RADIUS} + {@code SHADOW_X} + {@code SHADOW_Y}).
 */
public final class Shadow implements ViewModifier {

    /** {@link Shadow} values. */
    public static final class Config {

        private Color color;
        private Float radius;
        private Float x;
        private Float y;

        /** Set the shadow color. */
        public Config color(Color color) {
            this.color = color;
            return this;
        }

        /** Set the blur radius. */
        public Config radius(float radius) {
            this.radius = radius;
            return this;
        }

        /** Set the horizontal offset. */
        public Config x(float x) {
            this.x = x;
            return this;
        }

        /** Set the vertical offset. */
        public Config y(float y) {
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
        Float radius = config.radius != null ? config.radius : 0f;
        if (config.color == null) {
            return Modified.props(content, Modified.prop(Properties.SHADOW_RADIUS, radius));
        }
        return Modified.props(content,
                Modified.prop(Properties.SHADOW_COLOR, config.color),
                Modified.prop(Properties.SHADOW_RADIUS, radius),
                Modified.prop(Properties.SHADOW_X, config.x != null ? config.x : 0f),
                Modified.prop(Properties.SHADOW_Y, config.y != null ? config.y : 0f));
    }
}
