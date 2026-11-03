package dev.pathland.view.core

import java.util.function.Consumer

/** Attaches a tap gesture (`onTapGesture`). */
class TapGesture private constructor(private val action: Runnable?) : ViewModifier {

    class Config {
        var action: Runnable? = null

        fun action(action: Runnable): Config {
            this.action = action
            return this
        }
    }

    override fun body(content: View): View = TapGestureView(content, action)

    companion object {
        @JvmStatic
        fun with(action: Runnable): TapGesture = TapGesture(action)

        /** Configure the tap gesture. */
        @JvmStatic
        fun with(configure: Consumer<Config>): TapGesture {
            val config = Config()
            configure.accept(config)
            return TapGesture(config.action)
        }
    }
}
