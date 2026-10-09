package com.pathland.view;

import java.util.function.Consumer;

/**
 * disables/enables the view ({@code ENABLED}: 1 = interactive).
 */
public final class Disabled implements ViewModifier {

    /** {@link Disabled} values. */
    public static final class Config {

        private boolean disabled = true;

        /** Set whether the view is disabled. */
        public Config disabled(boolean disabled) {
            this.disabled = disabled;
            return this;
        }
    }

    private final boolean disabled;

    private Disabled(boolean disabled) {
        this.disabled = disabled;
    }

    /** Disable/enable ({@code disabled} = disabled). */
    public static Disabled of(boolean disabled) {
        return new Disabled(disabled);
    }

    /** Configure the disabled state. */
    public static Disabled with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new Disabled(config.disabled);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.ENABLED, disabled ? 0 : 1));
    }
}
