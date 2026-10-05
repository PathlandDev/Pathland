package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.WritableSignal;

/**
 * A date & time selection control ({@code DatePicker}). The value is carried by
 * the {@code PARAMETER::SET_DATE} command ({@code B}=days since epoch, {@code C}=millis of
 * day); the bound signal holds days since epoch. Renders with the
 * {@code DATE_PICKER_MODE} token.
 */
public final class DatePicker implements View {

    private final DatePickerMode mode;
    private final WritableSignal<Integer> days;

    private DatePicker(DatePickerMode mode, WritableSignal<Integer> days) {
        this.mode = mode;
        this.days = days;
    }

    /** A date & time picker bound to days-since-epoch. */
    public static DatePicker of(DatePickerMode mode, WritableSignal<Integer> days) {
        return new DatePicker(mode, days);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.DATE_PICKER);
        node.properties.put(Properties.DATE_PICKER_MODE, (float) mode.wire());
        node.days = days.get();
        node.dateBinding = days;
        node.dateInput = (d, m) -> days.set(d);
        return node;
    }
}