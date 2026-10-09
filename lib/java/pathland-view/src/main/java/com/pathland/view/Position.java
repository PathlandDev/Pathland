package com.pathland.view;

import java.util.function.Consumer;

/**
 * absolute placement within the parent ({@code POSITION_X} / {@code POSITION_Y}).
 */
public final class Position implements ViewModifier {

    /** {@link Position} values. */
    public static final class Config {

        private Float x;
        private Float y;

        /** Set the horizontal position. */
        public Config x(float x) {
            this.x = x;
            return this;
        }

        /** Set the vertical position. */
        public Config y(float y) {
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
        return Modified.props(content,
                Modified.prop(Properties.POSITION_X, config.x != null ? config.x : 0f),
                Modified.prop(Properties.POSITION_Y, config.y != null ? config.y : 0f));
    }
}
