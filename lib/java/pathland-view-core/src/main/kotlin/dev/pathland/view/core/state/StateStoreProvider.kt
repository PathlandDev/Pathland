package dev.pathland.view.core.state

/** Pluggable provider of [StateStore]s. */
interface StateStoreProvider {
    /** A store, optionally qualified (e.g. `"redis"`). */
    fun store(vararg qualifiers: String): StateStore

    /** A human-readable platform name (e.g. `"redis"`, `"in-memory"`). */
    fun platform(): String = "unknown"
}
