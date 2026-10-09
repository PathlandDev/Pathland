package com.pathland.demo.counter;

import com.pathland.view.AccessibilityRole;
import com.pathland.view.Color;
import com.pathland.view.Roles;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.ForegroundStyle;
import com.pathland.view.Padding;


/**
 * The application's root view: the {@link CounterControls} and {@link NameField}
 * components plus a footer. Children are instantiated inline — each view connects its own
 * {@code State} during render, so they need not be declared as fields.
 */
public final class CounterView implements View {

    @Override
    public View body() {
        return VStack.children(
                new CounterControls(),
                new NameField(),
                Text.with(t -> t.text("Pathland · per-session · 16-byte deltas"))
                        .modifiers(ForegroundStyle.with(f -> f.color(Color.rgb(0x88, 0x88, 0x88))))
        )
        // The app's main content region → `<main>`.
        .modifiers(AccessibilityRole.with(a -> a.role(Roles.MAIN)))
        .modifiers(Padding.with(p -> p.uniform(24)));
    }
}
