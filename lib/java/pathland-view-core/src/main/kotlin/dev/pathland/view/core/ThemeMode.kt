package dev.pathland.view.core

/** The renderer-wide color-scheme mode (`META::SET_COLOR_SCHEME`). */
enum class ThemeMode(private val code: Int) {
    PLATFORM(0),
    LIGHT(1),
    DARK(2);

    fun code(): Int = code
}
