package dev.pathland.core.protocol;

/**
 * A color value exposes its packed {@code 0xAARRGGBB} representation.
 *
 * <p>This is the small SPI that lets the wire/protocol layer encode colors
 * without importing the view-side {@code Color} type (which belongs to the DSL
 * runtime module).
 */
public interface ColorValue {
    /** The packed sRGB {@code 0xAARRGGBB} value. */
    int argb();
}
