package com.pathland.demo.kitchensink;

import com.pathland.view.Text;
import com.pathland.view.Toggle;
import com.pathland.view.ToggleStyle;
import com.pathland.view.VStack;
import com.pathland.view.View;
import static com.pathland.view.signal.Signals.*;
import com.pathland.view.state.State;
import com.pathland.view.Padding;


/**
 * Toggle section: the same {@code Toggle} control in all three {@code ToggleStyle}
 * variants ({@code Switch}, {@code Checkbox}, {@code Button}), each bound to a
 * persisted {@code State<Boolean>}, with a computed summary line.
 */
public final class ToggleSection implements View {

    State<Boolean> dark = new State<>(false, "dark");
    State<Boolean> notify = new State<>(true, "notify");
    State<Boolean> bold = new State<>(false, "bold");

    @Override
    public View body() {
        var summary = computed(() ->
                "Dark: " + dark.get() + " · Notify: " + notify.get() + " · Bold: " + bold.get());
        return new SectionCard("Toggle · Switch / Checkbox / Button",
                VStack.children(
                        Toggle.with(t -> t.style(ToggleStyle.SWITCH).isOn(dark.signal()).label("Dark mode")),
                        Toggle.with(t -> t.style(ToggleStyle.CHECKBOX).isOn(notify.signal()).label("Notify me")),
                        Toggle.with(t -> t.style(ToggleStyle.BUTTON).isOn(bold.signal()).label("Bold toggle")),
                        Text.with(t -> t.text(summary)).modifiers(Padding.with(p -> p.uniform(4)))
                ).modifiers(Padding.with(p -> p.uniform(4)))
        );
    }
}