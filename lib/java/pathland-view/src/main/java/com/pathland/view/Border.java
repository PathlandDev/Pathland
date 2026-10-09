package com.pathland.view;

import java.util.function.Consumer;

/**
 * a border ({@code BORDER_COLOR} + {@code BORDER_WIDTH} + optional {@code BORDER_RADIUS}).
 */
public final class Border implements ViewModifier {

    /** {@link Border} values. */
    public static final class Config {

        private Color color;
        private Float width;
        private Float radius;

        /** Set the border color. */
        public Config color(Color color) {
            this.color = color;
            return this;
        }

        /** Set the border width (defaults to {@code 1}). */
        public Config width(float width) {
            this.width = width;
            return this;
        }

        /** Set the corner radius. */
        public Config radius(float radius) {
            this.radius = radius;
            return this;
        }
    }

    private final Config config;

    private Border(Config config) {
        this.config = config;
    }

    /** A border of {@code color} and {@code width} ({@code .border(_:width:)}). */
    public static Border of(Color color, float width) {
        return new Border(new Config().color(color).width(width));
    }

    /** A border of {@code color}, {@code width}, and {@code radius}. */
    public static Border of(Color color, float width, float radius) {
        return new Border(new Config().color(color).width(width).radius(radius));
    }

    /** Configure the border. */
    public static Border with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Border(config);
    }

    @Override
    public View body(View content) {
        float width = config.width != null ? config.width : 1f;
        if (config.radius == null) {
            return Modified.props(content,
                    Modified.prop(Properties.BORDER_WIDTH, width),
                    Modified.prop(Properties.BORDER_COLOR, config.color));
        }
        return Modified.props(content,
                Modified.prop(Properties.BORDER_WIDTH, width),
                Modified.prop(Properties.BORDER_COLOR, config.color),
                Modified.prop(Properties.BORDER_RADIUS, config.radius));
    }
}
