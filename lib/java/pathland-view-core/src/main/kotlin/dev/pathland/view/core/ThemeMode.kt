package dev.pathland.view.core

/**
 * The forced color-scheme preference (`META::SET_COLOR_SCHEME`):
 * platform=0 follows the host's effective scheme, light=1, dark=2.
 *
 * The renderer resolves token values against this mode; it is renderer state,
 * not a node property.
 */
enum class ThemeMode(private val code: Int) {
    PLATFORM(0),
    LIGHT(1),
    DARK(2);

    fun code(): Int = code
}
