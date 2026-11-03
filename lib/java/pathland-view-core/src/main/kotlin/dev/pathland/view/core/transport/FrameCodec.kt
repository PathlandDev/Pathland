package dev.pathland.view.core.transport

import dev.pathland.view.core.protocol.Categories
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.core.protocol.Opcode
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** The network batch codec — mirrors `pathland_core_transport`. */
object FrameCodec {
    const val MAGIC = 0x504C_504C
    const val VERSION = 1
    const val GUEST_TO_HOST = 0x0000
    const val HOST_TO_GUEST = 0x0001
    const val HEADER = 16

    /** Encode a self-contained frame as a network batch (sequence 0). */
    @JvmStatic
    fun encodeFrame(frame: ProtocolFrame): ByteArray = encodeFrame(frame, 0)

    /** Encode a self-contained frame as a guest → host batch with [sequence]. */
    @JvmStatic
    fun encodeFrame(frame: ProtocolFrame, sequence: Int): ByteArray {
        val out = ByteArrayOutputStream()
        writeHeader(out, GUEST_TO_HOST, sequence, frame.opcodes.size)
        for (op in frame.opcodes) {
            writeBytes(out, op.toBytes())
        }
        writeIntLE(out, frame.strings.size)
        writeBytes(out, frame.strings)
        return out.toByteArray()
    }

    /** Decode a self-contained frame batch into opcodes + string section. */
    @JvmStatic
    fun decodeFrame(bytes: ByteArray): ProtocolFrame {
        val parsed = parse(bytes)
        return ProtocolFrame(parsed.opcodes, parsed.strings)
    }

    /** The per-stream message sequence encoded in a batch header (offset 8). */
    @JvmStatic
    fun sequence(bytes: ByteArray): Int {
        if (bytes.size < HEADER) throw IllegalArgumentException("batch truncated")
        return readIntLE(bytes, 8)
    }

    /** Encode a `META::RESYNC` request batch (host → guest). */
    @JvmStatic
    fun encodeResync(): ByteArray = singleMeta(Commands.Meta.RESYNC)

    /** Encode a `META::PONG` reply batch (host → guest). */
    @JvmStatic
    fun encodePong(): ByteArray = singleMeta(Commands.Meta.PONG)

    /** Encode a `META::PING` heartbeat probe batch (guest → host). */
    @JvmStatic
    fun encodePing(): ByteArray {
        val out = ByteArrayOutputStream()
        writeHeader(out, GUEST_TO_HOST, 0, 1)
        writeBytes(out, Opcode(Categories.META, Commands.Meta.PING, 0, 0, 0, 0).toBytes())
        writeIntLE(out, 0)
        return out.toByteArray()
    }

    private fun singleMeta(command: Int): ByteArray {
        val out = ByteArrayOutputStream()
        writeHeader(out, HOST_TO_GUEST, 0, 1)
        writeBytes(out, Opcode(Categories.META, command, 0, 0, 0, 0).toBytes())
        writeIntLE(out, 0)
        return out.toByteArray()
    }

    /** Encode a `META::ENVIRONMENT` field batch (viewport + route). */
    @JvmStatic
    fun encodeEnvironment(env: EnvironmentData): ByteArray {
        val route = env.route.toByteArray(StandardCharsets.UTF_8)
        val ops = ArrayList<Opcode>(3)
        if (env.viewportWidth > 0) {
            ops.add(Opcode(Categories.META, Commands.Meta.ENVIRONMENT, 0,
                Commands.Environment.VIEWPORT_WIDTH, env.viewportWidth.toRawBits(), 0))
        }
        if (env.viewportHeight > 0) {
            ops.add(Opcode(Categories.META, Commands.Meta.ENVIRONMENT, 0,
                Commands.Environment.VIEWPORT_HEIGHT, env.viewportHeight.toRawBits(), 0))
        }
        ops.add(Opcode(Categories.META, Commands.Meta.ENVIRONMENT, 0,
            Commands.Environment.ROUTE, 0, 0))
        val out = ByteArrayOutputStream()
        writeHeader(out, HOST_TO_GUEST, 0, ops.size)
        for (op in ops) {
            writeBytes(out, op.toBytes())
        }
        writeIntLE(out, 4 + route.size)
        writeIntLE(out, route.size)
        out.writeBytes(route)
        return out.toByteArray()
    }

    /** True when the batch is a `META::RESYNC` request. */
    @JvmStatic
    fun isResync(bytes: ByteArray): Boolean =
        parse(bytes).opcodes.any { it.category == Categories.META && it.command == Commands.Meta.RESYNC }

    /** True when the batch carries a `META::PING` heartbeat probe. */
    @JvmStatic
    fun isPing(bytes: ByteArray): Boolean =
        parse(bytes).opcodes.any { it.category == Categories.META && it.command == Commands.Meta.PING }

