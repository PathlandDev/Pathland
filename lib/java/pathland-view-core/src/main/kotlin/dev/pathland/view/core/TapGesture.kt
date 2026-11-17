package dev.pathland.view.core

import java.util.function.Consumer

/**
 * Attaches an app-side tap recognizer to the wrapped view.
 *
 * It declares `POINTER_DOWN`/`POINTER_UP` listeners and registers a `Runnable`
 * with the emitter's tap-action registry. Tap is composed app-side from raw
 * pointer events; it is not a protocol command.
 */
class TapGesture private constructor(private val action: Runnable?) : ViewModifier {

    /** The action to invoke when the composed tap is recognized. */
    class Config {
        var action: Runnable? = null

        fun action(action: Runnable): Config {
            this.action = action
            return this
        }
    }

    override fun body(content: View): View = TapGestureView(content, action)

    companion object {
        fun with(action: Runnable): TapGesture = TapGesture(action)

        /** Configure the tap gesture. */
        fun with(configure: Consumer<Config>): TapGesture {
            val config = Config()
            configure.accept(config)
            return TapGesture(config.action)
        }
    }
}
