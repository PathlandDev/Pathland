package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

/**
 * A composable view. Composite views declare their subtree with
 * {@link #body()}; the library's primitives (leaves, stacks, controls) instead override
 * {@link #render(Environment)} to materialize a {@link PathlandNode}. The interface is
 * open: the library ships the leaf primitives and modifier wrappers, and application
 * code (Quarkus, Spring Boot, desktop) defines its own views by implementing
 * {@code View} directly.
 *
 * <p>{@link #body()} is evaluated once at mount — reactivity comes from signals
 * ({@code Text.with(t -> t.text(Signal))}, {@code Signals.computed}), not from body
 * re-evaluation.
 *
 * <p>Modifiers are {@link ViewModifier} values applied with {@link #with(ViewModifier...)}
 * — built-in and application-authored modifiers share one mechanism (spec DSL.md
 * §5.6), applied innermost-first. There is no per-modifier factory sugar on this
 * interface. Constructor (structural/layout) properties are passed to the view
 * constructors and are never chainable.
 */
public interface View {

    /**
     * The base marker for a view's **value config** ({@code V.Config}). The
     * config is populated by the static {@code View.with(Consumer<Config>)}
     * factory and is the same type a style's {@code makeBody(Config)} reads
     * (spec DSL.md §5.7).
     */
    interface Config {
    }

    /**
     * Declarative composition: a composite view returns its subtree here. Primitives
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
     * Wrap this view in one or more modifiers ({@code .modifier}), applied
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
}