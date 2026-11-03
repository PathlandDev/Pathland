package dev.pathland.view.core

/** Platform-level environment values and helpers. */
object Platform {
    /** The environment key for the app's active platform path. */
    @JvmField
    val ACTIVE_PATH: EnvironmentKey<String> = EnvironmentKey.of("platform.activePath")
}
