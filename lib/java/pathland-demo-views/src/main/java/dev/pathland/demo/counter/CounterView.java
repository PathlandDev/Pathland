package dev.pathland.demo.counter;

import dev.pathland.view.AccessibilityRole;
import dev.pathland.view.Color;
import dev.pathland.view.Roles;
import dev.pathland.view.Text;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import dev.pathland.view.ForegroundStyle;
import dev.pathland.view.Padding;
import static dev.pathland.view.Modifiers.*;


/**
 * The application's root view: the {@link CounterControls} and {@link NameField}
 * components plus a footer. Children are instantiated inline — each view connects its own
 * {@code State} during render, so they need not be declared as fields.
 */
public final class CounterView implements View {

    @Override
    public View body() {
        return VStack.make()
                // The app's main content region → `<main>`.
                .modifiers(
                        accessibilityRole(Roles.MAIN),
                        padding(24))
                .children(
                        new CounterControls(),
                        new NameField(),
                        Text.make("Pathland · per-session · 16-byte deltas")
                                .modifiers(foregroundStyle(Color.rgb(0x88, 0x88, 0x88))));
    }
}
