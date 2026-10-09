package com.pathland.view;

/** Content mode for image/size fitting (the {@code CONTENT_MODE} enum: {@code Fit}=0, {@code Fill}=1). */
public enum ContentMode implements WireValue {

    FIT(0),
    FILL(1);

    private final int wire;

    ContentMode(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }
}