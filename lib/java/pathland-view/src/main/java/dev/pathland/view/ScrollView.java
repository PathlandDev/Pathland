package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.Signals;

import java.util.List;
import java.util.function.Consumer;

/** A scrollable content container ({@code ScrollView}). */
public final class ScrollView implements View, Configurable<ScrollView.Config>, ChildrenView {

    /** {@link ScrollView} values: the scroll axis (vertical by default). */
    public static final class Config implements View.Config {

        private ScrollAxis axis = ScrollAxis.VERTICAL;

        /** Set the scroll axis (vertical by default). The view is layout-greedy
         *  on this axis; the cross axis hugs its content. */
        public Config axis(ScrollAxis axis) {
            this.axis = axis;
            return this;
        }
    }

    private final Config config;
    private List<View> children = List.of();

    private ScrollView(Config config) {
        this.config = config;
    }

    /** An empty scroll view. */
    public static ViewBuilder<ScrollView, Config> make() {
        return new ViewBuilder<>(new ScrollView(new Config()));
    }

    /** Configure the scroll view (e.g. its axis). */
    public static ViewBuilder<ScrollView, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new ScrollView(config));
    }

    /** Supply the scrollable content. */
    public static ViewBuilder<ScrollView, Config> children(View... children) {
        return new ViewBuilder<>(new ScrollView(new Config())).children(children);
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
        PathlandNode node = new PathlandNode(Components.SCROLLVIEW);
        if (config.axis != ScrollAxis.VERTICAL) {
            node.property(Properties.SCROLL_AXIS, Signals.constant(config.axis));
        }
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}
