package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * whether the view participates in hit testing.
 */
public final class AllowsHitTesting implements ViewModifier {

    /** {@link AllowsHitTesting} values. */
    public static final class Config {

        private Signal<Boolean> allowed;

        /** Set a static hit-testing state. */
        public Config allowed(boolean allowed) {
            this.allowed = Signals.constant(allowed);
            return this;
        }

        /** Bind the hit-testing state to a signal. */
        public Config allowed(Signal<Boolean> allowed) {
            this.allowed = allowed;
            return this;
        }
    }

    private final Config config;

    private AllowsHitTesting(Config config) {
        this.config = config;
    }

    /** Hit-testing on ({@code on} = participates). */
    public static AllowsHitTesting of(boolean on) {
        return new AllowsHitTesting(new Config().allowed(on));
    }

    /** Configure hit testing. */
    public static AllowsHitTesting with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new AllowsHitTesting(config);
    }

    @Override
    public View body(View content) {
        Signal<Boolean> allowed = config.allowed != null ? config.allowed : Signals.constant(true);
        return Modified.props(content, Modified.prop(Properties.ALLOWS_HIT_TESTING, allowed));
    }
}
