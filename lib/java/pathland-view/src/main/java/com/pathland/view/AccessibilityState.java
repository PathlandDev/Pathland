package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * the accessibility state ({@code STATE} semantic enum code).
 */
public final class AccessibilityState implements ViewModifier {

    /** {@link AccessibilityState} values. */
    public static final class Config {

        private Signal<Integer> state;

        /** Set a static semantic state code. */
        public Config state(int state) {
            this.state = Signals.constant(state);
            return this;
        }

        /** Bind the semantic state code to a signal. */
        public Config state(Signal<Integer> state) {
            this.state = state;
            return this;
        }
    }

    private final Config config;

    private AccessibilityState(Config config) {
        this.config = config;
    }

    public static AccessibilityState of(int value) {
        return new AccessibilityState(new Config().state(value));
    }

    /** Configure the accessibility state. */
    public static AccessibilityState with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new AccessibilityState(config);
    }

    @Override
    public View body(View content) {
        Signal<Integer> state = config.state != null ? config.state : Signals.constant(0);
        return Modified.props(content, Modified.prop(Properties.STATE, state));
    }
}
