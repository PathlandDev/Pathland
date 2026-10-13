package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

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
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.SHAPE);
        node.properties.put(Properties.SHAPE_KIND, (float) ShapeKind.ELLIPSE.wire());
        return node;
    }
}
