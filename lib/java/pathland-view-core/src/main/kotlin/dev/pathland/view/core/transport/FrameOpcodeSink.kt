package dev.pathland.view.core.transport

import dev.pathland.view.core.protocol.Categories
import dev.pathland.view.core.protocol.Commands
import dev.pathland.view.core.protocol.Opcode
import dev.pathland.view.core.protocol.TokenPath
import dev.pathland.view.core.protocol.ValueEncoder
import dev.pathland.view.core.protocol.ValueTypes
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** An [OpcodeSink] that accumulates a self-contained [ProtocolFrame]. */
open class FrameOpcodeSink : OpcodeSink {
    private val opcodes = ArrayList<Opcode>()
    private val strings = ByteArrayOutputStream()
    private var frame: ProtocolFrame = ProtocolFrame.EMPTY
    private var framesProduced = 0

    fun frame(): ProtocolFrame = frame
    fun framesProduced(): Int = framesProduced
    fun hasChanges(): Boolean = opcodes.isNotEmpty()

    override fun beginFrame() {
        opcodes.clear()
        strings.reset()
    }

    override fun endFrame() {
        frame = ProtocolFrame(opcodes.toList(), strings.toByteArray())
        framesProduced++
    }

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
        val offset = strings.size()
        writeString(text)
        push(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, nodeId, offset, 0)
    }

    override fun setProperty(nodeId: Int, property: Int, valueType: Int, value: Any) {
        val b = (valueType shl 16) or (property and 0xFFFF)
        when (valueType) {
            ValueTypes.STRING -> {
                val offset = strings.size()
                writeString(value as String)
                push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, offset)
            }
            ValueTypes.DESIGN_TOKEN -> {
                val offset = strings.size()
                writeString((value as TokenPath).tokenPath() ?: "")
                push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, offset)
            }
            ValueTypes.LIST -> {
                val offset = strings.size()
                val elements = value as FloatArray
                writeIntLE(elements.size)
                for (element in elements) {
                    val bits = element.toRawBits()
                    strings.write(bits and 0xFF)
                    strings.write((bits ushr 8) and 0xFF)
                    strings.write((bits ushr 16) and 0xFF)
                    strings.write((bits ushr 24) and 0xFF)
                }
                push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b, offset)
            }
            else -> push(Categories.PARAMETER, Commands.Parameter.SET_PROPERTY, 0, nodeId, b,
                ValueEncoder.encodeBits(valueType, value))
        }
    }

    override fun setDate(nodeId: Int, days: Int, millisOfDay: Int) {
        push(Categories.PARAMETER, Commands.Parameter.SET_DATE, 0, nodeId, days, millisOfDay)
    }

    override fun setDesignToken(path: String, valueType: Int, value: Any) {
        val pathOffset = strings.size()
        writeString(path)
        val c = if (valueType == ValueTypes.STRING) {
            val offset = strings.size()
            writeString(value as String)
            offset
        } else {
            ValueEncoder.encodeBits(valueType, value)
        }
        push(Categories.PARAMETER, Commands.Parameter.SET_DESIGN_TOKEN, 0, pathOffset, valueType, c)
    }

    fun writeString(text: String): Int {
        val offset = strings.size()
        val utf8 = text.toByteArray(StandardCharsets.UTF_8)
        writeIntLE(utf8.size)
        strings.writeBytes(utf8)
        return offset
    }

    private fun push(category: Int, command: Int, flags: Int, a: Int, b: Int, c: Int) {
        opcodes.add(Opcode(category, command, flags, a, b, c))
    }

    private fun writeIntLE(value: Int) {
        strings.write(value and 0xFF)
        strings.write((value ushr 8) and 0xFF)
        strings.write((value ushr 16) and 0xFF)
        strings.write((value ushr 24) and 0xFF)
    }

    override fun resetTheme() {
        push(Categories.META, Commands.Meta.RESET_THEME, 0, 0, 0, 0)
    }

    override fun setColorScheme(mode: Int) {
        push(Categories.META, Commands.Meta.SET_COLOR_SCHEME, 0, mode, 0, 0)
    }

    override fun setWindowMetadata(field: Int, value: String) {
        val offset = strings.size()
        writeString(value)
        push(Categories.META, Commands.Meta.SET_WINDOW_METADATA, 0, field, offset, 0)
    }
}
