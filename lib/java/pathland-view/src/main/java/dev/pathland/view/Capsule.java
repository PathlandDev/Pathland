package dev.pathland.view;

import dev.pathland.view.core.protocol.Components;
import dev.pathland.view.core.Configurable;
import dev.pathland.view.core.Environment;
import dev.pathland.view.core.protocol.Properties;
import dev.pathland.view.core.ShapeKind;
import dev.pathland.view.core.View;
import dev.pathland.view.core.ViewBuilder;

import dev.pathland.view.engine.PathlandNode;

/** A capsule shape (a {@code SHAPE} node with {@code SHAPE_KIND = Capsule}). */
public final class Capsule implements View, Configurable<Capsule.Config> {

    /** {@link Capsule} has no values. */
    public static final class Config implements View.Config {
    }

    private final Config config = new Config();

    private Capsule() {
    }

    /** A capsule. */
    public static ViewBuilder<Capsule, Config> make() {
        return new ViewBuilder<>(new Capsule());
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode renderNode(Environment env) {
        PathlandNode node = new PathlandNode(Components.SHAPE);
        node.properties.put(Properties.SHAPE_KIND, (float) ShapeKind.CAPSULE.wire());
        return node;
    }
}
