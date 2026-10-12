package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.List;
import java.util.function.Consumer;

/**
 * A vertical stack. {@code alignment} and {@code spacing} are **values**
 * (configured with {@code VStack.make(v -> v.spacing(8))}); the content is
 * supplied with {@code .children(...)}.
 */
public final class VStack implements View, Configurable<VStack.Config>, ChildrenView {

    /** {@link VStack} values. */
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

    private VStack(Config config, List<View> children) {
        this.config = config;
        this.children = children;
    }

    /** Configure the stack's values. */
/** An empty VStack (no values). */
    public static ViewBuilder<VStack, Config> make() {
        return new ViewBuilder<>(new VStack(new Config(), List.of()));
    }

    public static ViewBuilder<VStack, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new VStack(config, List.of()));
    }

    /** Supply the stack's children. */
    public static ViewBuilder<VStack, Config> children(View... children) {
        return new ViewBuilder<>(new VStack(new Config(), List.of())).children(children);
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
        PathlandNode node = new PathlandNode(Components.VSTACK);
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
