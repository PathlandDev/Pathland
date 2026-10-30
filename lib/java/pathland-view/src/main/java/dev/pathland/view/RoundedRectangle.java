package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

import java.util.function.Consumer;

/**
 * A rounded rectangle shape (a {@code SHAPE} node with
 * {@code SHAPE_KIND = RoundedRectangle} and {@code BORDER_RADIUS} = corner radius).
 */
public final class RoundedRectangle implements View, Configurable<RoundedRectangle.Config> {

    /** {@link RoundedRectangle} values. */
    public static final class Config implements View.Config {

        private Float cornerRadius;

        /** Set the corner radius. */
        public Config cornerRadius(float cornerRadius) {
            this.cornerRadius = cornerRadius;
            return this;
        }
    }

    private final Config config;

    private RoundedRectangle(Config config) {
        this.config = config;
    }

    /** A rounded rectangle with a corner radius. */
    public static ViewBuilder<RoundedRectangle, Config> make(float cornerRadius) {
        return Configurators.view(Config::new, RoundedRectangle::new, c -> c.cornerRadius(cornerRadius));
    }

    /** Configure the shape's values. */
/** An empty RoundedRectangle (no values). */
    public static ViewBuilder<RoundedRectangle, Config> make() {
        return new ViewBuilder<>(new RoundedRectangle(new Config()));
    }

    public static ViewBuilder<RoundedRectangle, Config> make(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ViewBuilder<>(new RoundedRectangle(config));
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode renderNode(Environment env) {
        PathlandNode node = new PathlandNode(Components.SHAPE);
        node.properties.put(Properties.SHAPE_KIND, (float) ShapeKind.ROUNDED_RECTANGLE.wire());
        if (config.cornerRadius != null) {
            node.properties.put(Properties.BORDER_RADIUS, config.cornerRadius);
        }
        return node;
    }
}
