package dev.pathland.demo.qt

import com.sun.jna.Memory
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.core.transport.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

/** Headless: decodes raw ring EVENT opcodes + event-arena strings, no Qt. */
class RingEventReaderTest {

    private fun opcode(command: Int, flags: Int, a: Int, b: Int, c: Int): ByteArray {
        val raw = ByteArray(OPCODE_SIZE)
        raw[0] = CATEGORY_EVENT.toByte()
        raw[1] = command.toByte()
        raw[2] = (flags and 0xFF).toByte()
        raw[3] = ((flags ushr 8) and 0xFF).toByte()
        putLe(raw, 4, a)
        putLe(raw, 8, b)
        putLe(raw, 12, c)
        return raw
    }

    private fun putLe(raw: ByteArray, off: Int, value: Int) {
        raw[off] = (value and 0xFF).toByte()
        raw[off + 1] = ((value ushr 8) and 0xFF).toByte()
        raw[off + 2] = ((value ushr 16) and 0xFF).toByte()
        raw[off + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val out = ByteArray(arrays.sumOf { it.size })
        var offset = 0
        for (array in arrays) {
            array.copyInto(out, offset)
            offset += array.size
        }
        return out
    }

    /** A fake shared ring: header + an event arena holding one string at a known ref. */
    private fun ringWithArena(arenaOffset: Int, text: String, textRef: Int): Memory {
        val ring = Memory(arenaOffset.toLong() + 64)
        ring.setInt(OFF_EVENT_ARENA_OFFSET.toLong(), arenaOffset)
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        ring.setInt((arenaOffset + textRef).toLong(), bytes.size)
        ring.write((arenaOffset + textRef + 4).toLong(), bytes, 0, bytes.size)
        return ring
    }

    private fun <T> withRing(
        arenaOffset: Int = HEADER_SIZE,
        text: String = "x",
        textRef: Int = 0,
        block: (RingEventReader) -> T,
    ): T {
        val ring = ringWithArena(arenaOffset, text, textRef)
        return try {
            block(RingEventReader(ring, ring.size()))
        } finally {
            ring.close()
        }
    }

    @Test
    fun decodesPointerValueAndDateEvents() = withRing(text = "typed text") { reader ->
        val raw = concat(
            opcode(
                Commands.Event.POINTER_UP,
                0,
                7,
                java.lang.Float.floatToRawIntBits(10.0f),
                java.lang.Float.floatToRawIntBits(20.0f),
            ),
            opcode(Commands.Event.VALUE_CHANGED, 0, 5, java.lang.Float.floatToRawIntBits(42.0f), 0),
            opcode(Commands.Event.DATE_CHANGED, 0, 4, 19000, 3600000),
        )

        val events = reader.decode(raw)
        assertEquals(3, events.size)

        val up = events[0]
        assertTrue(up.isPointerUp)
        assertEquals(7, up.target())
        assertEquals(10.0f, up.x())
        assertEquals(20.0f, up.y())

        val value = events[1]
        assertTrue(value.isValueChanged)
        assertEquals(5, value.target())
        assertEquals(42.0f, value.value())

        val date = events[2]
        assertTrue(date.isDateChanged)
        assertEquals(4, date.target())
        assertEquals(19000, date.days())
        assertEquals(3600000, date.millisOfDay())
    }

    @Test
    fun resolvesTextChangedFromTheEventArena() = withRing(text = "typed text") { reader ->
        val events = reader.decode(opcode(Commands.Event.TEXT_CHANGED, 0, 9, 0, 0))
        assertEquals(1, events.size)
        assertTrue(events[0].isTextChanged)
        assertEquals(9, events[0].target())
        assertEquals("typed text", events[0].text())
    }

    @Test
    fun distinguishesNavigateUrlFromBackRequest() = withRing(text = "/kitchen") { reader ->
        val url = reader.decode(opcode(Commands.Event.NAVIGATE, Commands.Flags.NAVIGATE_URL, 0, 0, 0))
        assertEquals(1, url.size)
        assertTrue(url[0].isNavigate)
        assertEquals("/kitchen", url[0].url())

        val back = reader.decode(opcode(Commands.Event.NAVIGATE, 0, 0, 0, 0))
        assertEquals(1, back.size)
        assertTrue(back[0].isNavigateBack)
        assertNull(back[0].url())
    }

    @Test
    fun decodesMediaEvents() = withRing { reader ->
        val raw = concat(
            opcode(Commands.Event.MEDIA_PLAY_STATE_CHANGED, 0, 61, 1, 0),
            opcode(
                Commands.Event.MEDIA_TIME_UPDATED,
                0,
                61,
                java.lang.Float.floatToRawIntBits(12.5f),
                0,
            ),
            opcode(Commands.Event.MEDIA_ENDED, 0, 61, 0, 0),
            opcode(
                Commands.Event.MEDIA_VOLUME_CHANGED,
                0,
                61,
                java.lang.Float.floatToRawIntBits(0.7f),
                0,
            ),
        )

        val events = reader.decode(raw)
        assertEquals(4, events.size)

        val play = events[0]
        assertTrue(play.isMediaPlayStateChanged)
        assertEquals(61, play.target())
        assertTrue(play.mediaPlaying())

        val time = events[1]
        assertTrue(time.isMediaTimeUpdated)
        assertEquals(61, time.target())
        assertEquals(12.5f, time.mediaValue())

        val ended = events[2]
        assertTrue(ended.isMediaEnded)
        assertEquals(61, ended.target())

        val volume = events[3]
        assertTrue(volume.isMediaVolumeChanged)
        assertEquals(61, volume.target())
        assertEquals(0.7f, volume.mediaValue())
    }

    @Test
    fun decodesFitChangedAndMediaDuration() = withRing { reader ->
        val raw = concat(
            opcode(Commands.Event.FIT_CHANGED, 0, 42, java.lang.Float.floatToRawIntBits(1.0f), 0),
            opcode(
                Commands.Event.MEDIA_DURATION_CHANGED,
                0,
                61,
                java.lang.Float.floatToRawIntBits(180.0f),
                0,
            ),
        )

        val events = reader.decode(raw)
        assertEquals(2, events.size)

        val fit = events[0]
        assertTrue(fit.isFitChanged)
        assertEquals(42, fit.target())
        assertEquals(1, fit.fitIndex())

        val duration = events[1]
        assertTrue(duration.isMediaDurationChanged)
        assertEquals(61, duration.target())
        assertEquals(180.0f, duration.mediaValue())
    }

    private companion object {
        const val CATEGORY_EVENT = 0x03
        const val OPCODE_SIZE = 16
        const val OFF_EVENT_ARENA_OFFSET = 0x40
        const val HEADER_SIZE = 0x50
    }
}
