package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

/** A rectangle shape (a {@code SHAPE} node with {@code SHAPE_KIND = Rectangle}). */
public final class Rectangle implements View, Configurable<Rectangle.Config> {

    /** {@link Rectangle} has no values. */
    public static final class Config implements View.Config {
    }

    private final Config config = new Config();

    private Rectangle() {
    }

    /** A rectangle shape. */
    public static ViewBuilder<Rectangle, Config> make() {
        return new ViewBuilder<>(new Rectangle());
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.SHAPE);
        node.properties.put(Properties.SHAPE_KIND, (float) ShapeKind.RECTANGLE.wire());
        return node;
    }
}
