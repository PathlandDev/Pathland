package com.pathland.demo.settings;

import com.pathland.view.FontSize;
import com.pathland.view.FontWeight;
import com.pathland.view.FontWeightMod;
import com.pathland.view.Padding;
import com.pathland.view.Slider;
import com.pathland.view.Text;
import com.pathland.view.Toggle;
import com.pathland.view.ToggleStyle;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;

/**
 * The Settings content area of the {@code SplitNavDemo}: a titled pane with a switch and a
 * volume slider bound to local signals. Router-free and self-contained, so it is
 * instantiated inline in the route table as {@code new SettingsView()}.
 */
public final class SettingsView implements View {

    @Override
    public View body() {
        WritableSignal<Boolean> dark = Signals.signal(false);
        WritableSignal<Float> volume = Signals.signal(50f);
        Signal<String> volumeLabel = Signals.computed(() -> "Volume: " + volume.get().intValue());
        return VStack.of(
                Text.of("Settings").modifiers(FontSize.of(24), FontWeightMod.of(FontWeight.BOLD)),
                Text.of("A few controls bound to plain signals — the content area is "
                        + "just another destination view.").modifier(Padding.of(8)),
                Toggle.of(ToggleStyle.SWITCH, dark.get(), dark, "Dark mode"),
                Text.of(volumeLabel).modifier(Padding.of(8)),
                Slider.of(volume, 0f, 100f)
        ).modifier(Padding.of(24));
    }
}