package dev.pathland.demo.music.player.controls

import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.WritableSignal
import java.util.function.UnaryOperator

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
