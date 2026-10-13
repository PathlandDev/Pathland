package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

import java.util.function.Consumer;

/** A vector geometry by {@link ShapeKind} (a {@code SHAPE} node). */
public final class Shape implements View, Configurable<Shape.Config> {

    /** {@link Shape} values. */
    public static final class Config implements View.Config {

        private ShapeKind kind;

        /** Set the shape kind (e.g. {@link ShapeKind#PATH}). */
        public Config kind(ShapeKind kind) {
            this.kind = kind;
            return this;
        }
    }

    private final Config config;

    private Shape(Config config) {
        this.config = config;
    }

    /** A shape of the given kind (e.g. {@code Shape.make(ShapeKind.PATH)}). */
    public static ViewBuilder<Shape, Config> make(ShapeKind kind) {
        return Configurators.view(Config::new, Shape::new, c -> c.kind(kind));
    }

    /** Configure the shape's values. */
/** An empty Shape (no values). */
    public static ViewBuilder<Shape, Config> make() {
        return new ViewBuilder<>(new Shape(new Config()));
    }

    public static ViewBuilder<Shape, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Shape(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.SHAPE);
        if (config.kind != null) {
            node.properties.put(Properties.SHAPE_KIND, (float) config.kind.wire());
        }
        return node;
    }
}
