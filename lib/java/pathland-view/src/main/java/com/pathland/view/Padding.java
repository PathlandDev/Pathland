package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * uniform or per-edge padding.
 */
public final class Padding implements ViewModifier {

    /** {@link Padding} values. */
    public static final class Config {

        private Signal<Float> uniform;
        private Signal<Float> top;
        private Signal<Float> right;
        private Signal<Float> bottom;
        private Signal<Float> left;

        /** Uniform padding on every edge. */
        public Config uniform(float value) {
            this.uniform = Signals.constant(value);
            this.top = this.right = this.bottom = this.left = null;
            return this;
        }

        /** Uniform padding on every edge (reactive). */
        public Config uniform(Signal<Float> value) {
            this.uniform = value;
            this.top = this.right = this.bottom = this.left = null;
            return this;
        }

        /** Per-edge padding (top, right, bottom, left). */
        public Config edges(float top, float right, float bottom, float left) {
            return edges(Signals.constant(top), Signals.constant(right),
                    Signals.constant(bottom), Signals.constant(left));
        }

        /** Per-edge padding (top, right, bottom, left), each reactive. */
        public Config edges(Signal<Float> top, Signal<Float> right, Signal<Float> bottom, Signal<Float> left) {
            this.uniform = null;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.left = left;
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
        if (config.uniform != null || config.top == null) {
            Signal<Float> uniform =
                    config.uniform != null ? config.uniform : Signals.constant(0f);
            return Modified.props(content, Modified.prop(Properties.PADDING, uniform));
        }
        return Modified.props(content,
                Modified.prop(Properties.PADDING_TOP, config.top),
                Modified.prop(Properties.PADDING_RIGHT, config.right),
                Modified.prop(Properties.PADDING_BOTTOM, config.bottom),
                Modified.prop(Properties.PADDING_LEFT, config.left));
    }
}
