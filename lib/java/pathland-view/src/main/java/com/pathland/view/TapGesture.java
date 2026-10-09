package com.pathland.view;

import java.util.function.Consumer;

/**
 * attaches a tap gesture ({@code onTapGesture}).
 */
public final class TapGesture implements ViewModifier {

    /** {@link TapGesture} values. */
    public static final class Config {

        private Runnable action;

        /** Set the tap action. */
        public Config action(Runnable action) {
            this.action = action;
            return this;
        }
    }

    private final Runnable action;

    private TapGesture(Runnable action) {
        this.action = action;
    }

    public static TapGesture of(Runnable action) {
        return new TapGesture(action);
    }

    /** Configure the tap gesture. */
    public static TapGesture with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new TapGesture(config.action);
    }

    @Override
    public View body(View content) {
        return new TapGestureView(content, action);
    }
}
