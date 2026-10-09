package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/** A capsule shape (a {@code SHAPE} node with {@code SHAPE_KIND = Capsule}). */
public final class Capsule implements View, Configurable<Capsule.Config> {

    /** {@link Capsule} has no values. */
    public static final class Config implements View.Config {
    }

    private final Config config = new Config();

    private Capsule() {
    }

    /** A capsule. */
    public static Capsule of() {
        return new Capsule();
    }

    /** Apply modifiers to a capsule. */
    public static ViewBuilder<Capsule, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Capsule()).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.SHAPE);
        node.properties.put(Properties.SHAPE_KIND, (float) ShapeKind.CAPSULE.wire());
        return node;
    }
}
