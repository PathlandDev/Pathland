package com.pathland.view;

/**
 * A typed key for a scoped environment value ({@code EnvironmentValues}
 * key style). Identity-based: two keys are equal only if they are the same instance,
 * so a {@code static final EnvironmentKey<T>} per value is the idiom.
 *
 * <pre>{@code
 * public static final EnvironmentKey<Router> ROUTER = EnvironmentKey.of("router");
 * }</pre>
 */
public final class EnvironmentKey<T> {

    private final String name;

    private EnvironmentKey(String name) {
        this.name = name;
    }

    /** A named environment key (name is for debugging/reading — identity is by instance). */
    public static <T> EnvironmentKey<T> of(String name) {
        return new EnvironmentKey<>(name);
    }

    @Override
    public String toString() {
        return "EnvironmentKey[" + name + "]";
    }
}