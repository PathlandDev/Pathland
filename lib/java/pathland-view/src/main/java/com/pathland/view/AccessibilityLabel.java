package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.function.Consumer;

/**
 * the accessibility label (a {@code STRING} property); a single
 * {@link Signal<String>} — a static label is sugar for a constant signal.
 */
public final class AccessibilityLabel implements ViewModifier {

    /** {@link AccessibilityLabel} values. */
    public static final class Config {

        private Signal<String> signal;

        /** Set a static label. */
        public Config text(String value) {
            this.signal = Signals.constant(value);
            return this;
        }

        /** Bind the label to a signal. */
        public Config text(Signal<String> signal) {
            this.signal = signal;
            return this;
        }
    }

    private final Signal<String> signal;

    private AccessibilityLabel(Signal<String> signal) {
        this.signal = signal;
    }

    public static AccessibilityLabel of(String value) {
        return new AccessibilityLabel(Signals.constant(value));
    }

    /** A reactive label: a change re-emits only this node's {@code LABEL}. */
    public static AccessibilityLabel of(Signal<String> signal) {
        return new AccessibilityLabel(signal);
    }

    /** Configure the accessibility label. */
    public static AccessibilityLabel with(Consumer<Config> configure) {
        Config config = new Config();
        configure.accept(config);
        return new AccessibilityLabel(config.signal);
    }

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.LABEL, signal));
    }
}
