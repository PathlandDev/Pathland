package dev.pathland.view.core

/**
 * @ref spec/DSL.md#57-styleable-controls-the-style-contract [implements] — ListStyle
 *
 * The presentation strategy for a `List` composite.
 *
 * A style receives the list's row container through [Configuration] and returns
 * the scrollable content; [makeRow] decorates each row (background, separators,
 * insets). The DSL resolves the nearest `ListStyle` from the `LIST_STYLE`
 * environment key. Renderer-owned interaction styling (pressed/hover chrome) is
 * not part of the protocol.
 */
interface ListStyle : Style {
    /** Wrap the list's row container (scroll content chrome). */
    fun makeBody(config: Configuration): View = config.content()

    /** Decorate a single row before it enters the list. */
    fun makeRow(content: View, config: RowConfiguration): View = content

    /**
     * Extra main-axis extent each row gains from this style's chrome (vertical
     * padding + separator thickness). A `List` adds it to the app-declared row
     * content extent so a virtualized list keeps stable row geometry.
     */
    val rowExtentOverhead: Float get() = 0f

    /** The scroll viewport background; `null` uses `list.background`. */
    fun viewportBackground(): Color? = null

    override fun body(content: View): View =
        EnvironmentView.of(content, Environment.LIST_STYLE, this)

    /** The list content handed to a style: the rows and the item count. */
    data class Configuration(val content: View, val itemCount: Int?) {
        fun content(): View = content
        fun itemCount(): Int? = itemCount
    }

    /** The per-row data handed to a style. */
    data class RowConfiguration(
        val index: Int,
        val itemCount: Int,
        val selected: dev.pathland.view.core.signal.Signal<Boolean>,
    ) {
        fun index(): Int = index
        fun itemCount(): Int = itemCount
        fun selected(): dev.pathland.view.core.signal.Signal<Boolean> = selected
        fun isSelected(): Boolean = selected.get()
    }
}
