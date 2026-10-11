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
        View content = VStack.make()
                .modifiers(Padding.with(4))
                .children(
                        Text.make(valueLabel).modifiers(
                                FontSize.with(22), FontWeight.BOLD),
                        Slider.make(s -> s.value(value.signal()).in(0f, 100f)),
                        Stepper.make(s -> s.value(value.signal()).in(0f, 100f).step(5f)),
                        Gauge.make(g -> g.value(value.get()).minValue(0f).maxValue(100f)),
                        ProgressView.make(value.get() / 100f));
        return SectionCard.make("Value controls · Slider / Stepper / Gauge / Progress").children(content);
    }
}