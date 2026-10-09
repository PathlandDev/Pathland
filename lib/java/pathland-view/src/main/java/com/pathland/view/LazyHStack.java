package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

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

    /** A virtualized horizontal stack. */
    public static LazyHStack of(View... children) {
        return new LazyHStack(new Config(), List.of(children));
    }

    /** A virtualized horizontal stack with constructor layout properties. */
    public static LazyHStack of(VerticalAlignment alignment, float spacing, View... children) {
        return new LazyHStack(new Config().alignment(alignment).spacing(spacing), List.of(children));
    }

    /** A virtualized horizontal stack with constructor layout properties. */
    public static LazyHStack of(VerticalAlignment alignment, Float spacing, List<View> children) {
        Config config = new Config();
        if (alignment != null) {
            config.alignment(alignment);
        }
        if (spacing != null) {
            config.spacing(spacing);
        }
        return new LazyHStack(config, List.copyOf(children));
    }

    /** Configure the stack's values. */
    public static ViewBuilder<LazyHStack, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new LazyHStack(config, List.of()));
    }

    /** Apply modifiers to an empty stack. */
    public static ViewBuilder<LazyHStack, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new LazyHStack(new Config(), List.of())).modifiers(modifiers);
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
