package com.pathland.view;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared plumbing for the single-value {@code with(...)} shortcuts (spec DSL.md
 * §2): a shortcut is pure sugar for the config-builder lambda, so it builds a
 * fresh {@link View.Config}, applies the primary setter, and returns the view's
 * {@link ViewBuilder} (or the modifier value). Package-private — used only by the
 * library's own {@code with(value)} overloads.
 */
final class Configurators {

    private Configurators() {
    }

    /** A view shortcut: configure {@code ctor}'s fresh config and wrap it in a builder. */
    static <V extends View & Configurable<C>, C extends View.Config> ViewBuilder<V, C> view(
            Supplier<C> config, Function<C, V> ctor, Consumer<C> setter) {
        C c = config.get();
        setter.accept(c);
        return new ViewBuilder<>(ctor.apply(c));
    }

    /** A modifier shortcut: configure a fresh config and construct the modifier value. */
    static <M, C> M modifier(Supplier<C> config, Function<C, M> ctor, Consumer<C> setter) {
        C c = config.get();
        setter.accept(c);
        return ctor.apply(c);
    }
}