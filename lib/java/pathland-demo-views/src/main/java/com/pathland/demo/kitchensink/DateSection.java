package com.pathland.demo.kitchensink;

import com.pathland.view.DatePicker;
import com.pathland.view.DatePickerMode;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import static com.pathland.view.signal.Signals.*;
import com.pathland.view.state.State;
import com.pathland.view.Padding;


/**
 * Date section: a {@code DatePicker} bound to a persisted {@code State<Integer>}
 * holding days since epoch (the {@code STYLE::SET_DATE} value), with a computed
 * human-readable date label.
 */
public final class DateSection implements View {

    State<Integer> days = new State<>(20487, "days");

    @Override
    public View body() {
        var dateLabel = computed(() ->
                java.time.LocalDate.ofEpochDay(days.get()).toString());
        return new SectionCard("DatePicker · STYLE::SET_DATE",
                VStack.of(
                        DatePicker.of(DatePickerMode.DATE, days.signal()),
                        Text.of(dateLabel).with(Padding.of(4))
                ).with(Padding.of(4))
        );
    }
}