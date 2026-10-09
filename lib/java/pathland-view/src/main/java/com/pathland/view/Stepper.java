package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

/**
 * A discrete increment/decrement control ({@code Stepper}). The value binds to
 * a {@link WritableSignal<Float>}; presses flow back as {@code VALUE_CHANGED}.
 */
public final class Stepper implements View, Configurable<Stepper.Config> {

    /** {@link Stepper} values. */
    public static final class Config implements View.Config {

        private WritableSignal<Float> binding;
        private Float min;
        private Float max;
        private Float step;

        /** Bind the value (two-way). */
        public Config value(WritableSignal<Float> binding) {
            this.binding = binding;
            return this;
        }

        /** Set the range bounds. */
        public Config in(float min, float max) {
            this.min = min;
            this.max = max;
            return this;
        }

        /** Set the increment. */
        public Config step(float step) {
            this.step = step;
            return this;
        }
    }

    private final Config config;

    private Stepper(Config config) {
        this.config = config;
    }

    /** A discrete increment/decrement control bound to {@code binding}. */
    public static Stepper of(WritableSignal<Float> binding, float min, float max, float step) {
        return new Stepper(new Config().value(binding).in(min, max).step(step));
    }

    /** Configure the stepper's values. */
    public static ViewBuilder<Stepper, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Stepper(config));
    }

    /** Apply modifiers to a bare stepper. */
    public static ViewBuilder<Stepper, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Stepper(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.STEPPER);
        if (config.binding != null) {
            node.properties.put(Properties.VALUE, config.binding.get());
            node.propertyBindings.put(Properties.VALUE, config.binding);
            node.valueInput = config.binding::set;
        }
        if (config.min != null) {
            node.properties.put(Properties.MIN_VALUE, config.min);
        }
        if (config.max != null) {
            node.properties.put(Properties.MAX_VALUE, config.max);
        }
        if (config.step != null) {
            node.properties.put(Properties.STEP_VALUE, config.step);
        }
        return node;
    }
}
