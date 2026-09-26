package com.pathland.view;

import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

/**
 * the accessibility label (a {@code STRING} property); a single
 * {@link Signal<String>} — a static label is sugar for a constant signal.
 */
public final class AccessibilityLabel implements ViewModifier {

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

    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.LABEL, signal));
    }
}