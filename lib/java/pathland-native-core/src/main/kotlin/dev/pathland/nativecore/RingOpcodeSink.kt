package dev.pathland.nativecore

import com.sun.jna.Pointer
import dev.pathland.core.protocol.Categories
import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Opcode
import dev.pathland.core.protocol.TokenPath
import dev.pathland.core.protocol.ValueEncoder
import dev.pathland.core.protocol.ValueTypes
import dev.pathland.core.transport.OpcodeSink
import java.nio.charset.StandardCharsets

/** An [OpcodeSink] that writes opcodes zero-copy into the Rust SPSC ring via JNA. */
class RingOpcodeSink : OpcodeSink, AutoCloseable {
    private val core: PathlandCore
    private val handle: Pointer

    constructor() : this(PathlandCore.instance())

    constructor(core: PathlandCore) {
        this.core = core
        this.handle = core.create()
    }

    /** The opaque native handle (for direct ring access). */
    fun handle(): Pointer = handle

    override fun beginFrame() = core.beginFrame(handle)
    override fun endFrame() = core.endFrame(handle)

    override fun createNode(id: Int, component: Int) {
        push(Categories.TREE, Commands.Tree.CREATE_NODE, 0, id, component, 0)
    }

    override fun deleteNode(id: Int) {
        push(Categories.TREE, Commands.Tree.DELETE_NODE, 0, id, 0, 0)
    }

    override fun insertChild(parent: Int, child: Int, index: Int) {
        push(Categories.TREE, Commands.Tree.INSERT_CHILD, 0, parent, child, index)
    }

    override fun removeChild(parent: Int, child: Int) {
        push(Categories.TREE, Commands.Tree.REMOVE_CHILD, 0, parent, child, 0)
    }

    override fun moveChild(parent: Int, child: Int, newIndex: Int) {
        push(Categories.TREE, Commands.Tree.MOVE_CHILD, 0, parent, child, newIndex)
    }

    override fun setText(nodeId: Int, text: String) {
        val ref = core.arenaAlloc(handle, text.toByteArray(StandardCharsets.UTF_8))
        push(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, nodeId, ref, 0)
    }

    override fun setAttributedText(nodeId: Int, value: dev.pathland.core.text.AttributedString) {
        val ref = core.arenaAlloc(handle, value.encode())
        push(Categories.PARAMETER, Commands.Parameter.SET_ATTRIBUTED_TEXT, 0, nodeId, ref, 0)
    }

    override fun setProperty(nodeId: Int, property: Int, valueType: Int, value: Any) {
        val b = (valueType shl 16) or (property and 0xFFFF)
        when (valueType) {
            ValueTypes.STRING -> {
                val ref = core.arenaAlloc(handle, (value as String).toByteArray(StandardCharsets.UTF_8))
                push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, ref)
            }
            ValueTypes.DESIGN_TOKEN -> {
                val ref = core.arenaAlloc(handle,
                    ((value as TokenPath).tokenPath() ?: "").toByteArray(StandardCharsets.UTF_8))
                push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, ref)
            }
            ValueTypes.LIST -> {
                val ref = core.arenaAlloc(handle, encodeList(value as FloatArray))
                push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, ref)
            }
            else -> push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b,
                ValueEncoder.encodeBits(valueType, value))
        }
    }

    private fun encodeList(elements: FloatArray): ByteArray {
        val out = ByteArray(4 + 4 * elements.size)
        var value = elements.size
        out[0] = (value and 0xFF).toByte()
        out[1] = ((value ushr 8) and 0xFF).toByte()
        out[2] = ((value ushr 16) and 0xFF).toByte()
        out[3] = ((value ushr 24) and 0xFF).toByte()
        for (i in elements.indices) {
            val bits = elements[i].toRawBits()
            val o = 4 + 4 * i
            out[o] = (bits and 0xFF).toByte()
            out[o + 1] = ((bits ushr 8) and 0xFF).toByte()
            out[o + 2] = ((bits ushr 16) and 0xFF).toByte()
            out[o + 3] = ((bits ushr 24) and 0xFF).toByte()
        }
        return out
    }

    override fun setDate(nodeId: Int, days: Int, millisOfDay: Int) {
        push(Categories.PARAMETER, Commands.Parameter.SET_DATE, 0, nodeId, days, millisOfDay)
    }

    override fun setDesignToken(path: String, valueType: Int, value: Any) {
        val pathRef = core.arenaAlloc(handle, path.toByteArray(StandardCharsets.UTF_8))
        val c = if (valueType == ValueTypes.STRING) {
            core.arenaAlloc(handle, (value as String).toByteArray(StandardCharsets.UTF_8))
        } else {
            ValueEncoder.encodeBits(valueType, value)
        }
        push(Categories.PARAMETER, Commands.Parameter.SET_DESIGN_TOKEN, 0, pathRef, valueType, c)
    }

    private fun push(category: Int, command: Int, flags: Int, a: Int, b: Int, c: Int) {
        core.push(handle, Opcode(category, command, flags, a, b, c).toBytes())
    }

    override fun requiresBindingId(): Boolean = true

    override fun close() {
        core.destroy(handle)
    }

    override fun resetTheme() {
        push(Categories.META, Commands.Meta.RESET_THEME, 0, 0, 0, 0)
    }

    override fun setColorScheme(mode: Int) {
        push(Categories.META, Commands.Meta.SET_COLOR_SCHEME, 0, mode, 0, 0)
    }

    override fun setWindowMetadata(field: Int, value: String) {
        val ref = core.arenaAlloc(handle, value.toByteArray(StandardCharsets.UTF_8))
        push(Categories.META, Commands.Meta.SET_WINDOW_METADATA, 0, field, ref, 0)
    }
}
