package dev.pathland.view.core

import java.util.function.Consumer

/** Registers a listener for the active platform path. */
class PathChange private constructor(private val listener: Consumer<String>?) : ViewModifier {

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
        @JvmStatic
        fun with(configure: Consumer<Config>): PathChange {
            val config = Config()
            configure.accept(config)
            return PathChange(config.listener)
        }
    }
}
