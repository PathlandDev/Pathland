package dev.pathland.view.core

/** A typed key for a scoped environment value (identity-based). */
class EnvironmentKey<T> private constructor(private val name: String) {
    override fun toString(): String = "EnvironmentKey[$name]"

    companion object {
        /** A named environment key (identity is by instance). */
        fun <T> of(name: String): EnvironmentKey<T> = EnvironmentKey(name)
    }
}
