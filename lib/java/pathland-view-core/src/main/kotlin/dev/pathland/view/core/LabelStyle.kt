package dev.pathland.view.core

/**
 * The presentation strategy for a `Label`.
 *
 * A style receives the optional icon and title parts and returns the rendered
 * content. The DSL resolves the nearest `LabelStyle` from the `LABEL_STYLE`
 * environment key and falls back to the default icon-then-title row.
 */
interface LabelStyle : Style {
    fun makeBody(config: Configuration): View

    override fun body(content: View): View =
        EnvironmentView.of(content, Environment.LABEL_STYLE, this)

    /** The optional title and icon parts handed to a label style. */
    data class Configuration(val title: View?, val icon: View?) {
        fun title(): View? = title
        fun icon(): View? = icon
        fun hasTitle(): Boolean = title != null
        fun hasIcon(): Boolean = icon != null
    }
}
