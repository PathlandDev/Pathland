package dev.pathland.view.core.router

import dev.pathland.view.core.EnvironmentKey
import dev.pathland.view.core.View
import dev.pathland.view.core.ViewBuilder
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import java.util.function.Predicate

/** The ergonomic facade for routing and navigation. */
object Navigation {
    /** The environment key for the active Router. */
    @JvmField
    val ROUTER: EnvironmentKey<Router> = EnvironmentKey.of("router")

    /** The navigation container over [router]. */
    @JvmStatic
    fun container(router: Router): ViewBuilder<NavigationContainer, NavigationContainer.Config> =
        container(router, Chrome.PLATFORM_DEFAULT)

    /** The navigation container over [router] with an explicit chrome mode. */
    @JvmStatic
    fun container(
        router: Router,
        chrome: Chrome,
    ): ViewBuilder<NavigationContainer, NavigationContainer.Config> =
        NavigationContainer.make { n -> n.router(router).chrome(chrome) }

    /** A route list builder for a router seeded at `/`. */
    @JvmStatic
    fun navigator(): Builder = Builder("/")

    /** A route list builder seeded at [initialPath]. */
    @JvmStatic
    fun navigator(initialPath: String): Builder = Builder(initialPath)

    /** A reactive "is this the active route" signal. */
    @JvmStatic
    fun isActive(router: Router, path: String): Signal<Boolean> =
        Signals.computed { router.path() == path }

    /** A fluent route list → seeded Router builder. */
    class Builder internal constructor(private val initialPath: String) {
        private val table = RouteTable.builder()

        fun route(pattern: String, view: View): Builder {
            table.route(pattern, view)
            return this
        }

        fun route(pattern: String, handler: RouteHandler): Builder {
            table.route(pattern, handler)
            return this
        }

        fun route(pattern: String, guard: Predicate<Params>, redirect: String?, view: View): Builder {
            table.route(pattern, guard, redirect, view)
            return this
        }

        fun route(pattern: String, guard: Predicate<Params>, redirect: String?, handler: RouteHandler): Builder {
            table.route(pattern, guard, redirect, handler)
            return this
        }

        fun fallback(view: View): Builder {
            table.fallback(view)
            return this
        }

        fun fallback(handler: RouteHandler): Builder {
            table.fallback(handler)
            return this
        }

        fun build(): Router {
            val router = Router(table.build())
            router.navigate(initialPath)
            return router
        }

        fun build(activePath: Signal<String>): Router = Router(table.build(), activePath)
    }
}
