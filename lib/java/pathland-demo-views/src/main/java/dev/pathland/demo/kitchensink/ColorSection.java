package dev.pathland.demo.kitchensink;

import dev.pathland.view.Frame;
import dev.pathland.view.Alignment;
import dev.pathland.view.Color;
import dev.pathland.view.ColorPicker;
import dev.pathland.view.HStack;
import dev.pathland.view.Rectangle;
import dev.pathland.view.Text;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import static dev.pathland.view.signal.Signals.*;
import dev.pathland.view.state.State;
import dev.pathland.view.Background;
import dev.pathland.view.Padding;
import static dev.pathland.view.Modifiers.*;


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
        View content = VStack.make()
                .modifiers(padding(4))
                .children(
                        ColorPicker.make(c -> c.selection(accent.signal())),
                        HStack.make() .modifiers(padding(4))
                                .children(
                                        accent.get().modifiers(frame(f -> f.width(120).height(60).alignment(Alignment.CENTER))),
Rectangle.make().modifiers(frame(f -> f.width(120).height(60).alignment(Alignment.CENTER)))
                                                .modifiers(background(accent.signal()))),
                        Text.make(accentLabel).modifiers(
                                padding(4)));
        return SectionCard.make("Color · ColorPicker + Color view + Rectangle").children(content);
    }
}