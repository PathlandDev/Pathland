package dev.pathland.view.core.router

import dev.pathland.view.core.Configurable
import dev.pathland.view.core.Environment
import dev.pathland.view.core.EnvironmentValues
import dev.pathland.view.core.View
import dev.pathland.view.core.ViewBuilder
import dev.pathland.view.core.protocol.Components
import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.engine.PathlandNode
import java.util.Objects
import java.util.function.Consumer

/** The navigation slot: a structural container whose single child is the current route. */
class NavigationContainer private constructor(
    private val config: Config,
) : View, Configurable<NavigationContainer.Config> {

    /** NavigationContainer values. */
    class Config : View.Config {
        var router: Router? = null
        var chrome: Chrome = Chrome.PLATFORM_DEFAULT

        fun router(router: Router): Config {
            this.router = router
            return this
        }

        fun chrome(chrome: Chrome): Config {
            this.chrome = chrome
            return this
        }
    }

    override fun config(): Config = config

    override fun renderNode(env: Environment): PathlandNode {
        val router = Objects.requireNonNull(config.router, "router")!!
        val chrome = Objects.requireNonNull(config.chrome, "chrome")
        val node = PathlandNode(Components.VSTACK)
        val scope = Environment.current().with(Navigation.ROUTER, router)
        val previous = Environment.current()
        Environment.within(scope)
        try {
            val selected = router.destination()
            if (selected != null) {
                node.children.add(selected.render(env))
            }
        } finally {
            Environment.restore(previous)
        }
        node.properties[Properties.ROUTE] = router.path()
        node.properties[Properties.NAV_DEPTH] = router.depth()
        node.properties[Properties.NAV_CHROME] = chrome.wire().toFloat()
        node.properties[Properties.TRANSITION] = 1f
        node.navigateHandler = Consumer { event -> router.handleEvent(event) }
        node.router = router
        return node
    }

    companion object {
        /** An empty NavigationContainer (no values). */
        fun make(): ViewBuilder<NavigationContainer, Config> =
            ViewBuilder(NavigationContainer(Config()))

        fun make(configure: Consumer<Config>): ViewBuilder<NavigationContainer, Config> {
            val config = Config()
            configure.accept(config)
            return ViewBuilder(NavigationContainer(config))
        }
    }
}
