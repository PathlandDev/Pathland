package dev.pathland.demo.music.player.controls

import dev.pathland.view.core.signal.Signals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The seek slider's seek-on-release contract. */
class SeekControlTest {

    @Test
    fun buffersDuringEditingAndCommitsOnRelease() {
        val position = Signals.signal(0f)
        val seekRequest = Signals.signal(0f)
        val seek = SeekControl(position, seekRequest)

        seek.onEditingChanged(true)
        seek.set(30f)
        seek.set(45f)
        assertEquals(0f, seekRequest.get(), "no seek command during the drag")
        assertEquals(0f, position.get(), "the display stays until the commit")

        seek.onEditingChanged(false)
        assertEquals(45f, seekRequest.get(), "the last drag position commits on release")
        assertEquals(45f, position.get(), "the display follows the commit")
    }

    @Test
    fun nonDragChangeCommitsImmediately() {
        val position = Signals.signal(0f)
        val seekRequest = Signals.signal(0f)
        val seek = SeekControl(position, seekRequest)

        seek.set(10f)
        assertEquals(10f, seekRequest.get(), "a non-drag change seeks immediately")
        assertEquals(10f, position.get())
    }
}
