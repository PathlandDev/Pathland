package dev.pathland.view;

import dev.pathland.view.emit.PathlandNode;

/** Flexible space that expands along a stack's main axis. */
public final class Spacer implements View, Configurable<Spacer.Config> {

    /** {@link Spacer} has no values. */
    public static final class Config implements View.Config {
    }

    private final Config config = new Config();

    private Spacer() {
    }

    /** A flexible expanding spacer. */
    public static ViewBuilder<Spacer, Config> make() {
        return new ViewBuilder<>(new Spacer());
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode renderNode(Environment env) {
        return new PathlandNode(Components.SPACER);
    }
}
