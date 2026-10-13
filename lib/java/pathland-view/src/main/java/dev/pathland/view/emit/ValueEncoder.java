package dev.pathland.view.emit;

import dev.pathland.view.Color;
import dev.pathland.view.ValueTypes;

/** Encodes typed property values into their raw 32-bit wire payload. */
public final class ValueEncoder {

    private ValueEncoder() {}

    /** Encode a typed value into its raw 32-bit wire payload for {@code SET_PROPERTY}. */
    public static int encodeBits(int valueType, Object value) {
        return switch (valueType) {
            case ValueTypes.F32 -> Float.floatToRawIntBits((Float) value);
            case ValueTypes.COLOR -> ((Color) value).argb();
            case ValueTypes.U8, ValueTypes.U32, ValueTypes.ENUM -> (Integer) value;
            default -> throw new IllegalArgumentException("cannot encode value type " + valueType);
        };
    }
}