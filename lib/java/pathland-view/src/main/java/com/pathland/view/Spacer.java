package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/** Flexible space that expands along a stack's main axis. */
public final class Spacer implements View, Configurable<Spacer.Config> {

    /** {@link Spacer} has no values. */
    public static final class Config implements View.Config {
    }

    private final Config config = new Config();

    private Spacer() {
    }

    /** A flexible expanding spacer. */
    public static Spacer of() {
        return new Spacer();
    }

    /** Apply modifiers to a spacer. */
    public static ViewBuilder<Spacer, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Spacer()).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        return new PathlandNode(Components.SPACER);
    }
}
