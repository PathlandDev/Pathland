package dev.pathland.demo.music.player.controls

import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.WritableSignal
import java.util.function.UnaryOperator
import kotlin.math.max
import kotlin.math.min

/**
 * A [WritableSignal] that presents the player's position (stored in seconds, as
 * the wire carries it) as a 0..100 progress percent for the seek bar.
 */
internal class PercentSeek(
    private val seconds: WritableSignal<Float>,
    private val duration: () -> Float,
) : WritableSignal<Float> {

    override fun get(): Float {
        val d = max(duration(), 1f)
        return clampPercent(seconds.get() / d * 100f)
    }

    override fun set(percent: Float) {
        val d = max(duration(), 1f)
        seconds.set(clampPercent(percent) / 100f * d)
    }

    override fun update(fn: UnaryOperator<Float>) = set(fn.apply(get()))

    override fun asReadonly(): Signal<Float> = Signal { get() }

    private fun clampPercent(p: Float): Float = max(0f, min(100f, p))
}
