package com.pathland.view;

import java.util.function.Consumer;

/**
 * declares raw input listeners ({@code EVENT_LISTENERS} bitmask, OR-in).
 */
public final class PointerEvents implements ViewModifier {

    /** {@link PointerEvents} values. */
    public static final class Config {

        private int mask;

        /** Set the listener bitmask. */
        public Config mask(int mask) {
            this.mask = mask;
            return this;
        }
    }

    private final int mask;

    private PointerEvents(int mask) {
        this.mask = mask;
    }

    public static PointerEvents of(int mask) {
        return new PointerEvents(mask);
    }

    /** Configure the listener mask. */
    public static PointerEvents with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new PointerEvents(config.mask);
    }

    @Override
    public View body(View content) {
        return new PointerEventsView(content, mask);
    }
}
