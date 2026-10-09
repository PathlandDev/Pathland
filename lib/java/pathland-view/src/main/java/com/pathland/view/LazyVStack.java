package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

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

    /** A virtualized vertical stack. */
    public static LazyVStack of(View... children) {
        return new LazyVStack(new Config(), List.of(children));
    }

    /** A virtualized vertical stack with constructor layout properties. */
    public static LazyVStack of(HorizontalAlignment alignment, float spacing, View... children) {
        return new LazyVStack(new Config().alignment(alignment).spacing(spacing), List.of(children));
    }

    /** A virtualized vertical stack with constructor layout properties. */
    public static LazyVStack of(HorizontalAlignment alignment, Float spacing, List<View> children) {
        Config config = new Config();
        if (alignment != null) {
            config.alignment(alignment);
        }
        if (spacing != null) {
            config.spacing(spacing);
        }
        return new LazyVStack(config, List.copyOf(children));
    }

    /** Configure the stack's values. */
    public static ViewBuilder<LazyVStack, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new LazyVStack(config, List.of()));
    }

    /** Apply modifiers to an empty stack. */
    public static ViewBuilder<LazyVStack, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new LazyVStack(new Config(), List.of())).modifiers(modifiers);
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
    public PathlandNode render(Environment env) {
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
