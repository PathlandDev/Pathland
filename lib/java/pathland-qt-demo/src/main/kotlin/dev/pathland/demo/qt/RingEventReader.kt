package dev.pathland.demo.qt

import com.sun.jna.Pointer
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.core.transport.Event
import java.nio.charset.StandardCharsets

/**
 * Decodes raw `EVENT`-category opcodes drained from the shared ring into
 * [Event] objects, resolving strings from the shared host → guest event arena.
 */
class RingEventReader(
    private val ringPtr: Pointer?,
    private val ringLen: Long,
) {

    private val eventArenaOffset: Int =
        if (ringPtr != null && ringLen >= OFF_EVENT_ARENA_OFFSET + 4L) {
            ringPtr.getInt(OFF_EVENT_ARENA_OFFSET.toLong())
        } else {
            0
        }

    /** Decode consecutive 16-byte opcodes from [raw] (a drain buffer). */
    fun decode(raw: ByteArray): List<Event> {
        val events = ArrayList<Event>()
        var off = 0
        while (off + OPCODE_SIZE <= raw.size) {
            if ((raw[off].toInt() and 0xFF) != CATEGORY_EVENT) {
                off += OPCODE_SIZE
                continue
            }
            val command = raw[off + 1].toInt() and 0xFF
            val flags = u16(raw, off + 2)
            val a = i32(raw, off + 4)
            val b = i32(raw, off + 8)
            val c = i32(raw, off + 12)
            decodeOne(command, flags, a, b, c)?.let(events::add)
            off += OPCODE_SIZE
        }
        return events
    }

    private fun decodeOne(command: Int, flags: Int, a: Int, b: Int, c: Int): Event? =
        when (command) {
            Commands.Event.POINTER_DOWN -> Event.pointerDown(a, f32(b), f32(c))
            Commands.Event.POINTER_UP -> Event.pointerUp(a, f32(b), f32(c))
            Commands.Event.POINTER_MOVE -> Event.pointerMove(a, f32(b), f32(c), flags)
            Commands.Event.KEY_DOWN -> Event.keyDown(a, b, c, flags)
            Commands.Event.KEY_UP -> Event.keyUp(a, b, c)
            Commands.Event.VALUE_CHANGED -> Event.valueChanged(a, f32(b))
            Commands.Event.TEXT_CHANGED -> Event.textChanged(a, arenaString(b))
            Commands.Event.FOCUS_CHANGED -> Event.focusChanged(a, b != 0)
            Commands.Event.EDITING_CHANGED -> Event.editingChanged(a, b != 0)
            Commands.Event.SUBMIT -> Event.submit(a)
            Commands.Event.SCROLL -> Event.scroll(a, f32(b), f32(c))
            Commands.Event.WHEEL -> Event.wheel(a, f32(b), f32(c))
            Commands.Event.DATE_CHANGED -> Event.dateChanged(a, b, c)
            Commands.Event.MEDIA_PLAY_STATE_CHANGED -> Event.mediaPlayStateChanged(a, b != 0)
            Commands.Event.MEDIA_TIME_UPDATED -> Event.mediaTimeUpdated(a, f32(b))
            Commands.Event.MEDIA_ENDED -> Event.mediaEnded(a)
            Commands.Event.MEDIA_VOLUME_CHANGED -> Event.mediaVolumeChanged(a, f32(b))
            Commands.Event.MEDIA_DURATION_CHANGED -> Event.mediaDurationChanged(a, f32(b))
            Commands.Event.FIT_CHANGED -> Event.fitChanged(a, f32(b))
            Commands.Event.NAVIGATE ->
                if ((flags and Commands.Flags.NAVIGATE_URL) != 0) {
                    Event.navigate(arenaString(b))
                } else {
                    Event.navigateBack()
                }
            else -> null
        }

    /** Resolve a string from the shared event arena at [ref] (`[u32 len][bytes]`). */
    private fun arenaString(ref: Int): String? {
        if (ref < 0 || ringPtr == null || eventArenaOffset <= 0) return null
        val base = eventArenaOffset.toLong() + ref
        if (base + 4 > ringLen) return null
        val len = ringPtr.getInt(base)
        if (len < 0 || base + 4 + len > ringLen) return null
        val bytes = ringPtr.getByteArray(base + 4, len)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private companion object {
        const val OPCODE_SIZE = 16
        const val CATEGORY_EVENT = 0x03
        const val OFF_EVENT_ARENA_OFFSET = 0x40

        fun u16(raw: ByteArray, off: Int): Int =
            (raw[off].toInt() and 0xFF) or ((raw[off + 1].toInt() and 0xFF) shl 8)

        fun i32(raw: ByteArray, off: Int): Int =
            (raw[off].toInt() and 0xFF) or
                ((raw[off + 1].toInt() and 0xFF) shl 8) or
                ((raw[off + 2].toInt() and 0xFF) shl 16) or
                ((raw[off + 3].toInt() and 0xFF) shl 24)

        fun f32(bits: Int): Float = java.lang.Float.intBitsToFloat(bits)
    }
}
