package com.pathland.demo.kitchensink;

import com.pathland.view.Alignment;
import com.pathland.view.Color;
import com.pathland.view.ColorPicker;
import com.pathland.view.HStack;
import com.pathland.view.Rectangle;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import static com.pathland.view.signal.Signals.*;
import com.pathland.view.state.State;
import com.pathland.view.Background;
import com.pathland.view.Padding;


/**
 * Color section: a {@code ColorPicker} bound to a persisted {@code State<Color>}, a
 * {@code Color} used directly as a View (dual identity), and a reactive
 * {@code Rectangle} (a {@code SHAPE} node) that follows the picked color.
 */
public final class ColorSection implements View {

    State<Color> accent = new State<>(Color.BLUE, "accent");

    @Override
    public View body() {
        var accentLabel = computed(() -> {
            int rgb = accent.get().argb() & 0xFFFFFF;
            return String.format("Accent #%06x", rgb);
        });
        return new SectionCard("Color · ColorPicker + Color view + Rectangle",
                VStack.of(
                        ColorPicker.of(accent.signal()),
                        HStack.of(
                                accent.get().frame(120, 60, Alignment.CENTER),
                                Rectangle.of().frame(120, 60, Alignment.CENTER).with(
                                        Background.of(accent.signal()))
                        ).with(Padding.of(4)),
                        Text.of(accentLabel).with(Padding.of(4))
                ).with(Padding.of(4))
        );
    }
}