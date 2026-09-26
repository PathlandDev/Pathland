package com.pathland.demo.kitchensink;

import com.pathland.view.*;


/**
 * The kitchen-sink application root: every demo section stacked in a scrollable
 * column. Children are instantiated inline — each section connects its own
 * {@code State} during render.
 */
public final class KitchenSinkView implements View {

    @Override
    public View body() {
        return ScrollView.of(VStack.of(Alignment.LEADING, 10,
                // The kitchen-sink title is a heading → `<h2>`.
                Text.of("Pathland Kitchensink").modifiers(FontSize.of(24), FontWeightMod.of(FontWeight.BOLD), Padding.of(8, 0 ,8 ,0))
                        .modifier(AccessibilityRole.of(Roles.HEADER)),
                Text.of("Every protocol primitive, control, modifier, and state binding")
                        .modifier(ForegroundStyle.of(Color.rgb(0x88, 0x88, 0x88))),
                new CounterSection(),
                new TextFieldSection(),
                new ToggleSection(),
                new ValueControlsSection(),
                new PickerSection(),
                new ColorSection(),
                new DateSection(),
                new ThemeSection(),
                new LayoutSection(),
                new TextStylesSection(),
                new LabelSection(),
                new AppearanceSection(),
                Text.of("Pathland · per-session · 16-byte deltas")
                        .modifiers(
                                ForegroundStyle.of(Color.rgb(0x88, 0x88, 0x88)),
                                Padding.of(16))
        )
        // The demo's main content region → `<main>`.
        .modifier(AccessibilityRole.of(Roles.MAIN))
        .modifier(Padding.of(24)));
    }
}