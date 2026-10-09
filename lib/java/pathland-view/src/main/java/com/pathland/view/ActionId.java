package com.pathland.view;

import java.util.function.Consumer;

/**
 * the action callback id ({@code ACTION_ID}).
 */
public final class ActionId implements ViewModifier {

    /** {@link ActionId} values. */
    public static final class Config {

        private int value;

        /** Set the action id. */
        public Config value(int value) {
            this.value = value;
            return this;
        }
    }

    private final int value;

    private ActionId(int value) {
        this.value = value;
    }

    public static ActionId of(int value) {
        return new ActionId(value);
    }

    /** Configure the action id. */
    public static ActionId with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ActionId(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.ACTION_ID, value));
    }
}
