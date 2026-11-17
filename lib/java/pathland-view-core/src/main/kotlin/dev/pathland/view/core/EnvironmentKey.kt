package dev.pathland.view.core

/**
 * A typed key for a scoped environment value.
 *
 * Identity is the key instance, not its name: two keys with the same name are
 * distinct bindings. Values are resolved through the nearest enclosing scope and
 * are never emitted as node properties.
 */
class EnvironmentKey<T> private constructor(private val name: String) {
    override fun toString(): String = "EnvironmentKey[$name]"

    companion object {
        /** A named environment key (identity is by instance). */
        fun <T> of(name: String): EnvironmentKey<T> = EnvironmentKey(name)
    }
}
