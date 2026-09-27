package com.pathland.demo.counter;

import com.pathland.view.Button;
import com.pathland.view.HStack;
import com.pathland.view.Text;
import com.pathland.view.View;
import static com.pathland.view.signal.Signals.*;
import com.pathland.view.state.State;
import com.pathland.view.Padding;


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
        return HStack.of(
                Text.of(countLabel),
                Button.of("Increment", () -> count.update(v -> v + 1))
        ).with(Padding.of(16));
    }
}