package com.pathland.demo.kitchensink;

import com.pathland.view.Button;
import com.pathland.view.Color;
import com.pathland.view.Menu;
import com.pathland.view.Picker;
import com.pathland.view.PickerStyle;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;
import com.pathland.view.state.State;
import com.pathland.view.ForegroundStyle;
import com.pathland.view.Padding;


/**
 * Selection section: a {@code Picker} in two styles ({@code Segmented} and
 * {@code Menu}) bound to a persisted {@code State<Integer>} (the chosen option index),
 * plus a {@code Menu} whose action items are {@code Button}s.
 */
public final class PickerSection implements View {

    State<Integer> choice = new State<>(1, "choice");
    Signal<String> choiceLabel = Signals.computed(() -> "Choice: " + choice.get());

    @Override
    public View body() {
        return new SectionCard("Picker + Menu",
                VStack.of(
                        Text.of(choiceLabel).modifier(Padding.of(4)),
                        Picker.of(PickerStyle.SEGMENTED, choice.signal(),
                                Text.of("One"), Text.of("Two"), Text.of("Three")),
                        Picker.of(PickerStyle.MENU, choice.signal(),
                                Text.of("One"), Text.of("Two"), Text.of("Three")),
                        Menu.of(Button.of("Actions ▾", () -> { }),
                                Button.of("Item 1", () -> { }),
                                Button.of("Item 2", () -> { })),
                        Text.of("Menu action items are Buttons; choices route via VALUE_CHANGED")
                                .modifier(ForegroundStyle.of(Color.rgb(0x88, 0x88, 0x88)))
                ).modifier(Padding.of(4))
        );
    }
}