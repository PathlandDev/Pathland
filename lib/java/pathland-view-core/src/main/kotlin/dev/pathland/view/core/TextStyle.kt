package dev.pathland.view.core

/**
 * A predefined typography selected by the `TEXT_STYLE` property.
 *
 * The SwiftUI-style scale runs from largeTitle=0 through caption2=10; the
 * renderer resolves each step to its own font-size/weight/line-height tokens.
 * The code is sent as `F32`.
 */
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
