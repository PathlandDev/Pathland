package dev.pathland.core.protocol;

/** A value, typically an enum, that maps to a numeric protocol code. */
public interface WireValue {
    /** The numeric protocol code for this value. */
    int wire();
}
