package com.pathland.view;

import java.util.HashMap;
import java.util.Map;

/**
 * An immutable, hierarchical scope of environment values (SwiftUI
 * {@code EnvironmentValues}): a parent chain where each node adds (or overrides)
 * one {@link EnvironmentKey} → value binding. Lookup returns the nearest binding,
 * so a scoped {@code .environment(key, value)} overrides an outer one for its
 * subtree only. Values are resolved during render; structural slots re-apply the
 * incoming scope when they re-render their destination.
 *
 * <p>Built cheaply: {@link #with} returns a new node sharing the parent, so scoping
 * a large tree is O(1) per binding, not a full copy.
 */
public final class EnvironmentValues {

    private static final EnvironmentValues EMPTY = new EnvironmentValues(null, null, null);

    private final EnvironmentValues parent;
    private final EnvironmentKey<?> key;
    private final Object value;

    private EnvironmentValues(EnvironmentValues parent, EnvironmentKey<?> key, Object value) {
        this.parent = parent;
        this.key = key;
        this.value = value;
    }

    /** The empty scope (no bindings). */
    public static EnvironmentValues empty() {
        return EMPTY;
    }

    /** A scope extending {@code this} with {@code key → value} (nearest wins on read). */
    public <T> EnvironmentValues with(EnvironmentKey<T> key, T value) {
        return new EnvironmentValues(this, key, value);
    }

    /** The value bound to {@code key}, walking the chain; {@code null} when absent. */
    @SuppressWarnings("unchecked")
    public <T> T get(EnvironmentKey<T> key) {
        for (EnvironmentValues node = this; node != null; node = node.parent) {
            if (node.key == key) {
                return (T) node.value;
            }
        }
        return null;
    }

    /**
     * Whether {@code key} is bound anywhere in the chain (nearest-wins). Distinguishes
     * an explicit {@code null} binding from an absent one — used by the lazy
     * {@code Environment.value} signal to know when to (re)capture.
     */
    public boolean contains(EnvironmentKey<?> key) {
        for (EnvironmentValues node = this; node != null; node = node.parent) {
            if (node.key == key) {
                return true;
            }
        }
        return false;
    }

    /** A flattened view of the nearest bindings (for debugging/tests). */
    Map<EnvironmentKey<?>, Object> flattened() {
        Map<EnvironmentKey<?>, Object> out = new HashMap<>();
        for (EnvironmentValues node = this; node != null; node = node.parent) {
            if (node.key != null && !out.containsKey(node.key)) {
                out.put(node.key, node.value);
            }
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EnvironmentValues other)) {
            return false;
        }
        return flattened().equals(other.flattened());
    }

    @Override
    public int hashCode() {
        return flattened().hashCode();
    }
}