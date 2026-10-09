package com.pathland.view;

/** An `HStack`'s cross-axis alignment ({@code VerticalAlignment}): the
 *  vertical position of children within the stack. Position-only — a child's
 *  {@code FILL} size kind stretches, never an alignment. */
public enum VerticalAlignment implements WireValue {

    TOP(0),
    CENTER(1),
    BOTTOM(2);

    private final int wire;

    VerticalAlignment(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }
}