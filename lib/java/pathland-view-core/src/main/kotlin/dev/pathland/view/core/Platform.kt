package dev.pathland.view.core

/** Platform-level environment values and helpers. */
object Platform {
    /** The environment key for the app's active platform path. */
    @JvmField
    val ACTIVE_PATH: EnvironmentKey<String> = EnvironmentKey.of("platform.activePath")

    /** The environment key for the hosting surface's viewport (logical points). */
    @JvmField
    val VIEWPORT: EnvironmentKey<Viewport> = EnvironmentKey.of("platform.viewport")
}

/** A hosting surface's viewport in logical points. */
data class Viewport(val width: Float, val height: Float)
