package dev.pathland.view.engine

import dev.pathland.view.core.protocol.ValueTypes
import dev.pathland.view.core.protocol.WireValue
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.Signals

/** Converts a reactive signal's value into the typed property value stored on a node. */
object ReactiveValues {
    /** Read the signal for an initial render value without subscribing the component. */
    fun initial(property: Int, signal: Signal<*>): Any? =
        Signals.untracked { from(property, signal) }

    /** Read the signal and normalize its value for storage as a node property. */
    fun from(property: Int, signal: Signal<*>): Any? {
        var value: Any? = signal.get()
        if (value is Boolean) {
            value = if (value) 1 else 0
        } else if (value is WireValue) {
            value = value.wire()
        }
        return coerce(property, value)
    }

    private fun coerce(property: Int, value: Any?): Any? =
        when (ValueTypes.forProperty(property)) {
            ValueTypes.F32 -> toFloat(property, value)
            ValueTypes.U8, ValueTypes.U32, ValueTypes.I32, ValueTypes.ENUM -> toInt(property, value)
            ValueTypes.COLOR, ValueTypes.STRING, ValueTypes.DESIGN_TOKEN -> value
            else -> value
        }

    private fun toFloat(property: Int, value: Any?): Float = when (value) {
        is Float -> value
        is Int -> value.toFloat()
        is Double -> value.toFloat()
        else -> throw unsupported(property, value)
    }

    private fun toInt(property: Int, value: Any?): Int = when (value) {
        is Int -> value
        is Float -> value.toInt()
        else -> throw unsupported(property, value)
    }

    private fun unsupported(property: Int, value: Any?): IllegalArgumentException =
        IllegalArgumentException(
            "Unsupported reactive property value $value for property 0x${Integer.toHexString(property)}",
        )
}
