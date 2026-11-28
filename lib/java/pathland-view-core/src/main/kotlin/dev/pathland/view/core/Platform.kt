package dev.pathland.view.core

import dev.pathland.view.core.signal.WritableSignal

/**
 * Platform-level environment values injected by the host.
 *
 * The server/desktop host binds the active route, the surface viewport, and the
 * theme-mode preference; views read them through `Environment.value(Platform.*)`.
 */
object Platform {
    /** The environment key for the app's active platform path. */
    @JvmField
    val ACTIVE_PATH: EnvironmentKey<String> = EnvironmentKey.of("platform.activePath")

    /** The environment key for the hosting surface's viewport (logical points). */
    @JvmField
    val VIEWPORT: EnvironmentKey<Viewport> = EnvironmentKey.of("platform.viewport")

    /**
     * The host-owned color-scheme preference (`PLATFORM`/`LIGHT`/`DARK`).
     *
     * The host observes this signal and emits `META::SET_COLOR_SCHEME`, so an
     * app can request a scheme through ordinary view state without depending on
     * the host runtime's emitter API. The wrapper keeps the environment binding
     * from treating the writable signal itself as the reactive value.
     */
    @JvmField
    val COLOR_SCHEME: EnvironmentKey<ColorSchemeControl> =
        EnvironmentKey.of("platform.colorScheme")

    /** A host-owned, writable color-scheme preference for the [COLOR_SCHEME] binding. */
    class ColorSchemeControl(val mode: WritableSignal<ThemeMode>)
}

/**
 * A hosting surface's viewport in logical points.
 *
 * Used for lazy initial-window sizing and for responsive layout decisions;
 * `-1` means the host has not measured the surface yet.
 */
data class Viewport(val width: Float, val height: Float)
