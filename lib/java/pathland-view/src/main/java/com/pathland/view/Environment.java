package com.pathland.view;

import com.pathland.view.signal.Signal;

/**
 * The rendering environment. Carries implicit, thread-local values that a parent
 * injects down the entire child tree (e.g. an active {@link ButtonStyle}), plus a
 * generic, hierarchical scope of {@link EnvironmentValues} keyed by
 * {@link EnvironmentKey} (SwiftUI {@code EnvironmentValues} style).
 *
 * <p>A {@code .environment(key, value)} modifier binds a value for the subtree it
 * wraps — nearest wins, so an inner binding overrides an outer one. The mount render
 * is synchronous and single-threaded, so a {@link ThreadLocal} is equivalent to
 * ScopedValue here while keeping the library on every LTS from Java 17.
 *
 * <p>Reads always return a signal ({@link #value(EnvironmentKey)}): a value injected
 * as a {@link Signal} comes back as the same instance (bindable for reactive updates),
 * a plain value is wrapped in a constant signal, and a read made before the key is
 * bound (e.g. a field initializer) is a lazy signal resolved at render time.
 *
 * <p>Structural slots re-render their destination outside the container's render
 * path ({@code Emitter.reconcileSlot}); each node captures its incoming scope
 * ({@code PathlandNode.environmentForChildren}) so destinations that read a value
 * keep working on every re-render.
 */
public final class Environment {

    static final ThreadLocal<ButtonStyle> BUTTON_STYLE = new ThreadLocal<>();
    private static final ThreadLocal<EnvironmentValues> VALUES = ThreadLocal.withInitial(EnvironmentValues::empty);

    /** The default environment (no persistent state; buttons render with {@link PlainButtonStyle}). */
    public static final Environment DEFAULT = new Environment(null);

    private final com.pathland.view.state.PersistentState state;

    public Environment(com.pathland.view.state.PersistentState state) {
        this.state = state;
    }

    /** The button style active for the current render, defaulting to {@link PlainButtonStyle}. */
    public ButtonStyle buttonStyle() {
        ButtonStyle style = BUTTON_STYLE.get();
        return style != null ? style : PlainButtonStyle.INSTANCE;
    }

    /** The session's persistent state (null for a stateless environment). */
    public com.pathland.view.state.PersistentState state() {
        return state;
    }

    // --- generic environment values ---

    /** The current scope (empty at the top of a render). */
    public static EnvironmentValues current() {
        return VALUES.get();
    }

    /**
     * The scoped value for {@code key} as a signal (SwiftUI {@code @Environment},
     * reactive) — the single way to read an environment value.
     *
     * <p>When {@code key} is bound in the current scope, the concrete binding is
     * returned: an injected {@link Signal} comes back as the same instance (bindable
     * for reactive updates, and its {@code WritableSignal} capability is preserved),
     * a plain value is wrapped in a constant signal. When the key is <em>not</em> yet
     * bound — e.g. read in a field initializer before the enclosing
     * {@code .environment(...)} scope is pushed — a lazy {@link EnvironmentSignal} is
     * returned instead, which resolves the key on each {@code .get()} and keeps the
     * last captured binding.
     *
     * <pre>{@code
     * Router router = Environment.value(Navigation.ROUTER).get();  // one-off read
     * Text.of(Environment.value(Platform.ACTIVE_PATH));            // reactive binding
     * // field style (resolved at render time, inside the pushed scope):
     * private final Signal<Router> router = Environment.value(Navigation.ROUTER);
     * }</pre>
     */
    public static <T> Signal<T> value(EnvironmentKey<T> key) {
        EnvironmentValues scope = VALUES.get();
        if (scope.contains(key)) {
            return toSignal(scope.get(key));
        }
        return new EnvironmentSignal<>(key);
    }

    /** A {@link Signal} as-is; any other value wrapped in a constant signal. */
    @SuppressWarnings("unchecked")
    private static <T> Signal<T> toSignal(Object value) {
        if (value instanceof Signal<?> s) {
            return (Signal<T>) s;
        }
        return () -> (T) value;
    }

    /**
     * A lazy, scope-resolving environment read: created when the key is not yet bound,
     * it captures the binding on the first {@code .get()} where the key is bound
     * (nearest wins) and reuses it afterwards — so a field initializer resolves at
     * render time, and reads outside render (e.g. the emitter's binding effects) keep
     * the captured binding.
     */
    private static final class EnvironmentSignal<T> implements Signal<T> {

        private final EnvironmentKey<T> key;
        private Signal<T> captured; // the resolved binding, or null if never resolved

        EnvironmentSignal(EnvironmentKey<T> key) {
            this.key = key;
        }

        @Override
        public T get() {
            EnvironmentValues scope = VALUES.get();
            if (scope.contains(key)) {
                captured = toSignal(scope.get(key));
            }
            return captured == null ? null : captured.get();
        }
    }

    /** Temporarily set the active scope for a render (save/restore around it). */
    public static void within(EnvironmentValues scope) {
        VALUES.set(scope);
    }

    /** Restore the previous active scope (must pair with a prior {@link #within}). */
    public static void restore(EnvironmentValues previous) {
        VALUES.set(previous);
    }
}