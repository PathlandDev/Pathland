package com.pathland.demo.kitchensink;

import com.pathland.view.Text;
import com.pathland.view.Toggle;
import com.pathland.view.ToggleStyle;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;
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
    Signal<String> summary = Signals.computed(() ->
            "Dark: " + dark.get() + " · Notify: " + notify.get() + " · Bold: " + bold.get());

    @Override
    public View body() {
        return new SectionCard("Toggle · Switch / Checkbox / Button",
                VStack.of(
                        Toggle.of(ToggleStyle.SWITCH, dark.signal(), "Dark mode"),
                        Toggle.of(ToggleStyle.CHECKBOX, notify.signal(), "Notify me"),
                        Toggle.of(ToggleStyle.BUTTON, bold.signal(), "Bold toggle"),
                        Text.of(summary).modifier(Padding.of(4))
                ).modifier(Padding.of(4))
        );
    }
}