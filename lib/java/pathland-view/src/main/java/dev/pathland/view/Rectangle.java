package dev.pathland.view;

import dev.pathland.view.core.protocol.Components;
import dev.pathland.view.core.Configurable;
import dev.pathland.view.core.Environment;
import dev.pathland.view.core.protocol.Properties;
import dev.pathland.view.core.ShapeKind;
import dev.pathland.view.core.View;
import dev.pathland.view.core.ViewBuilder;

import dev.pathland.view.engine.PathlandNode;

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
    public PathlandNode renderNode(Environment env) {
        PathlandNode node = new PathlandNode(Components.SHAPE);
        node.properties.put(Properties.SHAPE_KIND, (float) ShapeKind.RECTANGLE.wire());
        return node;
    }
}
