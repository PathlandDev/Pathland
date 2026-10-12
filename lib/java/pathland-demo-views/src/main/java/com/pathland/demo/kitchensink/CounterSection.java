package com.pathland.demo.kitchensink;

import com.pathland.view.Alignment;
import com.pathland.view.Button;
import com.pathland.view.Color;
import com.pathland.view.FontWeight;
import com.pathland.view.HStack;
import com.pathland.view.Stepper;
import com.pathland.view.Text;
import com.pathland.view.VerticalAlignment;
import com.pathland.view.VStack;
import com.pathland.view.View;
import static com.pathland.view.signal.Signals.*;
import com.pathland.view.state.State;
import com.pathland.view.FontSize;
import com.pathland.view.ForegroundStyle;
import com.pathland.view.Padding;
import static com.pathland.view.Modifiers.*;


/**
 * Counter section: a persisted {@code State<Integer>} mutated by buttons and a
 * {@code Stepper} that sizes the increment step. Demonstrates {@code Button},
 * {@code Stepper}, and a computed label.
 */
public final class CounterSection implements View {

    State<Integer> count = new State<>(0, "count");
    State<Float> step = new State<>(1f, "step");

    @Override
    public View body() {
        var countLabel = computed(() -> "Count: " + count.get());
        View content = VStack.make()
                .modifiers(padding(4))
                .children(
                        Text.make(countLabel).modifiers(
                                fontSize(28), FontWeight.BOLD),
                        HStack.make(h -> h.alignment(VerticalAlignment.TOP).spacing(4)).modifiers(padding(4))
                                .children(
                                        Button.make(b -> b.title("−").action(() -> count.update(v -> v - step.get().intValue()))),
                                        Button.make(b -> b.title("+").action(() -> count.update(v -> v + step.get().intValue()))),
                                        Button.make(b -> b.title("Reset").action(() -> count.set(0)))),
                        Text.make("Step size").modifiers(
                                foregroundStyle(Color.rgb(0x88, 0x88, 0x88))),
                        Stepper.make(s -> s.value(step.signal()).in(1, 10).step(1)));
        return SectionCard.make("Counter · State + Button + Stepper").children(content);
    }
}