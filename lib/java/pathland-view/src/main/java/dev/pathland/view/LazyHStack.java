package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

import java.util.List;
import java.util.function.Consumer;

/** A virtualized horizontal stack (windowed realization of children). */
public final class LazyHStack implements View, Configurable<LazyHStack.Config>, ChildrenView {

    /** {@link LazyHStack} values. */
    public static final class Config implements View.Config {

        private Signal<VerticalAlignment> alignment;
        private Signal<Float> spacing;

        /** Set the cross-axis alignment. */
        public Config alignment(VerticalAlignment alignment) {
            this.alignment = Signals.constant(alignment);
            return this;
        }

        /** Bind the cross-axis alignment to a signal. */
        public Config alignment(Signal<VerticalAlignment> alignment) {
            this.alignment = alignment;
            return this;
        }

        /** Set the main-axis gap. */
        public Config spacing(float spacing) {
            this.spacing = Signals.constant(spacing);
            return this;
        }

        /** Bind the main-axis gap to a signal. */
        public Config spacing(Signal<Float> spacing) {
            this.spacing = spacing;
            return this;
        }
    }

    private final Config config;
    private List<View> children = List.of();

    private LazyHStack(Config config, List<View> children) {
        this.config = config;
        this.children = children;
    }

    /** Configure the stack's values. */
/** An empty LazyHStack (no values). */
    public static ViewBuilder<LazyHStack, Config> make() {
        return new ViewBuilder<>(new LazyHStack(new Config(), List.of()));
    }

    public static ViewBuilder<LazyHStack, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new LazyHStack(config, List.of()));
    }

    /** Supply the stack's children. */
    public static ViewBuilder<LazyHStack, Config> children(View... children) {
        return new ViewBuilder<>(new LazyHStack(new Config(), List.of())).children(children);
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
        PathlandNode node = new PathlandNode(Components.LAZY_HSTACK);
        if (config.alignment != null) {
            node.property(Properties.ALIGNMENT, config.alignment);
        }
        if (config.spacing != null) {
            node.property(Properties.SPACING, config.spacing);
        }
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}
