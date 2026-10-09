package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/** A circle shape (a {@code SHAPE} node with {@code SHAPE_KIND = Circle}). */
public final class Circle implements View, Configurable<Circle.Config> {

    /** {@link Circle} has no values. */
    public static final class Config implements View.Config {
    }

    private final Config config = new Config();

    private Circle() {
    }

    /** A circle. */
    public static Circle of() {
        return new Circle();
    }

    /** Apply modifiers to a circle. */
    public static ViewBuilder<Circle, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Circle()).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.SHAPE);
        node.properties.put(Properties.SHAPE_KIND, (float) ShapeKind.CIRCLE.wire());
        return node;
    }
}
