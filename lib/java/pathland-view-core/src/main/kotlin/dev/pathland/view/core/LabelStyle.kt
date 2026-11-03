package dev.pathland.view.core

/** The presentation of a Label. */
interface LabelStyle : Style {
    fun makeBody(config: Configuration): View

    override fun body(content: View): View =
        EnvironmentView.of(content, Environment.LABEL_STYLE, this)

    /** The parts of the label being styled (null when absent). */
    data class Configuration(val title: View?, val icon: View?) {
        fun title(): View? = title
        fun icon(): View? = icon
        fun hasTitle(): Boolean = title != null
        fun hasIcon(): Boolean = icon != null
    }
}
