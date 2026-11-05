package dev.pathland.view.core.protocol

/**
 * A fixed-size 16-byte Pathland opcode (little-endian):
 *
 * `[Category u8][Command u8][Flags u16][A u32][B u32][C u32]`
 *
 * The component-named methods preserve the Java record accessor API
 * (`op.category()`, `op.b()`, …) used by the renderers, hosts, and tests.
 */
data class Opcode(
    val category: Int,
    val command: Int,
    val flags: Int,
    val a: Int,
    val b: Int,
    val c: Int,
) {
    fun category(): Int = category
    fun command(): Int = command
    fun flags(): Int = flags
    fun a(): Int = a
    fun b(): Int = b
    fun c(): Int = c

    /** Serialize to the 16-byte little-endian wire representation. */
    fun toBytes(): ByteArray {
        val out = ByteArray(SIZE)
        out[0] = category.toByte()
        out[1] = command.toByte()
        out[2] = flags.toByte()
        out[3] = (flags ushr 8).toByte()
        writeIntLE(out, 4, a)
        writeIntLE(out, 8, b)
        writeIntLE(out, 12, c)
        return out
    }

    companion object {
        const val SIZE = 16

        /** Deserialize from the 16-byte little-endian wire representation. */
        fun fromBytes(bytes: ByteArray): Opcode {
            require(bytes.size == SIZE) { "opcode must be $SIZE bytes" }
            return Opcode(
                bytes[0].toInt() and 0xFF,
                bytes[1].toInt() and 0xFF,
                (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8),
                readIntLE(bytes, 4),
                readIntLE(bytes, 8),
                readIntLE(bytes, 12),
            )
        }

        private fun writeIntLE(out: ByteArray, offset: Int, value: Int) {
            out[offset] = value.toByte()
            out[offset + 1] = (value ushr 8).toByte()
            out[offset + 2] = (value ushr 16).toByte()
            out[offset + 3] = (value ushr 24).toByte()
        }

        private fun readIntLE(bytes: ByteArray, offset: Int): Int =
            (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
