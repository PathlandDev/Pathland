package com.pathland.view;

import com.pathland.view.signal.Signal;

/**
 * The rendering environment — the single inheritance/scoping mechanism in the DSL.
 * Carries a hierarchical scope of {@link EnvironmentValues} keyed by
 * {@link EnvironmentKey} (SwiftUI {@code EnvironmentValues} style): a parent injects
 * a value down the entire child tree via {@code .environment(key, value)}, and any
 * descendant reads it with {@link #value(EnvironmentKey)}.
 *
 * <p>A {@code .environment(key, value)} modifier binds a value for the subtree it
 * wraps — nearest wins, so an inner binding overrides an outer one. The mount render
 * is synchronous and single-threaded, so a {@link ThreadLocal} is equivalent to
 * ScopedValue here while keeping the library on every LTS from Java 17. This
 * thread-local is the <em>implementation</em> of the environment, never a parallel
 * mechanism: no modifier may scope values by any other route.
 *
 * <p>Reads always return a signal ({@link #value(EnvironmentKey)}): a value injected
 * as a {@link Signal} comes back as the same instance (bindable for reactive updates),
 * a plain value is wrapped in a constant signal, and a read made before the key is
 * bound (e.g. a field initializer) is a lazy signal resolved at render time.
 *
 * <p>Structural slots re-render their destination outside the container's render
 * path ({@code Emitter.reconcileSlot}); each node captures its incoming scope
 * ({@code PathlandNode.environmentForChildren}) so destinations that read a value
 * keep working on every re-render — including scoped styles like {@link #BUTTON_STYLE}.
 */
public final class Environment {

    /**
     * The environment key for the active {@link ButtonStyle} (SwiftUI
     * {@code .buttonStyle}). {@code ButtonStyleMod} binds it down the wrapped subtree
     * via the generic environment; {@link Button} reads it with {@link #buttonStyle()}
     * (defaulting to {@link PlainButtonStyle}).
     */
    public static final EnvironmentKey<ButtonStyle> BUTTON_STYLE = EnvironmentKey.of("buttonStyle");
    private static final ThreadLocal<EnvironmentValues> VALUES = ThreadLocal.withInitial(EnvironmentValues::empty);

    /** The default environment (no persistent state; buttons render with {@link PlainButtonStyle}). */
    public static final Environment DEFAULT = new Environment(null);

    private final com.pathland.view.state.PersistentState state;

    public Environment(com.pathland.view.state.PersistentState state) {
        this.state = state;
    }

    /**
     * The button style active for the current render — the scoped environment value
     * {@link #BUTTON_STYLE}, defaulting to {@link PlainButtonStyle} when no scope
     * binds it.
     */
    public ButtonStyle buttonStyle() {
        ButtonStyle style = Environment.value(BUTTON_STYLE).get();
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