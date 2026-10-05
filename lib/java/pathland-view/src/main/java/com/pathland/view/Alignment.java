package com.pathland.view;

/** A 2D alignment ({@code Alignment} / Compose {@code Alignment}) for
 *  `ZStack` children, grid cells, and the {@code frame} modifier's content
 *  placement. The wire code packs a horizontal and vertical position (spec
 *  PRIMITIVES.md §ZStack); `0`/absent is the default top-leading. Position-only
 *  — a child's {@code FILL} size kind stretches, never an alignment. */
public enum Alignment {

    TOP_LEADING(0),
    CENTER(1),
    BOTTOM_TRAILING(2),
    TOP_CENTER(3),
    BOTTOM_CENTER(4),
    CENTER_LEADING(5),
    CENTER_TRAILING(6),
    TOP_TRAILING(7),
    BOTTOM_LEADING(8);

    private final int wire;

    Alignment(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }
}