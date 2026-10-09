package com.pathland.view;

/** Control size (the {@code CONTROL_SIZE} enum: {@code Small}=0, {@code Regular}=1, {@code Large}=2). */
public enum ControlSize implements ViewModifier {

    SMALL(0),
    REGULAR(1),
    LARGE(2);

    private final int wire;

    ControlSize(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }

    /** The control size is itself the modifier ({@code CONTROL_SIZE}). */
    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.CONTROL_SIZE, (float) wire));
    }
}