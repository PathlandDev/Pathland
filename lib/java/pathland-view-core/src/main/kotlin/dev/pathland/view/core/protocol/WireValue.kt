package dev.pathland.view.core.protocol

/** A value — typically an enum — that maps to a numeric protocol code. */
interface WireValue {
    /** The numeric protocol code for this value. */
    fun wire(): Int
}
