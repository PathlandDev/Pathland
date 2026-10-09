package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

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

    /** An overlapping stack. */
    public static ZStack of(View... children) {
        return new ZStack(new Config(), List.of(children));
    }

    /** An overlapping stack. */
    public static ZStack of(List<View> children) {
        return new ZStack(new Config(), List.copyOf(children));
    }

    /** An overlapping stack with a 2D alignment (positions children on both axes). */
    public static ZStack of(Alignment alignment, View... children) {
        return new ZStack(new Config().alignment(alignment), List.of(children));
    }

    /** An overlapping stack with a 2D alignment (positions children on both axes). */
    public static ZStack of(Alignment alignment, List<View> children) {
        return new ZStack(new Config().alignment(alignment), List.copyOf(children));
    }

    /** Configure the stack's values. */
    public static ViewBuilder<ZStack, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new ZStack(config, List.of()));
    }

    /** Apply modifiers to an empty stack. */
    public static ViewBuilder<ZStack, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new ZStack(new Config(), List.of())).modifiers(modifiers);
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
    public PathlandNode render(Environment env) {
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
