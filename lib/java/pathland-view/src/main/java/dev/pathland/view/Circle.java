package dev.pathland.view;

import dev.pathland.view.core.protocol.Components;
import dev.pathland.view.core.Configurable;
import dev.pathland.view.core.Environment;
import dev.pathland.view.core.protocol.Properties;
import dev.pathland.view.core.ShapeKind;
import dev.pathland.view.core.View;
import dev.pathland.view.core.ViewBuilder;

import dev.pathland.view.engine.PathlandNode;

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
