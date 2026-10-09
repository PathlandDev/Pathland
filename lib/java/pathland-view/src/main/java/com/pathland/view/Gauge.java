package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.function.Consumer;

/** A read-only value shown against a scale ({@code Gauge}). */
public final class Gauge implements View, Configurable<Gauge.Config> {

    /** {@link Gauge} values. */
    public static final class Config implements View.Config {

        private Float value;
        private Float min;
        private Float max;

        /** Set the displayed value. */
        public Config value(float value) {
            this.value = value;
            return this;
        }

        /** Set the scale minimum. */
        public Config minValue(float min) {
            this.min = min;
            return this;
        }

        /** Set the scale maximum. */
        public Config maxValue(float max) {
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
            node.properties.put(Properties.VALUE, config.value);
        }
        if (config.min != null) {
            node.properties.put(Properties.MIN_VALUE, config.min);
        }
        if (config.max != null) {
            node.properties.put(Properties.MAX_VALUE, config.max);
        }
        return node;
    }
}
