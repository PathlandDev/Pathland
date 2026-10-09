package com.pathland.view;

/** The visual variant of a {@code TOGGLE} control (the {@code TOGGLE_STYLE} enum). */
public enum ToggleStyle implements WireValue {

    SWITCH(0),
    CHECKBOX(1),
    BUTTON(2);

    private final int wire;

    ToggleStyle(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }
}