    /** True when the batch carries `META::ENVIRONMENT` fields. */
    @JvmStatic
    fun isEnvironment(bytes: ByteArray): Boolean =
        parse(bytes).opcodes.any { it.category == Categories.META && it.command == Commands.Meta.ENVIRONMENT }

    /** Decode a `META::ENVIRONMENT` field batch into [EnvironmentData]. */
    @JvmStatic
    fun decodeEnvironment(bytes: ByteArray): EnvironmentData {
        val parsed = parse(bytes)
        var route: String? = null
        var width = -1f
        var height = -1f
        for (op in parsed.opcodes) {
            if (op.category != Categories.META || op.command != Commands.Meta.ENVIRONMENT) continue
            when (op.a and 0xFFFF) {
                Commands.Environment.VIEWPORT_WIDTH -> width = Float.fromBits(op.b)
                Commands.Environment.VIEWPORT_HEIGHT -> height = Float.fromBits(op.b)
                Commands.Environment.ROUTE -> route = parsed.stringAt(op.b)
            }
        }
        return EnvironmentData(route, width, height)
    }

    /** Encode host → guest events as a batch. */
    @JvmStatic
    fun encodeEvents(events: List<Event>): ByteArray {
        val opcodes = ArrayList<Opcode>(events.size)
        val strings = ByteArrayOutputStream()
        for (event in events) {
            when {
                event.isTextChanged -> {
                    val offset = strings.size()
                    val utf8 = event.text.orEmpty().toByteArray(StandardCharsets.UTF_8)
                    writeIntLE(strings, utf8.size)
                    strings.writeBytes(utf8)
                    opcodes.add(Opcode(
                        Categories.EVENT, Commands.Event.TEXT_CHANGED, 0, event.target, offset, 0))
                }
                event.isNavigate -> {
                    if (event.isNavigateBack) {
                        opcodes.add(Opcode(Categories.EVENT, Commands.Event.NAVIGATE, 0, 0, 0, 0))
                    } else {
                        val offset = strings.size()
                        val utf8 = event.url().orEmpty().toByteArray(StandardCharsets.UTF_8)
                        writeIntLE(strings, utf8.size)
                        strings.writeBytes(utf8)
                        opcodes.add(Opcode(
                            Categories.EVENT, Commands.Event.NAVIGATE, Commands.Flags.NAVIGATE_URL, 0, offset, 0))
                    }
                }
                event.isValueChanged -> opcodes.add(Opcode(
                    Categories.EVENT, Commands.Event.VALUE_CHANGED, 0,
                    event.target, event.value.toRawBits(), 0))
                event.isKeyDown || event.isKeyUp -> opcodes.add(Opcode(
                    Categories.EVENT, event.command, event.flags, event.target, event.b, event.c))
                event.isDateChanged -> opcodes.add(Opcode(
                    Categories.EVENT, Commands.Event.DATE_CHANGED, 0, event.target, event.b, event.c))
                event.isFocusChanged || event.isEditingChanged || event.isSubmit -> opcodes.add(Opcode(
                    Categories.EVENT, event.command, 0, event.target, event.b, 0))
                event.isMediaTimeUpdated || event.isMediaVolumeChanged ||
                    event.isMediaDurationChanged || event.isFitChanged -> opcodes.add(Opcode(
                    Categories.EVENT, event.command, 0, event.target, event.value.toRawBits(), 0))
                event.isMediaPlayStateChanged || event.isMediaEnded -> opcodes.add(Opcode(
                    Categories.EVENT, event.command, 0, event.target, event.b, 0))
                else -> opcodes.add(Opcode(
                    Categories.EVENT, event.command, event.flags, event.target,
                    event.x.toRawBits(), event.y.toRawBits()))
            }
        }
        val out = ByteArrayOutputStream()
        writeHeader(out, HOST_TO_GUEST, 0, opcodes.size)
        for (op in opcodes) {
            writeBytes(out, op.toBytes())
        }
        writeIntLE(out, strings.size())
        writeBytes(out, strings.toByteArray())
        return out.toByteArray()
    }

