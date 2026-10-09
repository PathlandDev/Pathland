package com.pathland.view;

import java.util.function.Consumer;

/**
 * strikethrough the text ({@code STRIKETHROUGH}).
 */
public final class Strikethrough implements ViewModifier {

    /** {@link Strikethrough} values. */
    public static final class Config {

        private boolean enabled = true;

        /** Set whether the text is struck through. */
        public Config enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
    }

    private final boolean on;

    private Strikethrough(boolean on) {
        this.on = on;
    }

    /** Strikethrough. */
    public static Strikethrough of() {
        return new Strikethrough(true);
    }

    /** Strikethrough ({@code on} = strikethrough). */
    public static Strikethrough of(boolean on) {
        return new Strikethrough(on);
    }

    /** Strikethrough (enabled). */
    public static Strikethrough with() {
        return new Strikethrough(true);
    }

    /** Configure the strikethrough. */
    public static Strikethrough with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Strikethrough(config.enabled);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.STRIKETHROUGH, on ? 1 : 0));
    }
}
