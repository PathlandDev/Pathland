package com.pathland.view;

/**
 * Font weight. The protocol carries {@code FONT_WEIGHT} as a numeric {@code F32} on
 * the 100–900 scale (regular ≈ 400, bold ≈ 700); {@link #wire()} returns that value.
 */
public enum FontWeight implements ViewModifier, WireValue {

    LIGHT(300),
    REGULAR(400),
    MEDIUM(500),
    SEMIBOLD(600),
    BOLD(700);

    private final int wire;

    FontWeight(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }

    /** The font weight is itself the modifier ({@code FONT_WEIGHT}). */
    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.FONT_WEIGHT, (float) wire));
    }
}