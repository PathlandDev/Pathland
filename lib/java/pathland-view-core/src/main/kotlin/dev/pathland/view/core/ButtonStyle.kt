package dev.pathland.view.core

/** A button style: decorates the button's label content. */
interface ButtonStyle : Style {
    fun makeBody(config: Configuration): View?

    override fun body(content: View): View =
        EnvironmentView.of(content, Environment.BUTTON_STYLE, this)

    /** The configuration of the button being styled: its label. */
    data class Configuration(val label: View?) {
        fun label(): View? = label
    }
}
