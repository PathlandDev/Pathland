package dev.pathland.view.core.router

import dev.pathland.view.core.View
import java.util.LinkedHashMap
import java.util.Objects
import java.util.Optional
import java.util.function.Predicate

/** A declarative path → destination table with params and guards. */
class RouteTable private constructor(
    private val entries: List<Entry>,
    private val fallback: RouteHandler?,
) {
    /** Match an absolute path, returning the first match or the fallback. */
    fun match(path: String): Optional<RouteMatch> {
        val pathOnly = Route.of(path).pathOnly()
        for (entry in entries) {
            val params = entry.match(pathOnly)
            if (!params.isPresent) continue
            val captured = params.get()
            if (entry.guard != null && !entry.guard.test(captured)) {
                return Optional.of(RouteMatch.redirect(entry.redirect))
            }
            return Optional.of(RouteMatch(pathOnly, captured, entry.handler, null))
        }
        return if (fallback != null) {
            Optional.of(RouteMatch(pathOnly, Params.none(), fallback, null))
        } else {
            Optional.empty()
        }
    }

    /** A match: either a destination (handler + params) or a guard redirect. */
    data class RouteMatch(
        val path: String?,
        val params: Params,
        val handler: RouteHandler?,
        val redirect: String?,
    ) {
        fun path(): String? = path
        fun params(): Params = params
        fun handler(): RouteHandler? = handler
        fun redirect(): String? = redirect

        /** True when a guard redirected this match (replace, not mount). */
        fun isRedirect(): Boolean = redirect != null

        companion object {
            internal fun redirect(target: String?): RouteMatch =
                RouteMatch(null, Params.none(), null, target)
        }
    }

    /** Builds a RouteTable. */
    class Builder {
        private val entries = ArrayList<Entry>()
        private var fallback: RouteHandler? = null

        fun route(pattern: String, view: View): Builder {
            entries.add(Entry(pattern, RouteHandler { view }, null, null))
            return this
        }

        fun route(pattern: String, handler: RouteHandler): Builder {
            entries.add(Entry(pattern, handler, null, null))
            return this
        }

        fun route(pattern: String, guard: Predicate<Params>, redirect: String?, view: View): Builder {
            entries.add(Entry(pattern, RouteHandler { view }, Objects.requireNonNull(guard, "guard"), redirect))
            return this
        }

        fun route(pattern: String, guard: Predicate<Params>, redirect: String?, handler: RouteHandler): Builder {
            entries.add(Entry(pattern, handler, Objects.requireNonNull(guard, "guard"), redirect))
            return this
        }

        fun fallback(view: View): Builder = fallback(RouteHandler { view })

        fun fallback(handler: RouteHandler): Builder {
            this.fallback = Objects.requireNonNull(handler, "fallback")
            return this
        }

        fun build(): RouteTable = RouteTable(entries.toList(), fallback)
    }

    private class Entry(
        pattern: String,
        val handler: RouteHandler,
        val guard: Predicate<Params>?,
        val redirect: String?,
    ) {
        private val segments = parse(pattern)

        fun match(path: String): Optional<Params> {
            val given = split(path)
            if (given.size != segments.size) return Optional.empty()
            val params = LinkedHashMap<String, String>()
            for (i in segments.indices) {
                if (!segments[i].matches(given[i], params)) return Optional.empty()
            }
            return Optional.of(Params.of(params, path))
        }

        companion object {
            private fun parse(pattern: String): List<Segment> =
                split(pattern).map { part ->
                    if (part.startsWith(":")) Segment.Param(part.substring(1))
                    else Segment.Literal(part)
                }

            private fun split(path: String): Array<String> {
                val trimmed = if (path.startsWith("/")) path.substring(1) else path
                return if (trimmed.isEmpty()) emptyArray() else trimmed.split("/").toTypedArray()
            }
        }
    }

    private sealed interface Segment {
        fun matches(part: String, params: MutableMap<String, String>): Boolean

        class Literal(private val value: String) : Segment {
            override fun matches(part: String, params: MutableMap<String, String>): Boolean = value == part
        }

        class Param(private val name: String) : Segment {
            override fun matches(part: String, params: MutableMap<String, String>): Boolean {
                params[name] = part
                return true
            }
        }
    }

    companion object {
        /** Start building a route table. */
        fun builder(): Builder = Builder()
    }
}
