package dev.pathland.view.core.router

import dev.pathland.view.core.View
import dev.pathland.view.core.ViewModifier

/**
 * The navigation action modifier: marks a view as a route-changer.
 *
 * It records the `NavOp` and target on the node; the host routes a tap through
 * the nearest enclosing router. It does not emit a protocol property itself.
 */
class NavigationIntent private constructor(
    private val to: String,
    private val op: NavOp,
) : ViewModifier {
    override fun body(content: View): View = NavigationIntentView(content, to, op)

    companion object {
        /** A NAVIGATE route-change to [to]. */
        fun navigate(to: String): NavigationIntent = NavigationIntent(to, NavOp.NAVIGATE)

        /** A PUSH route-change to [to] (drill-down). */
        fun push(to: String): NavigationIntent = NavigationIntent(to, NavOp.PUSH)

        /** A REPLACE route-change to [to]. */
        fun replace(to: String): NavigationIntent = NavigationIntent(to, NavOp.REPLACE)
    }
}
