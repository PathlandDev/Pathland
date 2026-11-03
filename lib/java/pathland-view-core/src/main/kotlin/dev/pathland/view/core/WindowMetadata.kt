package dev.pathland.view.core

import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.engine.PathlandNode
import java.util.function.Consumer

/** Window/surface presentation metadata (`windowMetadata(...)`). */
class WindowMetadata private constructor(
    private val title: Signal<String>?,
    private val description: Signal<String>?,
    private val locale: Signal<String>?,
) : ViewModifier {

    class Config {
        var title: Signal<String>? = null
        var description: Signal<String>? = null
        var locale: Signal<String>? = null

        fun title(value: String): Config {
            this.title = Signals.constant(value)
            return this
        }

        fun title(signal: Signal<String>): Config {
            this.title = signal
            return this
        }

        fun description(value: String): Config {
            this.description = Signals.constant(value)
            return this
        }

        fun description(signal: Signal<String>): Config {
            this.description = signal
            return this
        }

        fun locale(value: String): Config {
            this.locale = Signals.constant(value)
            return this
        }

        fun locale(signal: Signal<String>): Config {
            this.locale = signal
            return this
        }
    }

    fun title(): Signal<String>? = title
    fun description(): Signal<String>? = description
    fun locale(): Signal<String>? = locale

    override fun body(content: View): View = WindowMetadataView(content, this)

    private class WindowMetadataView(
        private val content: View,
        private val metadata: WindowMetadata,
    ) : View {
        override fun renderNode(env: Environment): PathlandNode {
            env.setWindowMetadata(metadata)
            return content.renderNode(env)
        }
    }

    companion object {
        /** Configure the window/surface metadata. */
        @JvmStatic
        fun with(configure: Consumer<Config>): WindowMetadata {
            val config = Config()
            configure.accept(config)
            return WindowMetadata(config.title, config.description, config.locale)
        }
    }
}
