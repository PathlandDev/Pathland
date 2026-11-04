package dev.pathland.view.core.protocol

import dev.pathland.view.core.Color

/** Encodes typed property values into their raw 32-bit wire payload. */
object ValueEncoder {
    /** Encode a typed value into its raw 32-bit wire payload for `SET_PROPERTY`. */
    fun encodeBits(valueType: Int, value: Any): Int = when (valueType) {
        ValueTypes.F32 -> (value as Float).toRawBits()
        ValueTypes.COLOR -> (value as Color).argb()
        ValueTypes.U8, ValueTypes.U32, ValueTypes.ENUM -> value as Int
        else -> throw IllegalArgumentException("cannot encode value type $valueType")
    }
}
