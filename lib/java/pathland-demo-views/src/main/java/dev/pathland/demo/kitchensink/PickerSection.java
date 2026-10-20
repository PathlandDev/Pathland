package dev.pathland.demo.kitchensink;

import dev.pathland.view.Button;
import dev.pathland.view.Color;
import dev.pathland.view.Menu;
import dev.pathland.view.Picker;
import dev.pathland.view.PickerStyle;
import dev.pathland.view.Text;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import static dev.pathland.view.signal.Signals.*;
import dev.pathland.view.state.State;
import dev.pathland.view.ForegroundStyle;
import dev.pathland.view.Padding;
import static dev.pathland.view.Modifiers.*;


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
                .modifiers(padding(4))
                .children(
                        Text.make(choiceLabel).modifiers(
                                padding(4)),
                        Picker.make(p -> p.style(PickerStyle.SEGMENTED).selection(choice.signal())).children(
                                Text.make("One"), Text.make("Two"), Text.make("Three")),
                        Picker.make(p -> p.style(PickerStyle.MENU).selection(choice.signal())).children(
                                Text.make("One"), Text.make("Two"), Text.make("Three")),
                        Menu.children(
                                Button.make(b -> b.title("Actions ▾").action(() -> { })),
                                Button.make(b -> b.title("Item 1").action(() -> { })),
                                Button.make(b -> b.title("Item 2").action(() -> { }))),
                        Text.make("Menu action items are Buttons; choices route via VALUE_CHANGED")
                                .modifiers(foregroundStyle(Color.rgb(0x88, 0x88, 0x88))));
        return SectionCard.make("Picker + Menu").children(content);
    }
}