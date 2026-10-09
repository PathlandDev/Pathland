package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * the two-way binding id ({@code BINDING_ID}).
 */
public final class BindingId implements ViewModifier {

    /** {@link BindingId} values. */
    public static final class Config {

        private Signal<Integer> value;

        /** Set a static binding id. */
        public Config value(int value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the binding id to a signal. */
        public Config value(Signal<Integer> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private BindingId(Config config) {
        this.config = config;
    }

    public static BindingId of(int value) {
        return new BindingId(new Config().value(value));
    }

    /** Configure the binding id. */
    public static BindingId with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new BindingId(config);
    }

    @Override
    public View body(View content) {
        Signal<Integer> value = config.value != null ? config.value : Signals.constant(0);
        return Modified.props(content, Modified.prop(Properties.BINDING_ID, value));
    }
}
