package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * a border ({@code BORDER_COLOR} + {@code BORDER_WIDTH} + optional {@code BORDER_RADIUS}).
 */
public final class Border implements ViewModifier {

    /** {@link Border} values. */
    public static final class Config {

        private Signal<Color> color;
        private Signal<Float> width;
        private Signal<Float> radius;

        /** Set a static border color. */
        public Config color(Color color) {
            this.color = Signals.constant(color);
            return this;
        }

        /** Bind the border color to a signal. */
        public Config color(Signal<Color> color) {
            this.color = color;
            return this;
        }

        /** Set a static border width (defaults to {@code 1}). */
        public Config width(float width) {
            this.width = Signals.constant(width);
            return this;
        }

        /** Bind the border width to a signal. */
        public Config width(Signal<Float> width) {
            this.width = width;
            return this;
        }

        /** Set a static corner radius. */
        public Config radius(float radius) {
            this.radius = Signals.constant(radius);
            return this;
        }

        /** Bind the corner radius to a signal. */
        public Config radius(Signal<Float> radius) {
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
        Signal<Color> color = config.color != null ? config.color : Signals.constant(Color.BLACK);
        Signal<Float> width = config.width != null ? config.width : Signals.constant(1f);
        if (config.radius == null) {
            return Modified.props(content,
                    Modified.prop(Properties.BORDER_WIDTH, width),
                    Modified.prop(Properties.BORDER_COLOR, color));
        }
        return Modified.props(content,
                Modified.prop(Properties.BORDER_WIDTH, width),
                Modified.prop(Properties.BORDER_COLOR, color),
                Modified.prop(Properties.BORDER_RADIUS, config.radius));
    }
}
