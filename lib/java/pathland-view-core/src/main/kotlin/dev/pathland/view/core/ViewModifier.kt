package dev.pathland.view.core

/**
 * A reusable transformation of a view.
 *
 * Modifiers compose innermost-first and are applied while building the retained
 * node. Most implementations wrap the content in `Modified` so the emitted node
 * carries the corresponding protocol property; a modifier may also install a
 * dynamic environment or listen to app-side events.
 */
fun interface ViewModifier {
    /** Transform [content] into a new view (typically the styled body). */
    fun body(content: View): View
}
