package dev.pathland.view.core

import java.util.HashMap

/** An immutable, hierarchical scope of environment values. */
class EnvironmentValues private constructor(
    private val parent: EnvironmentValues?,
    private val key: EnvironmentKey<*>?,
    private val value: Any?,
) {
    /** A scope extending this one with a binding (nearest wins). */
    fun <T> with(key: EnvironmentKey<T>, value: T): EnvironmentValues =
        EnvironmentValues(this, key, value)

    /** The value bound to [key], walking the chain; null when absent. */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: EnvironmentKey<T>): T? {
        var node: EnvironmentValues? = this
        while (node != null) {
            if (node.key === key) return node.value as T?
            node = node.parent
        }
        return null
    }

    /** Whether [key] is bound anywhere in the chain. */
    fun contains(key: EnvironmentKey<*>): Boolean {
        var node: EnvironmentValues? = this
        while (node != null) {
            if (node.key === key) return true
            node = node.parent
        }
        return false
    }

    internal fun flattened(): Map<EnvironmentKey<*>, Any?> {
        val out = HashMap<EnvironmentKey<*>, Any?>()
        var node: EnvironmentValues? = this
        while (node != null) {
            if (node.key != null && !out.containsKey(node.key)) {
                out[node.key] = node.value
            }
            node = node.parent
        }
        return out
    }

    override fun equals(other: Any?): Boolean =
        other is EnvironmentValues && flattened() == other.flattened()

    override fun hashCode(): Int = flattened().hashCode()

    companion object {
        private val EMPTY = EnvironmentValues(null, null, null)

        /** The empty scope (no bindings). */
        fun empty(): EnvironmentValues = EMPTY
    }
}
