package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.router.NavigationMod;
import com.pathland.view.signal.Signal;

/**
 * A composable view (SwiftUI ergonomics). Composite views declare their subtree with
 * {@link #body()}; the library's primitives (leaves, stacks, controls) instead override
 * {@link #render(Environment)} to materialize a {@link PathlandNode}. The interface is
 * open: the library ships the leaf primitives and modifier wrappers, and application
 * code (Quarkus, Spring Boot, desktop) defines its own views by implementing
 * {@code View} directly.
 *
 * <p>{@link #body()} is evaluated once at mount — reactivity comes from signals
 * ({@link Text#of(Signal)}, {@code Signals.computed}), not from body re-evaluation.
 *
 * <p>Modifiers are {@link ViewModifier} values applied with {@link #with(ViewModifier...)}
 * — built-in and application-authored modifiers share one mechanism (spec DSL.md
 * §5.6), applied innermost-first. There is no per-modifier factory sugar on this
 * interface. Constructor (structural/layout) properties are passed to the view
 * constructors and are never chainable.
 */
public interface View {

    /**
     * SwiftUI-style composition: a composite view returns its subtree here. Primitives
     * return {@code this} (the identity body) and instead override
     * {@link #render(Environment)}.
     */
    default View body() {
        return this;
    }

    /**
     * Materialize this view into retained node(s). Composite views resolve
     * {@link #body()} first (recursively); primitives override this to build a
     * {@link PathlandNode}.
     */
    default PathlandNode render(Environment env) {
        com.pathland.view.state.PersistentState state = env.state();
        if (state != null) {
            state.connect(this); // wire this view's State fields (idempotent)
        }
        View resolved = body();
        if (resolved != this) {
            return resolved.render(env);
        }
        throw new UnsupportedOperationException(
                getClass().getName() + " must override body() (composite) or render() (primitive)");
    }

    /**
     * Wrap this view in one or more modifiers (SwiftUI {@code .modifier}), applied
     * innermost-first: {@code with(A, B, C)} ≡ {@code with(A).with(B).with(C)}. A
     * single modifier is the common case; an empty call returns {@code this}.
     */
    default View with(ViewModifier... modifiers) {
        View result = this;
        for (ViewModifier modifier : modifiers) {
            result = Modified.apply(result, modifier);
        }
        return result;
    }

    /**
     * Apply a {@link Font} (SwiftUI {@code .font(_:)}): a predefined typography
     * ({@code Font.headline()}, … → a heading element for the heading styles), a
     * custom family + size, or a system size/weight/design.
     */
    default View font(Font font) {
        return with(FontMod.of(font));
    }

    /**
     * Declare this view a route-changer that {@code navigate}s to {@code to} (a
     * direct selection — no back-stack entry). Resolved to the nearest enclosing
     * {@code Router} by the emitter (spec DSL.md §4.5 "any component can change
     * the route").
     */
    default View navigate(String to) {
        return with(NavigationMod.navigate(to));
    }

    /** Declare this view a route-changer that {@code push}es to {@code to} (drill-down). */
    default View push(String to) {
        return with(NavigationMod.push(to));
    }

    /** Declare this view a route-changer that {@code replace}s to {@code to}. */
    default View replace(String to) {
        return with(NavigationMod.replace(to));
    }

    /**
     * Scope an environment value down this subtree (SwiftUI {@code .environment}):
     * the binding is active only while this subtree renders — nearest wins, so an
     * inner binding overrides an outer one for its subtree. Read it with
     * {@code Environment.value(key)}, which always returns a signal (a plain value
     * is wrapped; a signal value is returned as-is and stays reactive).
     */
    default <T> View environment(EnvironmentKey<T> key, T value) {
        return with(EnvironmentMod.of(key, value));
    }

    /**
     * Scope a reactive environment value down this subtree: the signal is returned
     * as-is by {@code Environment.value(key)}, so a node bound to it (e.g.
     * {@code Text.of(Environment.value(key))}) re-emits when it changes.
     */
    default <T> View environment(EnvironmentKey<T> key, Signal<T> value) {
        return with(EnvironmentMod.of(key, value));
    }

    /**
     * Register a listener for the active platform path (spec DSL.md §4.5 — SwiftUI
     * {@code onOpenURL}, generalized): fires whenever {@code Platform.ACTIVE_PATH}
     * changes (including its initial value). Works with or without navigation.
     */
    default View onPathChange(java.util.function.Consumer<String> listener) {
        return with(new PathChangeMod(listener));
    }
}