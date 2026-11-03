package dev.pathland.view.core

import dev.pathland.view.core.signal.Signal
import java.util.function.Consumer

/** Scopes an EnvironmentKey → value binding down a wrapped subtree. */
class EnvironmentBinding private constructor(
    private val key: EnvironmentKey<*>?,
    private val value: Any?,
) : ViewModifier {

    /** EnvironmentBinding values. */
    class Config {
        var key: EnvironmentKey<*>? = null
        var value: Any? = null

        fun key(key: EnvironmentKey<*>): Config {
            this.key = key
            return this
        }

        fun value(value: Any?): Config {
            this.value = value
            return this
        }

        fun value(value: Signal<*>): Config {
            this.value = value
            return this
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun body(content: View): View =
        EnvironmentView.of(content, key as EnvironmentKey<Any?>, value)

    companion object {
        /** Configure the binding. */
        @JvmStatic
        fun with(configure: Consumer<Config>): EnvironmentBinding {
            val config = Config()
            configure.accept(config)
            return EnvironmentBinding(config.key, config.value)
        }
    }
}
