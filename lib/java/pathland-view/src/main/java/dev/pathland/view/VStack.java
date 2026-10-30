package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.Signal;
import dev.pathland.view.signal.Signals;

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
        private Length spacingToken;

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
            this.spacingToken = null;
            return this;
        }

        /** Bind the main-axis gap to a signal. */
        public Config spacing(Signal<Float> spacing) {
            this.spacing = spacing;
            this.spacingToken = null;
            return this;
        }

        /** Set the main-axis gap from a design token (e.g. {@code space.2}). */
        public Config spacing(Length spacing) {
            this.spacingToken = spacing;
            this.spacing = null;
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
    public PathlandNode renderNode(Environment env) {
        PathlandNode node = new PathlandNode(Components.VSTACK);
        if (config.alignment != null) {
            node.property(Properties.ALIGNMENT, config.alignment);
        }
        if (config.spacingToken != null) {
            node.properties.put(Properties.SPACING, config.spacingToken);
        } else if (config.spacing != null) {
            node.property(Properties.SPACING, config.spacing);
        }
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}
