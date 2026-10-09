package com.pathland.view.emit;

import com.pathland.view.ValueTypes;
import com.pathland.view.WireValue;
import com.pathland.view.signal.Signal;

/**
 * Converts a reactive signal's value into the typed property value stored on a
 * {@link PathlandNode}, coerced to the property's declared wire value type
 * ({@link ValueTypes#forProperty}). Booleans become raw {@code 1}/{@code 0};
 * wire-typed enums ({@link WireValue}) become their numeric code; numeric
 * values are coerced to {@code F32} or an integer type as the property requires.
 */
public final class ReactiveValues {

    private ReactiveValues() {}

    /** Read the signal and normalize its value for storage as a node property. */
    public static Object from(int property, Signal<?> signal) {
        Object value = signal.get();
        if (value instanceof Boolean b) {
            value = b ? 1 : 0;
        } else if (value instanceof WireValue w) {
            value = w.wire();
        }
        return coerce(property, value);
    }

    private static Object coerce(int property, Object value) {
        return switch (ValueTypes.forProperty(property)) {
            case ValueTypes.F32 -> toFloat(property, value);
            case ValueTypes.U8, ValueTypes.U32, ValueTypes.I32, ValueTypes.ENUM -> toInt(property, value);
            case ValueTypes.COLOR, ValueTypes.STRING, ValueTypes.DESIGN_TOKEN -> value;
            default -> value;
        };
    }

    private static Float toFloat(int property, Object value) {
        if (value instanceof Float f) {
            return f;
        }
        if (value instanceof Integer i) {
            return (float) i;
        }
        if (value instanceof Double d) {
            return d.floatValue();
        }
        throw unsupported(property, value);
    }

    private static Integer toInt(int property, Object value) {
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Float f) {
            return f.intValue();
        }
        throw unsupported(property, value);
    }

    private static IllegalArgumentException unsupported(int property, Object value) {
        return new IllegalArgumentException(
                "Unsupported reactive property value " + value + " for property 0x"
                        + Integer.toHexString(property));
    }
}
