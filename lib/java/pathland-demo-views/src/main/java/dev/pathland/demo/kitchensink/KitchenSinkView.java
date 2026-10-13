package dev.pathland.demo.kitchensink;

import dev.pathland.view.*;
import static dev.pathland.view.Modifiers.*;


/**
 * The kitchen-sink application root: every demo section stacked in a scrollable
 * column. Children are instantiated inline — each section connects its own
 * {@code State} during render.
 */
public final class KitchenSinkView implements View {

    @Override
    public View body() {
        return ScrollView.make()
                // The demo's main content region → `<main>`.
                .modifiers(
                        accessibilityRole(Roles.MAIN),
                        padding(24))
                .children(
                        VStack
                                .make(v -> v.alignment(HorizontalAlignment.LEADING).spacing(10))
                                .children(
                                        // The kitchen-sink title is a heading → `<h2>`.
                                        Text.make("Pathland Kitchensink").modifiers(
                                                fontSize(24), FontWeight.BOLD,
                                                padding(p -> p.edges(8, 0, 8, 0)),
                                                accessibilityRole(Roles.HEADER)),
                                        Text.make("Every protocol primitive, control, modifier, and state binding")
                                                .modifiers(foregroundStyle(Color.rgb(0x88, 0x88, 0x88))),
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
                                        Text.make("Pathland · per-session · 16-byte deltas")
                                                .modifiers(
                                                        foregroundStyle(Color.rgb(0x88, 0x88, 0x88)),
                                                        padding(16))));
    }
}