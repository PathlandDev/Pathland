package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

import java.util.List;
import java.util.function.Consumer;

/** A virtualized vertical stack (windowed realization of children). */
public final class LazyVStack implements View, Configurable<LazyVStack.Config>, ChildrenView {

    /** {@link LazyVStack} values. */
    public static final class Config implements View.Config {

        private Signal<HorizontalAlignment> alignment;
        private Signal<Float> spacing;

        /** Set the cross-axis alignment. */
        public Config alignment(HorizontalAlignment alignment) {
            this.alignment = Signals.constant(alignment);
            return this;
        }

        /** Bind the cross-axis alignment to a signal. */
        public Config alignment(Signal<HorizontalAlignment> alignment) {
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

    private LazyVStack(Config config, List<View> children) {
        this.config = config;
        this.children = children;
    }

    /** Configure the stack's values. */
/** An empty LazyVStack (no values). */
    public static ViewBuilder<LazyVStack, Config> make() {
        return new ViewBuilder<>(new LazyVStack(new Config(), List.of()));
    }

    public static ViewBuilder<LazyVStack, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new LazyVStack(config, List.of()));
    }

    /** Supply the stack's children. */
    public static ViewBuilder<LazyVStack, Config> children(View... children) {
        return new ViewBuilder<>(new LazyVStack(new Config(), List.of())).children(children);
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
        PathlandNode node = new PathlandNode(Components.LAZY_VSTACK);
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
