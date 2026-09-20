package com.pathland.view;

import com.pathland.view.signal.Signal;

/**
 * the accessibility label (a {@code STRING} property).
 */
public final class AccessibilityLabel implements ViewModifier {

    private final String value;
    private final Signal<String> signal;

    private AccessibilityLabel(String value, Signal<String> signal) {
        this.value = value;
        this.signal = signal;
    }

    public static AccessibilityLabel of(String value) {
        return new AccessibilityLabel(value, null);
    }

    /** A reactive label: a change re-emits only this node's {@code LABEL}. */
    public static AccessibilityLabel of(Signal<String> signal) {
        return new AccessibilityLabel(null, signal);
    }

    @Override
    public View body(View content) {
        return signal != null
                ? Modified.props(content, Modified.prop(Properties.LABEL, signal))
                : Modified.props(content, Modified.prop(Properties.LABEL, value));
    }
}
