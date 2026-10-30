package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

/** A circle shape (a {@code SHAPE} node with {@code SHAPE_KIND = Circle}). */
public final class Circle implements View, Configurable<Circle.Config> {

    /** {@link Circle} has no values. */
    public static final class Config implements View.Config {
    }

    private final Config config = new Config();

    private Circle() {
    }

    /** A circle. */
    public static ViewBuilder<Circle, Config> make() {
        return new ViewBuilder<>(new Circle());
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode renderNode(Environment env) {
        PathlandNode node = new PathlandNode(Components.SHAPE);
        node.properties.put(Properties.SHAPE_KIND, (float) ShapeKind.CIRCLE.wire());
        return node;
    }
}
