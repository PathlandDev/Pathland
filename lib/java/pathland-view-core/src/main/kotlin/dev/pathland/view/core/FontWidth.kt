package dev.pathland.view.core

import dev.pathland.core.protocol.Properties
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import java.util.function.Consumer

/**
 * Sets the font width expansion (`FONT_WIDTH`, 0.5–1.5; 1.0 default).
 *
 * Values are emitted as `F32` and inherited by text in the wrapped subtree.
 */
class FontWidth private constructor(private val config: Config) : ViewModifier {

    /** Width values as literals or signals; 1.0 is normal, lower narrows, higher widens. */
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
        fun with(value: Float): FontWidth = FontWidth(Config().value(value))

        fun with(value: Signal<Float>): FontWidth = FontWidth(Config().value(value))

        /** Configure the font width. */
        fun with(configure: Consumer<Config>): FontWidth {
            val config = Config()
            configure.accept(config)
            return FontWidth(config)
        }
    }
}
