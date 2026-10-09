package com.pathland.view;

/** Text alignment (protocol ENUM values). */
public enum TextAlignment implements ViewModifier {

    LEADING(0),
    CENTER(1),
    TRAILING(2);

    private final int wire;

    TextAlignment(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }

    /** The alignment is itself the modifier ({@code TEXT_ALIGNMENT}). */
    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.TEXT_ALIGNMENT, (float) wire));
    }
}