package dev.pathland.view.core

/**
 * The presentation strategy for a `BUTTON`.
 *
 * A style receives the button's label content through [Configuration] and
 * returns the body to render. The DSL resolves the nearest `ButtonStyle` from
 * the `BUTTON_STYLE` environment key; the renderer still owns pressed/hover and
 * disabled chrome (interaction styling is not part of the protocol).
 */
interface ButtonStyle : Style {
    fun makeBody(config: Configuration): View?

    override fun body(content: View): View =
        EnvironmentView.of(content, Environment.BUTTON_STYLE, this)

    /** The button content handed to a style: the explicit label subtree, if any. */
    data class Configuration(val label: View?) {
        fun label(): View? = label
    }
}
