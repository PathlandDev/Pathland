package dev.pathland.view;

import dev.pathland.view.core.protocol.Components;
import dev.pathland.view.core.Configurable;
import dev.pathland.view.core.Environment;
import dev.pathland.view.core.protocol.Properties;
import dev.pathland.view.core.ShapeKind;
import dev.pathland.view.core.View;
import dev.pathland.view.core.ViewBuilder;

import dev.pathland.view.engine.PathlandNode;

/** An ellipse shape (a {@code SHAPE} node with {@code SHAPE_KIND = Ellipse}). */
public final class Ellipse implements View, Configurable<Ellipse.Config> {

    /** {@link Ellipse} has no values. */
    public static final class Config implements View.Config {
    }

    private final Config config = new Config();

    private Ellipse() {
    }

    /** An ellipse. */
    public static ViewBuilder<Ellipse, Config> make() {
        return new ViewBuilder<>(new Ellipse());
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode renderNode(Environment env) {
        PathlandNode node = new PathlandNode(Components.SHAPE);
        node.properties.put(Properties.SHAPE_KIND, (float) ShapeKind.ELLIPSE.wire());
        return node;
    }
}
