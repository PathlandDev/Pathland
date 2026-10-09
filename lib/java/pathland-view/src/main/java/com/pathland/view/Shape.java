package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

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

    /** A shape of the given kind (e.g. {@code Shape.of(ShapeKind.PATH)}). */
    public static Shape of(ShapeKind kind) {
        return new Shape(new Config().kind(kind));
    }

    /** Configure the shape's values. */
    public static ViewBuilder<Shape, Config> with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new Shape(config));
    }

    /** Apply modifiers to a kind-less shape. */
    public static ViewBuilder<Shape, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Shape(new Config())).modifiers(modifiers);
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
