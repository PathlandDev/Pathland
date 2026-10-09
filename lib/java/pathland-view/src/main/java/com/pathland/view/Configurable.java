package com.pathland.view;

/**
 * A view whose **values** (structural/layout properties, a control's bound
 * value) are configured through a fluent {@link View.Config}. The static
 * {@code View.with(Consumer<Config>)} factory populates it; the chain lives on
 * the returned {@link ViewBuilder} (spec DSL.md §2).
 */
public interface Configurable<C extends View.Config> {

    /** This view's value config. */
    C config();
}
