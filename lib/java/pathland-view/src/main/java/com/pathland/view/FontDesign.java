package com.pathland.view;

/**
 * Font design (the {@code FONT_DESIGN} enum: {@code Default}=0, {@code Serif}=1,
 * {@code Rounded}=2, {@code Monospaced}=3).
 */
public enum FontDesign implements ViewModifier, WireValue {

    DEFAULT(0),
    SERIF(1),
    ROUNDED(2),
    MONOSPACED(3);

    private final int wire;

    FontDesign(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }

    /** The font design is itself the modifier ({@code FONT_DESIGN}). */
    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.FONT_DESIGN, (float) wire));
    }
}