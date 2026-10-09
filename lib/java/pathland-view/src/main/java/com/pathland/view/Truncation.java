package com.pathland.view;

/** Truncation mode for multi-line text (protocol ENUM values). */
public enum Truncation implements ViewModifier, WireValue {

    HEAD(0),
    MIDDLE(1),
    TAIL(2);

    private final int wire;

    Truncation(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }

    /** The truncation mode is itself the modifier ({@code TRUNCATION_MODE}). */
    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.TRUNCATION_MODE, (float) wire));
    }
}