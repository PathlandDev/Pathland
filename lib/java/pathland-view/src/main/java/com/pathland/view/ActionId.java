package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * the action callback id ({@code ACTION_ID}).
 */
public final class ActionId implements ViewModifier {

    /** {@link ActionId} values. */
    public static final class Config {

        private Signal<Integer> value;

        /** Set a static action id. */
        public Config value(int value) {
            this.value = Signals.constant(value);
            return this;
        }

        /** Bind the action id to a signal. */
        public Config value(Signal<Integer> value) {
            this.value = value;
            return this;
        }
    }

    private final Config config;

    private ActionId(Config config) {
        this.config = config;
    }

    public static ActionId of(int value) {
        return new ActionId(new Config().value(value));
    }

    /** Configure the action id. */
    public static ActionId with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new ActionId(config);
    }

    @Override
    public View body(View content) {
        Signal<Integer> value = config.value != null ? config.value : Signals.constant(0);
        return Modified.props(content, Modified.prop(Properties.ACTION_ID, value));
    }
}
