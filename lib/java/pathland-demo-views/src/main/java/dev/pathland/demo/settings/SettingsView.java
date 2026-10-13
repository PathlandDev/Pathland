package dev.pathland.demo.settings;

import dev.pathland.view.AccessibilityRole;
import dev.pathland.view.FontSize;
import dev.pathland.view.FontWeight;
import dev.pathland.view.Padding;
import dev.pathland.view.Roles;
import dev.pathland.view.Slider;
import dev.pathland.view.Text;
import dev.pathland.view.Toggle;
import dev.pathland.view.ToggleStyle;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import static dev.pathland.view.Modifiers.*;
import static dev.pathland.view.signal.Signals.*;

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
        return VStack.make()
                // The destination content region → `<main>`.
                .modifiers(
                        accessibilityRole(Roles.MAIN),
                        padding(24))
                .children(
                        // The destination title is a heading → `<h2>`.
                        Text.make("Settings").modifiers(
                                fontSize(24), FontWeight.BOLD, accessibilityRole(Roles.HEADER)),
                        Text.make("A few controls bound to plain signals — the content area is "
                                + "just another destination view.").modifiers(padding(8)),
                        Toggle.make(t -> t.style(ToggleStyle.SWITCH).isOn(dark).label("Dark mode")),
                        Text.make(volumeLabel).modifiers(padding(8)),
                        Slider.make(s -> s.value(volume).in(0f, 100f)));
    }
}