package com.pathland.view;

import java.util.function.Consumer;

/**
 * the accessibility state ({@code STATE} semantic enum code).
 */
public final class AccessibilityState implements ViewModifier {

    /** {@link AccessibilityState} values. */
    public static final class Config {

        private Integer state;

        /** Set the semantic state code. */
        public Config state(int state) {
            this.state = state;
            return this;
        }
    }

    private final float value;

    private AccessibilityState(float value) {
        this.value = value;
    }

    public static AccessibilityState of(int value) {
        return new AccessibilityState((float) value);
    }

    /** Configure the accessibility state. */
    public static AccessibilityState with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new AccessibilityState(config.state != null ? (float) config.state : 0f);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.STATE, value));
    }
}
