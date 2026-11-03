package dev.pathland.view.core.router

import dev.pathland.view.core.View
import dev.pathland.view.core.ViewModifier

/** The navigation action modifier: marks a view as a route-changer. */
class NavigationIntent private constructor(
    private val to: String,
    private val op: NavOp,
) : ViewModifier {
    override fun body(content: View): View = NavigationIntentView(content, to, op)

    companion object {
        /** A NAVIGATE route-change to [to]. */
        @JvmStatic
        fun navigate(to: String): NavigationIntent = NavigationIntent(to, NavOp.NAVIGATE)

        /** A PUSH route-change to [to] (drill-down). */
        @JvmStatic
        fun push(to: String): NavigationIntent = NavigationIntent(to, NavOp.PUSH)

        /** A REPLACE route-change to [to]. */
        @JvmStatic
        fun replace(to: String): NavigationIntent = NavigationIntent(to, NavOp.REPLACE)
    }
}
