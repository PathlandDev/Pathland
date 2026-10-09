package com.pathland.view;

import java.util.function.Consumer;

/**
 * the two-way binding id ({@code BINDING_ID}).
 */
public final class BindingId implements ViewModifier {

    /** {@link BindingId} values. */
    public static final class Config {

        private int value;

        /** Set the binding id. */
        public Config value(int value) {
            this.value = value;
            return this;
        }
    }

    private final int value;

    private BindingId(int value) {
        this.value = value;
    }

    public static BindingId of(int value) {
        return new BindingId(value);
    }

    /** Configure the binding id. */
    public static BindingId with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new BindingId(config.value);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.BINDING_ID, value));
    }
}
