package dev.pathland.view.core.router

import java.util.Objects

/** A navigation route: the absolute path that identifies a destination. */
data class Route(val path: String) {
    init {
        Objects.requireNonNull(path, "path")
        require(path.startsWith("/")) { "route paths must be absolute: $path" }
    }

    fun path(): String = path

    /** The path with any query/fragment stripped. */
    fun pathOnly(): String = pathOnly(path)

    companion object {
        /** A route for an absolute path. */
        fun of(path: String): Route = Route(path)

        private fun pathOnly(path: String): String {
            var end = path.length
            val query = path.indexOf('?')
            val fragment = path.indexOf('#')
            if (query >= 0) end = minOf(end, query)
            if (fragment >= 0) end = minOf(end, fragment)
            return path.substring(0, end)
        }
    }
}
