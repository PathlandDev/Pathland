package com.pathland.view;

/** Font style (the {@code FONT_STYLE} enum: {@code Normal}=0, {@code Italic}=1). */
public enum FontStyle implements ViewModifier, WireValue {

    NORMAL(0),
    ITALIC(1);

    private final int wire;

    FontStyle(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }

    /** The font style is itself the modifier ({@code FONT_STYLE}). */
    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.FONT_STYLE, (float) wire));
    }
}