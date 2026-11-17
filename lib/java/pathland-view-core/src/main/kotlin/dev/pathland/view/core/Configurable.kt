package dev.pathland.view.core

/**
 * A view whose values are configured through a fluent `View.Config`.
 *
 * `with { … }` mutates the config and returns the same view; the static
 * `make(...)` entry points are thin wrappers over this.
 */
interface Configurable<C : View.Config> {
    /** This view's value config. */
    fun config(): C
}