    /** Decode a host → guest event batch into typed events. */
    @JvmStatic
    fun decodeEvents(bytes: ByteArray): List<Event> {
        val parsed = parse(bytes)
        val events = ArrayList<Event>()
        for (op in parsed.opcodes) {
            if (op.category != Categories.EVENT) continue
            when (op.command) {
                Commands.Event.TEXT_CHANGED -> events.add(Event.textChanged(op.a, parsed.stringAt(op.b)))
                Commands.Event.NAVIGATE -> events.add(
                    if ((op.flags and Commands.Flags.NAVIGATE_URL) != 0) {
                        Event.navigate(parsed.stringAt(op.b))
                    } else {
                        Event.navigateBack()
                    })
                Commands.Event.VALUE_CHANGED -> events.add(Event.valueChanged(op.a, Float.fromBits(op.b)))
                Commands.Event.KEY_DOWN -> events.add(Event.keyDown(op.a, op.b, op.c, op.flags))
                Commands.Event.KEY_UP -> events.add(Event.keyUp(op.a, op.b, op.c))
                Commands.Event.DATE_CHANGED -> events.add(Event.dateChanged(op.a, op.b, op.c))
                Commands.Event.FOCUS_CHANGED -> events.add(Event.focusChanged(op.a, op.b != 0))
                Commands.Event.EDITING_CHANGED -> events.add(Event.editingChanged(op.a, op.b != 0))
                Commands.Event.SUBMIT -> events.add(Event.submit(op.a))
                Commands.Event.SCROLL -> events.add(Event.scroll(
                    op.a, Float.fromBits(op.b), Float.fromBits(op.c)))
                Commands.Event.WHEEL -> events.add(Event.wheel(
                    op.a, Float.fromBits(op.b), Float.fromBits(op.c)))
                Commands.Event.MEDIA_PLAY_STATE_CHANGED -> events.add(
                    Event.mediaPlayStateChanged(op.a, op.b != 0))
                Commands.Event.MEDIA_TIME_UPDATED -> events.add(
                    Event.mediaTimeUpdated(op.a, Float.fromBits(op.b)))
                Commands.Event.MEDIA_ENDED -> events.add(Event.mediaEnded(op.a))
                Commands.Event.MEDIA_VOLUME_CHANGED -> events.add(
                    Event.mediaVolumeChanged(op.a, Float.fromBits(op.b)))
                Commands.Event.MEDIA_DURATION_CHANGED -> events.add(
                    Event.mediaDurationChanged(op.a, Float.fromBits(op.b)))
                Commands.Event.FIT_CHANGED -> events.add(
                    Event.fitChanged(op.a, Float.fromBits(op.b)))
                Commands.Event.POINTER_DOWN, Commands.Event.POINTER_MOVE, Commands.Event.POINTER_UP ->
                    events.add(Event(op.command, op.a, op.flags, Float.fromBits(op.b),
                        Float.fromBits(op.c), 0f, null, 0, 0))
            }
        }
        return events
    }

    private class Parsed(val opcodes: List<Opcode>, val strings: ByteArray) {
        fun stringAt(offset: Int): String {
            val len = (strings[offset].toInt() and 0xFF) or
                ((strings[offset + 1].toInt() and 0xFF) shl 8) or
                ((strings[offset + 2].toInt() and 0xFF) shl 16) or
                ((strings[offset + 3].toInt() and 0xFF) shl 24)
            return String(strings, offset + 4, len, StandardCharsets.UTF_8)
        }
    }

    private fun parse(bytes: ByteArray): Parsed {
        if (bytes.size < HEADER) throw IllegalArgumentException("batch truncated")
        val magic = readIntLE(bytes, 0)
        if (magic != MAGIC) {
            throw IllegalArgumentException("bad batch magic 0x${Integer.toHexString(magic)}")
        }
        val version = (bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8)
        if (version != VERSION) {
            throw IllegalArgumentException("unsupported batch version $version")
        }
        val opcodeCount = readIntLE(bytes, 12)
        val opcodesEnd = HEADER + opcodeCount * Opcode.SIZE
        if (opcodesEnd + 4 > bytes.size) {
            throw IllegalArgumentException("batch truncated (opcodes)")
        }
        val opcodes = ArrayList<Opcode>(opcodeCount)
        for (i in 0 until opcodeCount) {
            val slot = ByteArray(Opcode.SIZE)
            System.arraycopy(bytes, HEADER + i * Opcode.SIZE, slot, 0, Opcode.SIZE)
            opcodes.add(Opcode.fromBytes(slot))
        }
        val stringsLen = readIntLE(bytes, opcodesEnd)
        val stringsStart = opcodesEnd + 4
        if (stringsStart + stringsLen > bytes.size) {
            throw IllegalArgumentException("batch truncated (strings)")
        }
        val strings = ByteArray(stringsLen)
        System.arraycopy(bytes, stringsStart, strings, 0, stringsLen)
        return Parsed(opcodes, strings)
    }

    private fun writeHeader(out: ByteArrayOutputStream, flags: Int, sequence: Int, opcodeCount: Int) {
        writeIntLE(out, MAGIC)
        writeShortLE(out, VERSION)
        writeShortLE(out, flags)
        writeIntLE(out, sequence)
        writeIntLE(out, opcodeCount)
    }

    private fun writeIntLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun writeBytes(out: ByteArrayOutputStream, bytes: ByteArray) {
        out.writeBytes(bytes)
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
