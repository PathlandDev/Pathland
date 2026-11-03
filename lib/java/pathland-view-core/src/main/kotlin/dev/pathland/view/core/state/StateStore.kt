package dev.pathland.view.core.state

import java.util.Optional

/**
 * A cross-platform state store abstraction. The same application code runs on a
 * multi-tenant server (Redis), a desktop app (File/SQLite), a browser
 * (LocalStorage), or an embedded target by swapping the implementation.
 */
interface StateStore {
    /** Load state for [key] (type-checked against [clazz]), or empty when absent. */
    fun <T : Any> load(key: String, clazz: Class<T>): Optional<T>

    /** Persist [state] for [key]. */
    fun save(key: String, state: Any)

    /** Remove state for [key]. */
    fun delete(key: String)
}
