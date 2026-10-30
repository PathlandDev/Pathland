package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

import java.util.List;
import java.util.function.Consumer;

/**
 * An overlapping stack (children rendered on top of each other). Rendered as a
 * plain container; the native renderer decides the exact stacking.
 */
public final class ZStack implements View, Configurable<ZStack.Config>, ChildrenView {

    /** {@link ZStack} values. */
    public static final class Config implements View.Config {

        private Signal<Alignment> alignment;

        /** Set the 2D alignment (positions children on both axes). */
        public Config alignment(Alignment alignment) {
            this.alignment = Signals.constant(alignment);
            return this;
        }

        /** Bind the 2D alignment to a signal. */
        public Config alignment(Signal<Alignment> alignment) {
            this.alignment = alignment;
            return this;
        }
    }

    private final Config config;
    private List<View> children = List.of();

    private ZStack(Config config, List<View> children) {
        this.config = config;
        this.children = children;
    }

    /** Configure the stack's values. */
/** An empty ZStack (no values). */
    public static ViewBuilder<ZStack, Config> make() {
        return new ViewBuilder<>(new ZStack(new Config(), List.of()));
    }

    public static ViewBuilder<ZStack, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new ZStack(config, List.of()));
    }

    /** Supply the stack's children. */
    public static ViewBuilder<ZStack, Config> children(View... children) {
        return new ViewBuilder<>(new ZStack(new Config(), List.of())).children(children);
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
        PathlandNode node = new PathlandNode(Components.ZSTACK);
        if (config.alignment != null) {
            node.property(Properties.ALIGNMENT, config.alignment);
        }
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}
