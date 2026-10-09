package com.pathland.view;

/** Text case transformation (the {@code TEXT_CASE} enum: {@code None}=0, {@code Uppercase}=1, {@code Lowercase}=2). */
public enum TextCase implements ViewModifier {

    NONE(0),
    UPPERCASE(1),
    LOWERCASE(2);

    private final int wire;

    TextCase(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }

    /** The text case is itself the modifier ({@code TEXT_CASE}). */
    @Override
    public View body(View content) {
        return Modified.props(content, Modified.prop(Properties.TEXT_CASE, (float) wire));
    }
}