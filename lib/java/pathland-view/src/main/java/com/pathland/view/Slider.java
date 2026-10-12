package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

/**
 * A continuous or stepped numeric range control ({@code Slider}). The value
 * binds to a {@link WritableSignal<Float>}; user drags flow back as {@code VALUE_CHANGED},
 * and an optional {@code onEditingChanged} receives {@code EDITING_CHANGED} boundaries
 * (spec/EVENTS.md in-flight interaction) — e.g. a seek bar that commits on release.
 */
public final class Slider implements View, Configurable<Slider.Config> {

    /** {@link Slider} values. */
    public static final class Config implements View.Config {

        private WritableSignal<Float> binding;
        private Float min;
        private Float max;
        private Consumer<Boolean> onEditingChanged;

        /** Bind the current value (two-way). */
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

        /** Report {@code EDITING_CHANGED} drag start/end boundaries. */
        public Config onEditingChanged(Consumer<Boolean> onEditingChanged) {
            this.onEditingChanged = onEditingChanged;
            return this;
        }
    }

    private final Config config;

    private Slider(Config config) {
        this.config = config;
    }

    /** Configure the slider's values. */
/** An empty Slider (no values). */
    public static ViewBuilder<Slider, Config> make() {
        return new ViewBuilder<>(new Slider(new Config()));
    }

    public static ViewBuilder<Slider, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Slider(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.SLIDER);
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
        if (config.onEditingChanged != null) {
            node.properties.computeIfAbsent(Properties.EVENT_LISTENERS, k -> 0);
            node.properties.put(Properties.EVENT_LISTENERS,
                    (Integer) node.properties.get(Properties.EVENT_LISTENERS) | Commands.Listeners.EDITING);
            node.editingInput = config.onEditingChanged;
        }
        return node;
    }
}
