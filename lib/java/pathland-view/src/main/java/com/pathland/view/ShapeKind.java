package com.pathland.view;

/** Shape geometry for a {@code SHAPE} node (the {@code SHAPE_KIND} enum). */
public enum ShapeKind implements WireValue {

    CIRCLE(0),
    RECTANGLE(1),
    ROUNDED_RECTANGLE(2),
    CAPSULE(3),
    ELLIPSE(4),
    PATH(5);

    private final int wire;

    ShapeKind(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }
}