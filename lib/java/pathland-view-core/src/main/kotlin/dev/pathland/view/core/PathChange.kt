package dev.pathland.view.core

import java.util.function.Consumer

/**
 * Registers an app-side listener for the active platform path.
 *
 * The listener receives route changes as strings; it is never emitted as a
 * protocol property. Navigation-aware views use it to react to a host route
 * change without re-rendering the whole app.
 */
class PathChange private constructor(private val listener: Consumer<String>?) : ViewModifier {

    /** The listener to invoke with the new absolute platform path. */
    class Config {
        var listener: Consumer<String>? = null

        fun listener(listener: Consumer<String>): Config {
            this.listener = listener
            return this
        }
    }

    override fun body(content: View): View = PathChangeView(content, listener)

    companion object {
        /** Configure the path-change listener. */
        fun with(configure: Consumer<Config>): PathChange {
            val config = Config()
            configure.accept(config)
            return PathChange(config.listener)
        }
    }
}
