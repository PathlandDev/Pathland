package dev.pathland.demo.counter;

import dev.pathland.view.Button;
import dev.pathland.view.HStack;
import dev.pathland.view.Text;
import dev.pathland.view.View;
import static dev.pathland.view.signal.Signals.*;
import dev.pathland.view.state.State;
import dev.pathland.view.Padding;
import static dev.pathland.view.Modifiers.*;


/**
 * A view component: the count label plus its increment button. The {@code count} state is
 * a {@link State} field — the annotation processor wires it to the session's store
 * automatically (keyed {@code "count"}).
 */
public final class CounterControls implements View {

    State<Integer> count = new State<>(0);

    @Override
    public View body() {
        var countLabel = computed(() -> "Count: " + count.get());
        return HStack.make()
                .modifiers(padding(16))
                .children(
                        Text.make(countLabel),
                        Button.make(b -> b.title("Increment").action(() -> count.update(v -> v + 1))));
    }
}