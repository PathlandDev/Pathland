package dev.pathland.core.protocol;

/** Encodes typed property values into their raw 32-bit wire payload. */
public final class ValueEncoder {
    private ValueEncoder() {
    }

    /** Encode a typed value into its raw 32-bit wire payload for {@code SET_PROPERTY}. */
    public static int encodeBits(int valueType, Object value) {
        switch (valueType) {
            case ValueTypes.F32:
                return Float.floatToRawIntBits((Float) value);
            case ValueTypes.COLOR:
                return ((ColorValue) value).argb();
            case ValueTypes.U8:
            case ValueTypes.U32:
            case ValueTypes.ENUM:
                return ((Integer) value).intValue();
            default:
                throw new IllegalArgumentException("cannot encode value type " + valueType);
        }
    }
}
