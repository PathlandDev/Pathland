package dev.pathland.view.core

import dev.pathland.core.protocol.Properties
import dev.pathland.core.protocol.TokenPath
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import java.util.function.Consumer

/**
 * Sets the font family (`FONT_FAMILY`, a STRING) for the wrapped subtree.
 *
 * Accepts a literal, a signal, or a `Token` design-token reference; a token is
 * emitted as a `DESIGN_TOKEN` value and resolved by the renderer.
 */
class FontFamily private constructor(private val config: Config) : ViewModifier {

    /** A design-token reference for `FONT_FAMILY`; the renderer resolves it. */
    data class Token(val token: String) : TokenPath {
        fun token(): String = token
        override fun tokenPath(): String = token
    }

    /** A literal/signal family or a design-token reference; the forms are exclusive. */
    class Config {
        var value: Signal<String>? = null
        var token: Token? = null

        fun family(value: String): Config {
            this.value = Signals.constant(value)
            this.token = null
            return this
        }

        fun family(value: Signal<String>): Config {
            this.value = value
            this.token = null
            return this
        }

        fun family(token: Token): Config {
            this.token = token
            this.value = null
            return this
        }
    }

    override fun body(content: View): View {
        val token = config.token
        if (token != null) {
            return Modified.props(content, Modified.prop(Properties.FONT_FAMILY, token))
        }
        val value = config.value ?: Signals.constant("")
        return Modified.props(content, Modified.prop(Properties.FONT_FAMILY, value))
    }

    companion object {
        fun with(value: String): FontFamily = FontFamily(Config().family(value))

        fun with(value: Signal<String>): FontFamily = FontFamily(Config().family(value))

        fun with(token: Token): FontFamily = FontFamily(Config().family(token))

        /** Configure the font family. */
        fun with(configure: Consumer<Config>): FontFamily {
            val config = Config()
            configure.accept(config)
            return FontFamily(config)
        }
    }
}
