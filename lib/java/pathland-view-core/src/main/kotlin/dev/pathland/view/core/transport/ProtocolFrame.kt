package dev.pathland.view.core.transport

import dev.pathland.view.core.protocol.Opcode
import java.nio.charset.StandardCharsets

/**
 * A self-contained frame: 16-byte opcodes plus its own string section. STRING
 * payloads reference strings by a relative offset, so a frame decodes with no
 * prior state.
 */
data class ProtocolFrame(
    val opcodes: List<Opcode>,
    val strings: ByteArray,
) {
    fun opcodes(): List<Opcode> = opcodes
    fun strings(): ByteArray = strings

    fun isEmpty(): Boolean = opcodes.isEmpty()

    /** Read a length-prefixed string at a relative offset in the string section. */
    fun stringAt(offset: Int): String {
        val len = readIntLE(strings, offset)
        return String(strings, offset + 4, len, StandardCharsets.UTF_8)
    }

    companion object {
        @JvmField
        val EMPTY = ProtocolFrame(emptyList(), ByteArray(0))

        private fun readIntLE(bytes: ByteArray, offset: Int): Int =
            (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
