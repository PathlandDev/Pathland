package com.pathland.view;

/**
 * A value — typically an enum — that maps to a numeric protocol code (its
 * {@code wire}). Lets a reactive config member of an enum type (e.g.
 * {@code Signal<ShapeKind>}) be normalized to a property's {@code F32} value by
 * {@code com.pathland.view.emit.ReactiveValues}.
 */
public interface WireValue {

    /** The numeric protocol code for this value. */
    int wire();
}
