package com.pathland.demo.kitchensink;

import com.pathland.view.FontWeight;
import com.pathland.view.Gauge;
import com.pathland.view.ProgressView;
import com.pathland.view.Slider;
import com.pathland.view.Stepper;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import static com.pathland.view.signal.Signals.*;
import com.pathland.view.state.State;
import com.pathland.view.FontSize;
import com.pathland.view.Padding;


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
        return new SectionCard("Value controls · Slider / Stepper / Gauge / Progress",
                VStack.children(
                        Text.with(t -> t.text(valueLabel)).modifiers(FontSize.with(s -> s.size(22)), FontWeight.BOLD),
                        Slider.with(s -> s.value(value.signal()).in(0f, 100f)),
                        Stepper.with(s -> s.value(value.signal()).in(0f, 100f).step(5f)),
                        Gauge.with(g -> g.value(value.get()).minValue(0f).maxValue(100f)),
                        ProgressView.with(p -> p.value(value.get() / 100f))
                ).modifiers(Padding.with(p -> p.uniform(4)))
        );
    }
}