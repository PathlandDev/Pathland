package dev.pathland.demo.kitchensink;

import dev.pathland.view.DatePicker;
import dev.pathland.view.DatePickerMode;
import dev.pathland.view.Text;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import static dev.pathland.view.signal.Signals.*;
import dev.pathland.view.state.State;
import dev.pathland.view.Padding;
import static dev.pathland.view.Modifiers.*;


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
        View content = VStack.make()
                .modifiers(padding(4))
                .children(
                        DatePicker.make(d -> d.mode(DatePickerMode.DATE).selection(days.signal())),
                        Text.make(dateLabel).modifiers(
                                padding(4)));
        return SectionCard.make("DatePicker · PARAMETER::SET_DATE").children(content);
    }
}