package dev.pathland.view.core

/**
 * Platform-level environment values injected by the host.
 *
 * The server/desktop host binds the active route and the surface viewport;
 * views read them through `Environment.value(Platform.*)`.
 */
object Platform {
    /** The environment key for the app's active platform path. */
    @JvmField
    val ACTIVE_PATH: EnvironmentKey<String> = EnvironmentKey.of("platform.activePath")

    /** The environment key for the hosting surface's viewport (logical points). */
    @JvmField
    val VIEWPORT: EnvironmentKey<Viewport> = EnvironmentKey.of("platform.viewport")
}

/**
 * A hosting surface's viewport in logical points.
 *
 * Used for lazy initial-window sizing and for responsive layout decisions;
 * `-1` means the host has not measured the surface yet.
 */
data class Viewport(val width: Float, val height: Float)
