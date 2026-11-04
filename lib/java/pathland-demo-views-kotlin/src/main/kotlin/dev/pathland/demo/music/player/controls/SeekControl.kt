package dev.pathland.demo.music.player.controls

import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.WritableSignal
import java.util.function.UnaryOperator

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
