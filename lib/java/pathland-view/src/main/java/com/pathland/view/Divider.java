package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/** An axis-aligned separator line (orientation implied by the parent stack axis). */
public final class Divider implements View, Configurable<Divider.Config> {

    /** {@link Divider} has no values. */
    public static final class Config implements View.Config {
    }

    private final Config config = new Config();

    private Divider() {
    }

    /** An axis-aligned separator. */
    public static Divider of() {
        return new Divider();
    }

    /** Apply modifiers to a divider. */
    public static ViewBuilder<Divider, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new Divider()).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public PathlandNode render(Environment env) {
        return new PathlandNode(Components.DIVIDER);
    }
}
