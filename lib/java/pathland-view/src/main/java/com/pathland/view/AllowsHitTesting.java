package com.pathland.view;

import java.util.function.Consumer;

/**
 * whether the view participates in hit testing.
 */
public final class AllowsHitTesting implements ViewModifier {

    /** {@link AllowsHitTesting} values. */
    public static final class Config {

        private boolean allowed = true;

        /** Set whether hit testing is allowed. */
        public Config allowed(boolean allowed) {
            this.allowed = allowed;
            return this;
        }
    }

    private final boolean on;

    private AllowsHitTesting(boolean on) {
        this.on = on;
    }

    /** Hit-testing on ({@code on} = participates). */
    public static AllowsHitTesting of(boolean on) {
        return new AllowsHitTesting(on);
    }

    /** Configure hit testing. */
    public static AllowsHitTesting with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new AllowsHitTesting(config.allowed);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.ALLOWS_HIT_TESTING, on ? 1 : 0));
    }
}
