package dev.pathland.view.core

import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.protocol.TokenPath
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals
import java.util.function.Consumer

/** Font family (a STRING property). */
class FontFamily private constructor(private val config: Config) : ViewModifier {

    /** A font-family design-token reference. */
    data class Token(val token: String) : TokenPath {
        fun token(): String = token
        override fun tokenPath(): String = token
    }

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
