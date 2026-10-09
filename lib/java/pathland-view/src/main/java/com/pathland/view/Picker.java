package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

import java.util.List;
import java.util.function.Consumer;

/**
 * A selection control ({@code Picker}). The options are the child nodes, in
 * display order; {@code SELECTION} is the selected child index. Changes flow back as
 * {@code VALUE_CHANGED} (the new index) into the binding.
 */
public final class Picker implements View, Configurable<Picker.Config>, ChildrenView {

    /** {@link Picker} values. */
    public static final class Config implements View.Config {

        private PickerStyle style = PickerStyle.MENU;
        private WritableSignal<Integer> selection;

        /** Set the presentation style. */
        public Config style(PickerStyle style) {
            this.style = style;
            return this;
        }

        /** Bind the selected child index (two-way). */
        public Config selection(WritableSignal<Integer> selection) {
            this.selection = selection;
            return this;
        }
    }

    private final Config config;
    private List<View> children = List.of();

    private Picker(Config config, List<View> children) {
        this.config = config;
        this.children = children;
    }

    /** A selection control; the options are the children. */
    public static Picker of(PickerStyle style, WritableSignal<Integer> selection, View... options) {
        return new Picker(new Config().style(style).selection(selection), List.of(options));
    }

    /** Configure the picker's values. */
    public static ViewBuilder<Picker, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Picker(config, List.of()));
    }

    /** Supply the picker's options. */
    public static ViewBuilder<Picker, Config> children(View... options) {
        return new ViewBuilder<>(new Picker(new Config(), List.of())).children(options);
    }

    /** Apply modifiers to a bare picker. */
    public static ViewBuilder<Picker, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Picker(new Config(), List.of())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public void setChildren(List<View> children) {
        this.children = List.copyOf(children);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.PICKER);
        node.properties.put(Properties.PICKER_STYLE, (float) config.style.wire());
        if (config.selection != null) {
            node.properties.put(Properties.SELECTION, config.selection.get());
            node.propertyBindings.put(Properties.SELECTION, config.selection);
            node.valueInput = v -> config.selection.set(Math.round(v));
        }
        for (View option : children) {
            node.children.add(option.render(env));
        }
        return node;
    }
}
