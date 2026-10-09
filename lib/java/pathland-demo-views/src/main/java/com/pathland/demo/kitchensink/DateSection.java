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
 * holding days since epoch (the {@code PARAMETER::SET_DATE} value), with a computed
 * human-readable date label.
 */
public final class DateSection implements View {

    State<Integer> days = new State<>(20487, "days");

    @Override
    public View body() {
        var dateLabel = computed(() ->
                java.time.LocalDate.ofEpochDay(days.get()).toString());
        return new SectionCard("DatePicker · PARAMETER::SET_DATE",
                VStack.children(
                        DatePicker.with(d -> d.mode(DatePickerMode.DATE).selection(days.signal())),
                        Text.with(t -> t.text(dateLabel)).modifiers(Padding.with(p -> p.uniform(4)))
                ).modifiers(Padding.with(p -> p.uniform(4)))
        );
    }
}