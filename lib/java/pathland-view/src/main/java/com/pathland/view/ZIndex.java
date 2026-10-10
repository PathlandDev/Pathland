package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * draw-order elevation within a stack.
 */
public final class ZIndex implements ViewModifier {

    /** {@link ZIndex} values. */
    public static final class Config {

        private Signal<Float> value;

        /** Set a static z-index. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the z-index to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private ZIndex(Config config) {
        this.config = config;
    }

    public static ZIndex with(float value) {
        return Configurators.modifier(Config::new, ZIndex::new, c -> c.value(value));
    }

    public static ZIndex with(Signal<Float> value) {
        return Configurators.modifier(Config::new, ZIndex::new, c -> c.value(value));
    }

    /** Configure the z-index. */
    public static ZIndex with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ZIndex(config);
    }

    @Override
    public View body(View content) {
        Signal<Float> value = config.value != null ? config.value : Signals.constant(0f);
        return Modified.props(content, Modified.prop(Properties.Z_INDEX, value));
    }
}
