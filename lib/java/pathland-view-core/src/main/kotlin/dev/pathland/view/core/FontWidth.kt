package dev.pathland.view.core

import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import java.util.function.Consumer

/** Font width (0.5–1.5, 1.0 default). */
class FontWidth private constructor(private val config: Config) : ViewModifier {

    class Config {
        var value: Signal<Float>? = null

        fun value(value: Float): Config {
            this.value = Signals.constant(value)
            return this
        }

        fun value(value: Signal<Float>): Config {
            this.value = value
            return this
        }
    }

    override fun body(content: View): View {
        val value = config.value ?: Signals.constant(1f)
        return Modified.props(content, Modified.prop(Properties.FONT_WIDTH, value))
    }

    companion object {
        @JvmStatic
        fun with(value: Float): FontWidth = FontWidth(Config().value(value))

        @JvmStatic
        fun with(value: Signal<Float>): FontWidth = FontWidth(Config().value(value))

        /** Configure the font width. */
        @JvmStatic
        fun with(configure: Consumer<Config>): FontWidth {
            val config = Config()
            configure.accept(config)
            return FontWidth(config)
        }
    }
}
