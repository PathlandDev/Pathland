package com.pathland.demo.kitchensink;

import com.pathland.view.Button;
import com.pathland.view.Color;
import com.pathland.view.Menu;
import com.pathland.view.Picker;
import com.pathland.view.PickerStyle;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import static com.pathland.view.signal.Signals.*;
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

    @Override
    public View body() {
        var choiceLabel = computed(() -> "Choice: " + choice.get());
        View content = VStack.make()
                .modifiers(Padding.with(4))
                .children(
                        Text.make(choiceLabel).modifiers(
                                Padding.with(4)),
                        Picker.make(p -> p.style(PickerStyle.SEGMENTED).selection(choice.signal())).children(
                                Text.make("One"), Text.make("Two"), Text.make("Three")),
                        Picker.make(p -> p.style(PickerStyle.MENU).selection(choice.signal())).children(
                                Text.make("One"), Text.make("Two"), Text.make("Three")),
                        Menu.children(
                                Button.make(b -> b.title("Actions ▾").action(() -> { })),
                                Button.make(b -> b.title("Item 1").action(() -> { })),
                                Button.make(b -> b.title("Item 2").action(() -> { }))),
                        Text.make("Menu action items are Buttons; choices route via VALUE_CHANGED")
                                .modifiers(ForegroundStyle.with(Color.rgb(0x88, 0x88, 0x88))));
        return SectionCard.make("Picker + Menu").children(content);
    }
}