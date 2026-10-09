package com.pathland.demo.settings;

import com.pathland.view.AccessibilityRole;
import com.pathland.view.FontSize;
import com.pathland.view.FontWeight;
import com.pathland.view.Padding;
import com.pathland.view.Roles;
import com.pathland.view.Slider;
import com.pathland.view.Text;
import com.pathland.view.Toggle;
import com.pathland.view.ToggleStyle;
import com.pathland.view.VStack;
import com.pathland.view.View;
import static com.pathland.view.signal.Signals.*;

/**
 * The Settings content area of the {@code SplitNavDemo}: a titled pane with a switch and a
 * volume slider bound to local signals. Router-free and self-contained, so it is
 * instantiated inline in the route table as {@code new SettingsView()}.
 */
public final class SettingsView implements View {

    @Override
    public View body() {
        var dark = signal(false);
        var volume = signal(50f);
        var volumeLabel = computed(() -> "Volume: " + volume.get().intValue());
        return VStack.children(
                // The destination title is a heading → `<h2>`.
                Text.with(t -> t.text("Settings")).modifiers(FontSize.with(s -> s.size(24)), FontWeight.BOLD)
                        .modifiers(AccessibilityRole.with(a -> a.role(Roles.HEADER))),
                Text.with(t -> t.text("A few controls bound to plain signals — the content area is "
                        + "just another destination view.")).modifiers(Padding.with(p -> p.uniform(8))),
                Toggle.with(t -> t.style(ToggleStyle.SWITCH).isOn(dark).label("Dark mode")),
                Text.with(t -> t.text(volumeLabel)).modifiers(Padding.with(p -> p.uniform(8))),
                Slider.with(s -> s.value(volume).in(0f, 100f))
        )
        // The destination content region → `<main>`.
        .modifiers(AccessibilityRole.with(a -> a.role(Roles.MAIN)))
        .modifiers(Padding.with(p -> p.uniform(24)));
    }
}