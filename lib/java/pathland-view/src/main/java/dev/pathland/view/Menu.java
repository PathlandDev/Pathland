package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.WritableSignal;

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

    /** Configure the menu's values. */
/** An empty Menu (no values). */
    public static ViewBuilder<Menu, Config> make() {
        return new ViewBuilder<>(new Menu(new Config(), List.of()));
    }

    public static ViewBuilder<Menu, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Menu(config, List.of()));
    }

    /** Supply the trigger (first) and action items. */
    public static ViewBuilder<Menu, Config> children(View... children) {
        return new ViewBuilder<>(new Menu(new Config(), List.of())).children(children);
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
    public PathlandNode renderNode(Environment env) {
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
