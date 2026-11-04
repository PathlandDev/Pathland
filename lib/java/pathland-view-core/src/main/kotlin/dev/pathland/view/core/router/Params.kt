package dev.pathland.view.core.router

import java.util.Collections
import java.util.Objects

/** Path parameters captured from a route pattern, plus the matched path. */
class Params private constructor(
    private val values: Map<String, String>,
    private val matchedPath: String?,
) {
    fun path(): String? = matchedPath

    /** The raw string value of a param, or null when absent. */
    operator fun get(name: String): String? = values[name]

    fun intValue(name: String): Int = try {
        get(name)!!.toInt()
    } catch (_: RuntimeException) {
        0
    }

    fun longValue(name: String): Long = try {
        get(name)!!.toLong()
    } catch (_: RuntimeException) {
        0L
    }

    fun doubleValue(name: String): Double = try {
        get(name)!!.toDouble()
    } catch (_: RuntimeException) {
        0.0
    }

    fun booleanValue(name: String): Boolean {
        val v = get(name)
        return v != null && (v.equals("true", ignoreCase = true) || v == "1")
    }

    fun contains(name: String): Boolean = values.containsKey(name)
    fun isEmpty(): Boolean = values.isEmpty()

    override fun equals(other: Any?): Boolean =
        other is Params && values == other.values && matchedPath == other.matchedPath

    override fun hashCode(): Int = Objects.hash(values, matchedPath)
    override fun toString(): String = "Params$values"

    companion object {
        private val NONE = Params(emptyMap(), null)

        /** An empty param set. */
        fun none(): Params = NONE

        /** Wrap captured path params plus the matched path. */
        fun of(values: Map<String, String>, path: String?): Params =
            Params(Collections.unmodifiableMap(values), path)
    }
}
