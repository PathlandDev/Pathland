package dev.pathland.view;

import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

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
        private Length widthToken;
        private Length radiusToken;

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
            this.widthToken = null;
            return this;
        }

        /** Bind the border width to a signal. */
        public Config width(Signal<Float> width) {
            this.width = width;
            this.widthToken = null;
            return this;
        }

        /** Reference a border-width design token (e.g. {@code border.width.thin}). */
        public Config width(Length width) {
            this.widthToken = width;
            this.width = null;
            return this;
        }

        /** Set a static corner radius. */
        public Config radius(float radius) {
            this.radius = Signals.constant(radius);
            this.radiusToken = null;
            return this;
        }

        /** Bind the corner radius to a signal. */
        public Config radius(Signal<Float> radius) {
            this.radius = radius;
            this.radiusToken = null;
            return this;
        }

        /** Reference a radius design token (e.g. {@code radius.lg}). */
        public Config radius(Length radius) {
            this.radiusToken = radius;
            this.radius = null;
            return this;
        }
    }

    private final Config config;

    private Border(Config config) {
        this.config = config;
    }

    /** Configure the border. */
    static Border with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Border(config);
    }

    @Override
    public View body(View content) {
        Signal<Color> color = config.color != null ? config.color : Signals.constant(Color.BLACK);
        Modified.Prop width = config.widthToken != null
                ? Modified.prop(Properties.BORDER_WIDTH, config.widthToken)
                : Modified.prop(Properties.BORDER_WIDTH,
                        config.width != null ? config.width : Signals.constant(1f));
        if (config.radiusToken == null && config.radius == null) {
            return Modified.props(content,
                    width,
                    Modified.prop(Properties.BORDER_COLOR, color));
        }
        Modified.Prop radius = config.radiusToken != null
                ? Modified.prop(Properties.BORDER_RADIUS, config.radiusToken)
                : Modified.prop(Properties.BORDER_RADIUS, config.radius);
        return Modified.props(content,
                width,
                Modified.prop(Properties.BORDER_COLOR, color),
                radius);
    }
}
