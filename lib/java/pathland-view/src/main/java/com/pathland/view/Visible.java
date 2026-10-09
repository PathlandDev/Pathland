package com.pathland.view;

import java.util.function.Consumer;

/**
 * shows/hides the view ({@code VISIBLE}).
 */
public final class Visible implements ViewModifier {

    /** {@link Visible} values. */
    public static final class Config {

        private boolean visible = true;

        /** Set visibility. */
        public Config visible(boolean visible) {
            this.visible = visible;
            return this;
        }
    }

    private final boolean visible;

    private Visible(boolean visible) {
        this.visible = visible;
    }

    /** Show ({@code visible} = shown). */
    public static Visible of(boolean visible) {
        return new Visible(visible);
    }

    /** Configure the visibility. */
    public static Visible with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Visible(config.visible);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.VISIBLE, visible ? 1 : 0));
    }
}
