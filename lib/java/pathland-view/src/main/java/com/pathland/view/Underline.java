package com.pathland.view;

import java.util.function.Consumer;

/**
 * underlines the text ({@code UNDERLINE}).
 */
public final class Underline implements ViewModifier {

    /** {@link Underline} values. */
    public static final class Config {

        private boolean enabled = true;

        /** Set whether the text is underlined. */
        public Config enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
    }

    private final boolean on;

    private Underline(boolean on) {
        this.on = on;
    }

    /** Underline. */
    public static Underline of() {
        return new Underline(true);
    }

    /** Underline ({@code on} = underline). */
    public static Underline of(boolean on) {
        return new Underline(on);
    }

    /** Underline (enabled). */
    public static Underline with() {
        return new Underline(true);
    }

    /** Configure the underline. */
    public static Underline with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Underline(config.enabled);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.UNDERLINE, on ? 1 : 0));
    }
}
