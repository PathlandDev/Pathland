package dev.pathland.view.core

/** Protocol semantic-role codes for the ROLE property. */
object Roles {
    const val NONE = 0
    const val HEADER = 1
    const val PARAGRAPH = 2
    const val LIST = 3
    const val LIST_ITEM = 4
    const val SUMMARY = 5
    const val BANNER = 6
    const val NAVIGATION = 7
    const val MAIN = 8
    const val CONTENT_INFO = 9
    const val COMPLEMENTARY = 10
    const val ARTICLE = 11
    const val SECTION = 12
    const val SEARCH = 13

    /** Whether [code] is a defined semantic role. */
    fun isDefined(code: Int): Boolean = code in NONE..SEARCH
}
