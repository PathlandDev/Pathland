package dev.pathland.demo.kitchensink;

import dev.pathland.view.FontWeight;
import dev.pathland.view.Gauge;
import dev.pathland.view.ProgressView;
import dev.pathland.view.Slider;
import dev.pathland.view.Stepper;
import dev.pathland.view.Text;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import static dev.pathland.view.signal.Signals.*;
import dev.pathland.view.state.State;
import dev.pathland.view.FontSize;
import dev.pathland.view.Padding;
import static dev.pathland.view.Modifiers.*;


/**
 * Value-controls section: one persisted {@code State<Float>} driving a {@code Slider},
 * a {@code Stepper}, a read-only {@code Gauge}, and a {@code ProgressView} — all
 * reporting their changes back into the same binding.
 */
public final class ValueControlsSection implements View {

    State<Float> value = new State<>(50f, "value");

    @Override
    public View body() {
        var valueLabel = computed(() -> "Value: " + value.get());
        View content = VStack.make()
                .modifiers(padding(4))
                .children(
                        Text.make(valueLabel).modifiers(
                                fontSize(22), FontWeight.BOLD),
                        Slider.make(s -> s.value(value.signal()).in(0f, 100f)),
                        Stepper.make(s -> s.value(value.signal()).in(0f, 100f).step(5f)),
                        Gauge.make(g -> g.value(value.get()).minValue(0f).maxValue(100f)),
                        ProgressView.make(value.get() / 100f));
        return SectionCard.make("Value controls · Slider / Stepper / Gauge / Progress").children(content);
    }
}