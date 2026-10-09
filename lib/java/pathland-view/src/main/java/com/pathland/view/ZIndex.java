package com.pathland.view;

import java.util.function.Consumer;

/**
 * draw-order elevation within a stack.
 */
public final class ZIndex implements ViewModifier {

    /** {@link ZIndex} values. */
    public static final class Config {

        private float value;

        /** Set the z-index. */
        public Config value(float value) {
            this.value = value;
            return this;
        }
    }

    private final float value;

    private ZIndex(float value) {
        this.value = value;
    }

    public static ZIndex of(float value) {
        return new ZIndex(value);
    }

    /** Configure the z-index. */
    public static ZIndex with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ZIndex(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.Z_INDEX, value));
    }
}
