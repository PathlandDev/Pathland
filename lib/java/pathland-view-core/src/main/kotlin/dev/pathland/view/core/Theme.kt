package dev.pathland.view.core

import dev.pathland.core.protocol.ValueTypes
import dev.pathland.core.transport.OpcodeSink

/** A batch of global design-token overrides for a single scheme. */
class Theme : ThemeData {

    /** A single token override (path, wire value type, typed value). */
    data class TokenOverride(
        val path: String,
        val valueType: Int,
        val value: Any,
    ) {
        fun path(): String = path
        fun valueType(): Int = valueType
        fun value(): Any = value
    }

    private val overrides = ArrayList<TokenOverride>()

    fun color(path: String, argb: Int): Theme = push(path, ValueTypes.COLOR, Color.of(argb))
    fun f32(path: String, value: Float): Theme = push(path, ValueTypes.F32, value)
    fun u32(path: String, value: Int): Theme = push(path, ValueTypes.U32, value)
    fun u8(path: String, value: Int): Theme = push(path, ValueTypes.U8, value)
    fun string(path: String, value: String): Theme = push(path, ValueTypes.STRING, value)

    private fun push(path: String, valueType: Int, value: Any): Theme {
        overrides.add(TokenOverride(path, valueType, value))
        return this
    }

    internal fun overrides(): List<TokenOverride> = overrides

    override fun emitInto(sink: OpcodeSink) {
        for (o in overrides) {
            sink.setDesignToken(o.path, o.valueType, o.value)
        }
    }
}
