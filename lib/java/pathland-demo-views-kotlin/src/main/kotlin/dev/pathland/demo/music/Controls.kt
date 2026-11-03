package dev.pathland.demo.music

import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.WritableSignal
import java.util.function.UnaryOperator
import kotlin.math.max
import kotlin.math.min

/**
 * A [WritableSignal] (seconds) for the seek slider that presents the playback
 * position but on a user drag writes the seek command signal bound to
 * `MEDIA_POSITION` (and the display, for immediate thumb feedback).
 *
 * Seek-on-release: during a drag session values are buffered; the command is
 * committed once when the drag ends.
 */
internal class SeekControl(
    private val position: WritableSignal<Float>,
    private val seekRequest: WritableSignal<Float>,
) : WritableSignal<Float> {

    private var editing = false
    private var pendingSeek = 0f

    override fun get(): Float = position.get()

    override fun set(value: Float) {
        pendingSeek = value
        if (!editing) commit()
    }

    /** `EDITING_CHANGED` boundary: a drag session began (true) / ended (false). */
    fun onEditingChanged(editing: Boolean) {
        this.editing = editing
        if (!editing) commit()
    }

    private fun commit() {
        position.set(pendingSeek)
        seekRequest.set(pendingSeek)
    }

    override fun update(fn: UnaryOperator<Float>) = set(fn.apply(get()))

    override fun asReadonly(): Signal<Float> = Signal { get() }
}

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

/**
 * A [WritableSignal] (0..1) for the volume slider that presents the current
 * volume but on a user drag writes the volume command signal bound to
 * `MEDIA_VOLUME` (and the display, for immediate feedback).
 */
internal class VolumeControl(
    private val volume: WritableSignal<Float>,
    private val volumeRequest: WritableSignal<Float>,
) : WritableSignal<Float> {

    override fun get(): Float = volume.get()

    override fun set(value: Float) {
        volume.set(value)
        volumeRequest.set(value)
    }

    override fun update(fn: UnaryOperator<Float>) = set(fn.apply(get()))

    override fun asReadonly(): Signal<Float> = Signal { get() }
}
