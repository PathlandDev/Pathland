package com.pathland.view;

/** A `VStack`'s cross-axis alignment (SwiftUI {@code HorizontalAlignment}): the
 *  horizontal position of children within the stack. Position-only — a child's
 *  {@code FILL} size kind stretches, never an alignment. */
public enum HorizontalAlignment {

    LEADING(0),
    CENTER(1),
    TRAILING(2);

    private final int wire;

    HorizontalAlignment(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }
}