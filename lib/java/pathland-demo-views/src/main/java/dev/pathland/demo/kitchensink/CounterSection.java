package dev.pathland.demo.kitchensink;

import dev.pathland.view.Alignment;
import dev.pathland.view.Button;
import dev.pathland.view.Color;
import dev.pathland.view.FontWeight;
import dev.pathland.view.HStack;
import dev.pathland.view.Stepper;
import dev.pathland.view.Text;
import dev.pathland.view.VerticalAlignment;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import static dev.pathland.view.signal.Signals.*;
import dev.pathland.view.state.State;
import dev.pathland.view.FontSize;
import dev.pathland.view.ForegroundStyle;
import dev.pathland.view.Padding;
import static dev.pathland.view.Modifiers.*;


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