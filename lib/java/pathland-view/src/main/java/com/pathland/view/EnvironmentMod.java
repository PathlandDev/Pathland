package com.pathland.view;

/**
 * Scopes an {@link EnvironmentKey} → value binding down a subtree (SwiftUI
 * {@code .environment(...)}): the value is active only while the wrapped subtree
 * renders, and an outer binding is restored afterward. Applied via
 * {@code View#environment(EnvironmentKey, Object)}.
 */
public final class EnvironmentMod implements ViewModifier {

    private final EnvironmentKey<Object> key;
    private final Object value;

    @SuppressWarnings("unchecked")
    static <T> EnvironmentMod of(EnvironmentKey<T> key, Object value) {
        return new EnvironmentMod((EnvironmentKey<Object>) key, value);
    }

    private EnvironmentMod(EnvironmentKey<Object> key, Object value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public View body(View content) {
        return new EnvironmentView(content, key, value);
    }
}