package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

import java.util.List;
import java.util.function.Consumer;

/**
 * A contextual action trigger and popover container ({@code Menu}). The first
 * child is the custom trigger; the rest are action items ({@code Button}/{@code Toggle}/
 * nested {@code Menu}). When a {@code selection} binding is provided, choosing an item
 * reports its index as {@code VALUE_CHANGED}.
 */
public final class Menu implements View, Configurable<Menu.Config>, ChildrenView {

    /** {@link Menu} values. */
    public static final class Config implements View.Config {

        private WritableSignal<Integer> selection;

        /** Report the chosen action item index as {@code VALUE_CHANGED}. */
        public Config selection(WritableSignal<Integer> selection) {
            this.selection = selection;
            return this;
        }
    }

    private final Config config;
    private List<View> children = List.of();

    private Menu(Config config, List<View> children) {
        this.config = config;
        this.children = children;
    }

    /** A menu with a custom trigger and action items. */
    public static Menu of(View trigger, View... actions) {
        List<View> all = new java.util.ArrayList<>(1 + actions.length);
        all.add(trigger);
        all.addAll(List.of(actions));
        return new Menu(new Config(), List.copyOf(all));
    }

    /** A menu reporting the chosen action item index via {@code VALUE_CHANGED}. */
    public static Menu of(View trigger, WritableSignal<Integer> selection, View... actions) {
        List<View> all = new java.util.ArrayList<>(1 + actions.length);
        all.add(trigger);
        all.addAll(List.of(actions));
        return new Menu(new Config().selection(selection), List.copyOf(all));
    }

    /** Configure the menu's values. */
    public static ViewBuilder<Menu, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Menu(config, List.of()));
    }

    /** Supply the trigger (first) and action items. */
    public static ViewBuilder<Menu, Config> children(View... children) {
        return new ViewBuilder<>(new Menu(new Config(), List.of())).children(children);
    }

    /** Apply modifiers to an empty menu. */
    public static ViewBuilder<Menu, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Menu(new Config(), List.of())).modifiers(modifiers);
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
        PathlandNode node = new PathlandNode(Components.MENU);
        for (View child : children) {
            node.children.add(child.render(env));
        }
        if (config.selection != null) {
            node.valueInput = v -> config.selection.set(Math.round(v));
        }
        return node;
    }
}
