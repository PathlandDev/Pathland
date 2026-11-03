package dev.pathland.view.core

/** A reusable view modifier. */
fun interface ViewModifier {
    /** Transform [content] into a new view (typically the styled body). */
    fun body(content: View): View
}
