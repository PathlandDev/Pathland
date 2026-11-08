package dev.pathland.core.text;

import dev.pathland.core.protocol.Properties;
import dev.pathland.core.protocol.ValueTypes;

import java.util.Objects;

/**
 * A scalar text-format attribute on a range.
 *
 * <p>{@code value} is the standard 4-byte wire value (an {@code F32} bit pattern
 * for float attributes, a packed {@code 0xAARRGGBB} for colors, {@code 0}/{@code 1}
 * for booleans).
 */
public final class TextAttribute {
    private final int property;
    private final int valueType;
    private final int value;

    public TextAttribute(int property, int valueType, int value) {
        this.property = property;
        this.valueType = valueType;
        this.value = value;
    }

    public TextAttribute(int property, int value) {
        this(property, ValueTypes.forProperty(property), value);
    }

    public int property() {
        return property;
    }

    public int valueType() {
        return valueType;
    }

    public int value() {
        return value;
    }

    public int getProperty() {
        return property;
    }

    public int getValueType() {
        return valueType;
    }

    public int getValue() {
        return value;
    }

    public static TextAttribute fontSize(float points) {
        return new TextAttribute(Properties.FONT_SIZE, ValueTypes.F32,
                Float.floatToRawIntBits(points));
    }

    public static TextAttribute fontWeight(float weight) {
        return new TextAttribute(Properties.FONT_WEIGHT, ValueTypes.F32,
                Float.floatToRawIntBits(weight));
    }

    public static TextAttribute color(int argb) {
        return new TextAttribute(Properties.COLOR, ValueTypes.COLOR, argb);
    }

    public static TextAttribute italic() {
        return new TextAttribute(Properties.FONT_STYLE, ValueTypes.F32,
                Float.floatToRawIntBits(1.0f));
    }

    public static TextAttribute fontDesign(int code) {
        return new TextAttribute(Properties.FONT_DESIGN, ValueTypes.F32,
                Float.floatToRawIntBits((float) code));
    }

    public static TextAttribute underline() {
        return underline(true);
    }

    public static TextAttribute underline(boolean on) {
        return new TextAttribute(Properties.UNDERLINE, ValueTypes.U8, on ? 1 : 0);
    }

    public static TextAttribute strikethrough() {
        return strikethrough(true);
    }

    public static TextAttribute strikethrough(boolean on) {
        return new TextAttribute(Properties.STRIKETHROUGH, ValueTypes.U8, on ? 1 : 0);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TextAttribute)) {
            return false;
        }
        TextAttribute that = (TextAttribute) other;
        return property == that.property
                && valueType == that.valueType
                && value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(property, valueType, value);
    }

    @Override
    public String toString() {
        return "TextAttribute(property=" + property + ", valueType=" + valueType
                + ", value=" + value + ")";
    }
}
