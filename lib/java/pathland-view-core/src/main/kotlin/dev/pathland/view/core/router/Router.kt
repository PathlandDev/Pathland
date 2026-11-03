package dev.pathland.view.core.router

import dev.pathland.view.core.View
import dev.pathland.view.core.signal.EffectOptions
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.core.transport.Event
import java.util.ArrayDeque
import java.util.Deque
import java.util.Objects

/** App-owned navigation state: a writable route signal plus a back-stack. */
class Router {
    private val table: RouteTable
    private val stack: Deque<Route> = ArrayDeque()
    private val current: WritableSignal<Route>
    private val boundPath: WritableSignal<String>?
    private var selfWriting = false

    /** A router over [table]; seed the initial route before mount. */
    constructor(table: RouteTable) {
        this.table = Objects.requireNonNull(table, "table")
        this.current = Signals.signal(Route.of("/"))
        this.boundPath = null
    }

    /** A router bound to an external active-path signal. */
    constructor(table: RouteTable, activePath: Signal<String>) {
        this.table = Objects.requireNonNull(table, "table")
        Objects.requireNonNull(activePath, "activePath")
        val initial = activePath.get()
        this.current = Signals.signal(Route.of(pathOf(if (initial == null) "/" else initial)))
        this.boundPath = activePath as? WritableSignal<String>
        Signals.effect({
            val path = activePath.get()
            if (!selfWriting && path != null) {
                handlePlatformNavigation(path)
            }
        }, EffectOptions.allowWrites())
    }

    fun routeSignal(): Signal<Route> = current.asReadonly()
    fun current(): Route = current.get()
    fun path(): String = current().pathOnly()

    fun depth(): Int = stack.size + 1

    fun navigate(path: String) {
        go(Route.of(path))
    }

    fun push(path: String) {
        stack.push(current())
        go(Route.of(path))
    }

    fun replace(path: String) {
        go(Route.of(path))
    }

    fun pop() {
        if (stack.isNotEmpty()) go(stack.pop())
    }

    fun back() = pop()

    fun handlePlatformNavigation(url: String) {
        go(Route.of(pathOf(url)))
    }

    fun handleEvent(event: Event) {
        if (event.isNavigateBack) {
            back()
        } else if (event.url() != null) {
            handlePlatformNavigation(event.url()!!)
        }
    }

    fun destination(): View? {
        val match = table.match(path()).orElse(null) ?: return null
        if (match.isRedirect()) return null
        return match.handler()?.destination(match.params())
    }

    private fun go(route: Route) {
        val match = table.match(route.path).orElse(null)
        if (match != null && match.isRedirect()) {
            setCurrent(Route.of(match.redirect()!!))
            return
        }
        setCurrent(route)
    }

    private fun setCurrent(route: Route) {
        current.set(route)
        if (boundPath != null) {
            selfWriting = true
            try {
                boundPath.set(route.pathOnly())
            } finally {
                selfWriting = false
            }
        }
    }

    companion object {
        /** The pathname of a URL or an absolute path. */
        @JvmStatic
        fun pathOf(url: String): String {
            val scheme = url.indexOf("://")
            val start = if (scheme >= 0) url.indexOf('/', scheme + 3) else 0
            val path = if (start < 0) "/" else url.substring(start)
            val query = path.indexOf('?')
            val fragment = path.indexOf('#')
            var end = path.length
            if (query >= 0) end = minOf(end, query)
            if (fragment >= 0) end = minOf(end, fragment)
            val pathOnly = path.substring(0, end)
            return if (pathOnly.isEmpty()) "/" else pathOnly
        }
    }
}
