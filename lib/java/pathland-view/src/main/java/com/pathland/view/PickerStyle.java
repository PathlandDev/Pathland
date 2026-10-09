package com.pathland.view;

/** Picker style (the {@code PICKER_STYLE} enum: {@code Menu}=0, {@code Segmented}=1, {@code Wheel}=2, {@code RadioGroup}=3). */
public enum PickerStyle implements WireValue {

    MENU(0),
    SEGMENTED(1),
    WHEEL(2),
    RADIO_GROUP(3);

    private final int wire;

    PickerStyle(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }
}