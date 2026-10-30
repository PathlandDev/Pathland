package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

/** An axis-aligned separator line (orientation implied by the parent stack axis). */
public final class Divider implements View, Configurable<Divider.Config> {

    /** {@link Divider} has no values. */
    public static final class Config implements View.Config {
    }

    private final Config config = new Config();

    private Divider() {
    }

    /** An axis-aligned separator. */
    public static ViewBuilder<Divider, Config> make() {
        return new ViewBuilder<>(new Divider());
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode renderNode(Environment env) {
        return new PathlandNode(Components.DIVIDER);
    }
}
