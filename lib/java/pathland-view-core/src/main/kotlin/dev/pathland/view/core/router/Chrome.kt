package dev.pathland.view.core.router

/**
 * Who supplies navigation chrome for a `NavigationContainer`.
 *
 * `PLATFORM_DEFAULT` (0) lets the renderer provide the native header/back
 * affordance; `CUSTOM` (1) means the application draws all navigation UI and
 * the renderer adds none. The code is emitted as `NAV_CHROME`.
 */
enum class Chrome(private val wire: Int) {
    PLATFORM_DEFAULT(0),
    CUSTOM(1);

    /** The wire enum code for `NAV_CHROME` (F32). */
    fun wire(): Int = wire
}
