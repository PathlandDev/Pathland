package com.pathland.view;

/** Date picker mode (the {@code DATE_PICKER_MODE} enum: {@code Date}=0, {@code Time}=1, {@code DateAndTime}=2). */
public enum DatePickerMode implements WireValue {

    DATE(0),
    TIME(1),
    DATE_AND_TIME(2);

    private final int wire;

    DatePickerMode(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }
}