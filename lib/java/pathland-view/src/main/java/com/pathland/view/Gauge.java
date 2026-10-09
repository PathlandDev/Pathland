package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/** A read-only value shown against a scale ({@code Gauge}). */
public final class Gauge implements View, Configurable<Gauge.Config> {

    /** {@link Gauge} values. */
    public static final class Config implements View.Config {

        private Signal<Float> value;
        private Signal<Float> min;
        private Signal<Float> max;

        /** Set a static displayed value. */
        public Config value(float value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the displayed value to a signal. */
        public Config value(Signal<Float> value) {
            this.value = value;
            return this;
        }

        /** Set a static scale minimum. */
        public Config minValue(float min) {
            this.min = Signals.constant(min);
            return this;
        }

        /** Bind the scale minimum to a signal. */
        public Config minValue(Signal<Float> min) {
            this.min = min;
            return this;
        }

        /** Set a static scale maximum. */
        public Config maxValue(float max) {
            this.max = Signals.constant(max);
            return this;
        }

        /** Bind the scale maximum to a signal. */
        public Config maxValue(Signal<Float> max) {
            this.max = max;
            return this;
        }
    }

    private final Config config;

    private Gauge(Config config) {
        this.config = config;
    }

    /** A read-only range meter. */
    public static Gauge of(float value, float min, float max) {
        return new Gauge(new Config().value(value).minValue(min).maxValue(max));
    }

    /** Configure the gauge's values. */
    public static ViewBuilder<Gauge, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Gauge(config));
    }

    /** Apply modifiers to a bare gauge. */
    public static ViewBuilder<Gauge, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Gauge(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.GAUGE);
        if (config.value != null) {
            node.property(Properties.VALUE, config.value);
        }
        if (config.min != null) {
            node.property(Properties.MIN_VALUE, config.min);
        }
        if (config.max != null) {
            node.property(Properties.MAX_VALUE, config.max);
        }
        return node;
    }
}
