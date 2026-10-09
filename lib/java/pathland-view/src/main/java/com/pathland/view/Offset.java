package com.pathland.view;

import java.util.function.Consumer;

/**
 * a post-layout translation ({@code OFFSET_X} / {@code OFFSET_Y}).
 */
public final class Offset implements ViewModifier {

    /** {@link Offset} values. */
    public static final class Config {

        private Float x;
        private Float y;

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
        return Modified.props(content,
                Modified.prop(Properties.OFFSET_X, config.x != null ? config.x : 0f),
                Modified.prop(Properties.OFFSET_Y, config.y != null ? config.y : 0f));
    }
}
