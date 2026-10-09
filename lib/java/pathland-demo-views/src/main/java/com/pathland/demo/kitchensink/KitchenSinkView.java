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
        return ScrollView.children(VStack.with(v -> v.alignment(HorizontalAlignment.LEADING).spacing(10)).children(
                // The kitchen-sink title is a heading → `<h2>`.
                Text.with(t -> t.text("Pathland Kitchensink")).modifiers(FontSize.with(s -> s.size(24)), FontWeight.BOLD, Padding.with(p -> p.edges(8, 0 ,8 ,0)))
                        .modifiers(AccessibilityRole.with(a -> a.role(Roles.HEADER))),
                Text.with(t -> t.text("Every protocol primitive, control, modifier, and state binding"))
                        .modifiers(ForegroundStyle.with(f -> f.color(Color.rgb(0x88, 0x88, 0x88)))),
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
                new MediaSection(),
                new AppearanceSection(),
                Text.with(t -> t.text("Pathland · per-session · 16-byte deltas"))
                        .modifiers(
                                ForegroundStyle.with(f -> f.color(Color.rgb(0x88, 0x88, 0x88))),
                                Padding.with(p -> p.uniform(16)))
        )
        // The demo's main content region → `<main>`.
        .modifiers(AccessibilityRole.with(a -> a.role(Roles.MAIN)))
        .modifiers(Padding.with(p -> p.uniform(24))));
    }
}