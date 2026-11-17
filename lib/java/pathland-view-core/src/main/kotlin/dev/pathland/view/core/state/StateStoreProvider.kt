package dev.pathland.view.core.state

/**
 * A pluggable source of [StateStore] instances.
 *
 * A host resolves the provider per platform (in-memory, Redis, file, browser
 * storage); qualifiers select a named backend. The provider is configuration
 * for the session state, not a state store itself.
 */
interface StateStoreProvider {
    /** A store, optionally qualified (e.g. `"redis"`). */
    fun store(vararg qualifiers: String): StateStore

    /** A human-readable platform name (e.g. `"redis"`, `"in-memory"`). */
    fun platform(): String = "unknown"
}
