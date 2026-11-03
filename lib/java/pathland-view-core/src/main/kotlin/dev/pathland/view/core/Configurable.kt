package dev.pathland.view.core

/** A view whose values are configured through a fluent View.Config. */
interface Configurable<C : View.Config> {
    /** This view's value config. */
    fun config(): C
}
