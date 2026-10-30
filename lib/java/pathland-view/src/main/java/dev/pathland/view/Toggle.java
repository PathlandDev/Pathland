package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

/**
 * A boolean switch, checkbox, or toggle button ({@code Toggle}). The visual
 * variant is the {@code TOGGLE_STYLE} token; the checked state binds to a
 * {@link WritableSignal<Boolean>} (two-way).
 */
public final class Toggle implements View, Configurable<Toggle.Config> {

    /** {@link Toggle} values. */
    public static final class Config implements View.Config {

        private Signal<ToggleStyle> style;
        private WritableSignal<Boolean> binding;
        private Signal<String> label;

        /** Set a static visual variant ({@code TOGGLE_STYLE}). */
        public Config style(ToggleStyle style) {
            this.style = Signals.constant(style);
            return this;
        }

        /** Bind the visual variant ({@code TOGGLE_STYLE}) to a signal. */
        public Config style(Signal<ToggleStyle> style) {
            this.style = style;
            return this;
        }

        /** Bind the checked state (two-way). */
        public Config isOn(WritableSignal<Boolean> binding) {
            this.binding = binding;
            return this;
        }

        /** Set a static label. */
        public Config label(String label) {
            this.label = Signals.constant(label);
            return this;
        }

        /** Bind the label to a signal. */
        public Config label(Signal<String> label) {
            this.label = label;
            return this;
        }
    }

    private final Config config;

    private Toggle(Config config) {
        this.config = config;
    }

    /** Configure the toggle's values. */
/** An empty Toggle (no values). */
    public static ViewBuilder<Toggle, Config> make() {
        return new ViewBuilder<>(new Toggle(new Config()));
    }

    public static ViewBuilder<Toggle, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Toggle(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode renderNode(Environment env) {
        PathlandNode node = new PathlandNode(Components.TOGGLE);
        node.property(Properties.TOGGLE_STYLE,
                config.style != null ? config.style : Signals.constant(ToggleStyle.SWITCH));
        if (config.binding != null) {
            node.properties.put(Properties.SELECTED, dev.pathland.view.emit.ReactiveValues.initial(Properties.SELECTED, config.binding));
            node.propertyBindings.put(Properties.SELECTED, config.binding);
            node.valueInput = v -> config.binding.set(v > 0f);
        }
        if (config.label != null) {
            node.textBinding = config.label;
            node.text = dev.pathland.view.signal.Signals.untracked(config.label::get);
        }
        return node;
    }
}
