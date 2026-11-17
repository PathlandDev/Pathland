package dev.pathland.view.core

import java.util.function.Consumer

/**
 * ORs raw input listener bits into the wrapped node's `EVENT_LISTENERS`.
 *
 * Bits request pointer/key/focus/editing/scroll/wheel/visible-range events
 * (spec/EVENTS.md). Listeners do not change layout or visuals; the renderer
 * reports the events through the event ring or network batch.
 */
class PointerEvents private constructor(private val mask: Int) : ViewModifier {

    /** Raw input listener mask configuration (`EVENT_LISTENERS` bits). */
    class Config {
        var mask: Int = 0

        fun mask(mask: Int): Config {
            this.mask = mask
            return this
        }
    }

    override fun body(content: View): View = PointerEventsView(content, mask)

    companion object {
        fun with(mask: Int): PointerEvents = PointerEvents(mask)

        /** Configure the listener mask. */
        fun with(configure: Consumer<Config>): PointerEvents {
            val config = Config()
            configure.accept(config)
            return PointerEvents(config.mask)
        }
    }
}
