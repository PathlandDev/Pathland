package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

import java.util.function.Consumer;

/**
 * A boolean switch, checkbox, or toggle button ({@code Toggle}). The visual
 * variant is the {@code TOGGLE_STYLE} token ({@code Switch}/{@code Checkbox}/{@code Button}).
 * The checked state binds to a {@link WritableSignal<Boolean>} (its current value is the
 * initial state); user changes flow back as {@code VALUE_CHANGED} (0/1) and are written
 * into the binding.
 */
public final class Toggle implements View, Configurable<Toggle.Config> {

    /** {@link Toggle} values. */
    public static final class Config implements View.Config {

        private ToggleStyle style = ToggleStyle.SWITCH;
        private WritableSignal<Boolean> binding;
        private String label;

        /** Set the visual variant ({@code TOGGLE_STYLE}). */
        public Config style(ToggleStyle style) {
            this.style = style;
            return this;
        }

        /** Bind the checked state (two-way). */
        public Config isOn(WritableSignal<Boolean> binding) {
            this.binding = binding;
            return this;
        }

        /** Set the label. */
        public Config label(String label) {
            this.label = label;
            return this;
        }
    }

    private final Config config;

    private Toggle(Config config) {
        this.config = config;
    }

    /** A toggle with a style token, a binding, and a label. */
    public static Toggle of(ToggleStyle style, WritableSignal<Boolean> binding, String label) {
        return new Toggle(new Config().style(style).isOn(binding).label(label));
    }

    /** A {@code Switch}-style toggle with no label. */
    public static Toggle of(WritableSignal<Boolean> binding) {
        return new Toggle(new Config().isOn(binding));
    }

    /** A {@code Switch}-style toggle with a label ({@code Toggle("label", isOn:)}). */
    public static Toggle of(String label, WritableSignal<Boolean> binding) {
        return new Toggle(new Config().isOn(binding).label(label));
    }

    /** Configure the toggle's values. */
    public static ViewBuilder<Toggle, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Toggle(config));
    }

    /** Apply modifiers to a bare toggle. */
    public static ViewBuilder<Toggle, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Toggle(new Config())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.TOGGLE);
        node.properties.put(Properties.TOGGLE_STYLE, (float) config.style.wire());
        if (config.binding != null) {
            node.properties.put(Properties.SELECTED, config.binding.get() ? 1 : 0);
            node.propertyBindings.put(Properties.SELECTED, config.binding);
            node.valueInput = v -> config.binding.set(v > 0f);
        }
        if (config.label != null) {
            node.text = config.label;
        }
        return node;
    }
}
