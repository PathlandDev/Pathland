package dev.pathland.view;

/** A {@code SCROLLVIEW}'s scroll axis (the {@code SCROLL_AXIS} enum,
 *  spec/PRIMITIVES.md §ScrollView). The view is layout-greedy on this axis; the
 *  cross axis hugs its content (a {@code FILL}-width content child propagates
 *  {@code FILL}). */
public enum ScrollAxis implements WireValue {

    VERTICAL(0),
    HORIZONTAL(1);

    private final int wire;

    ScrollAxis(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }
}
