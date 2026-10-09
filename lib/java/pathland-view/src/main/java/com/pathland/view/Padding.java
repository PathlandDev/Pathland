package com.pathland.view;

import java.util.function.Consumer;

/**
 * uniform or per-edge padding.
 */
public final class Padding implements ViewModifier {

    /** {@link Padding} values. */
    public static final class Config {

        private Float uniform;
        private float[] edges;

        /** Uniform padding on every edge. */
        public Config uniform(float value) {
            this.uniform = value;
            this.edges = null;
            return this;
        }

        /** Per-edge padding (top, right, bottom, left). */
        public Config edges(float top, float right, float bottom, float left) {
            this.edges = new float[] { top, right, bottom, left };
            this.uniform = null;
            return this;
        }
    }

    private final Config config;

    private Padding(Config config) {
        this.config = config;
    }

    /** Uniform padding. */
    public static Padding of(float value) {
        return new Padding(new Config().uniform(value));
    }

    /** Per-edge padding (top, right, bottom, left). */
    public static Padding of(float top, float right, float bottom, float left) {
        return new Padding(new Config().edges(top, right, bottom, left));
    }

    /** Configure the padding. */
    public static Padding with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Padding(config);
    }

    @Override
    public View body(View content) {
        if (config.edges == null) {
            return Modified.props(content, Modified.prop(Properties.PADDING, config.uniform));
        }
        float[] edges = config.edges;
        return Modified.props(content,
                Modified.prop(Properties.PADDING_TOP, edges[0]),
                Modified.prop(Properties.PADDING_RIGHT, edges[1]),
                Modified.prop(Properties.PADDING_BOTTOM, edges[2]),
                Modified.prop(Properties.PADDING_LEFT, edges[3]));
    }
}
