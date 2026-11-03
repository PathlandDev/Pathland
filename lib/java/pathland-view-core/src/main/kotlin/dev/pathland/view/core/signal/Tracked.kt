package dev.pathland.view.core.signal

/**
 * A synchronous tracked computation: the supplier runs immediately, records the
 * signals it reads, and re-runs on change, handing the value to the consumer.
 */
class Tracked<T> internal constructor(
    private val initial: T,
    private val effect: EffectRef,
) : EffectRef {
    /** The value produced by the synchronous first run. */
    fun initial(): T = initial

    override fun destroy() {
        effect.destroy()
    }
}
