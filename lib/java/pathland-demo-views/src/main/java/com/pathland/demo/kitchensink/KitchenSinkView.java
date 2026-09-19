package com.pathland.demo.kitchensink;

import com.pathland.view.Color;
import com.pathland.view.FontWeight;
import com.pathland.view.ScrollView;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.FontSize;
import com.pathland.view.FontWeightMod;
import com.pathland.view.ForegroundStyle;
import com.pathland.view.Padding;


/**
 * The kitchen-sink application root: every demo section stacked in a scrollable
 * column. Children are instantiated inline — each section connects its own
 * {@code State} during render.
 */
public final class KitchenSinkView implements View {

    @Override
    public View body() {
        return ScrollView.of(VStack.of(
                Text.of("Pathland Kitchensink").modifiers(FontSize.of(24), FontWeightMod.of(FontWeight.BOLD), Padding.of(8)),
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
        ).modifier(Padding.of(24)));
    }
}