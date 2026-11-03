package dev.pathland.view.core.router

/** The navigation chrome mode of a NavigationContainer. */
enum class Chrome(private val wire: Int) {
    PLATFORM_DEFAULT(0),
    CUSTOM(1);

    /** The wire enum code for `NAV_CHROME` (F32). */
    fun wire(): Int = wire
}
