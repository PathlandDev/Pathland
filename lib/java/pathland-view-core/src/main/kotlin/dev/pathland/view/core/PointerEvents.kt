package dev.pathland.view.core

import java.util.function.Consumer

/** Declares raw input listeners (`EVENT_LISTENERS` bitmask, OR-in). */
class PointerEvents private constructor(private val mask: Int) : ViewModifier {

    class Config {
        var mask: Int = 0

        fun mask(mask: Int): Config {
            this.mask = mask
            return this
        }
    }

    override fun body(content: View): View = PointerEventsView(content, mask)

    companion object {
        @JvmStatic
        fun with(mask: Int): PointerEvents = PointerEvents(mask)

        /** Configure the listener mask. */
        @JvmStatic
        fun with(configure: Consumer<Config>): PointerEvents {
            val config = Config()
            configure.accept(config)
            return PointerEvents(config.mask)
        }
    }
}
