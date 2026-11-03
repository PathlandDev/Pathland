package dev.pathland.view.core

/** Predefined typographies for the TEXT_STYLE property. */
enum class TextStyle(private val code: Int) {
    LARGE_TITLE(0),
    TITLE(1),
    TITLE2(2),
    TITLE3(3),
    HEADLINE(4),
    SUBHEADLINE(5),
    BODY(6),
    CALLOUT(7),
    FOOTNOTE(8),
    CAPTION(9),
    CAPTION2(10);

    fun code(): Int = code
}
