package dev.pathland.demo.kitchensink;

import dev.pathland.view.Frame;
import dev.pathland.view.Alignment;
import dev.pathland.view.Color;
import dev.pathland.view.Divider;
import dev.pathland.view.Grid;
import dev.pathland.view.HStack;
import dev.pathland.view.LazyVStack;
import dev.pathland.view.Rectangle;
import dev.pathland.view.Spacer;
import dev.pathland.view.Text;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import dev.pathland.view.ZStack;
import dev.pathland.view.Background;
import dev.pathland.view.CornerRadius;
import dev.pathland.view.FontSize;
import dev.pathland.view.Offset;
import dev.pathland.view.Padding;
import static dev.pathland.view.Modifiers.*;


/**
 * Layout section: the container primitives — {@code Grid}, {@code LazyVStack},
 * {@code HStack} + {@code Spacer}, {@code ZStack}, and {@code Divider}.
 */
public final class LayoutSection implements View {

    @Override
    public View body() {
        View content = VStack.make()
                .modifiers(padding(4))
                .children(
                        Grid.children(
                                cell("1"), cell("2"), cell("3"),
                                cell("4"), cell("5"), cell("6")),
                        LazyVStack.make() .modifiers(padding(4))
                                .children(
                                        Text.make("Lazy row A"),
                                        Text.make("Lazy row B"),
                                        Text.make("Lazy row C")),
                        HStack.make() .modifiers(frame(f -> f.width(260).alignment(Alignment.CENTER)))
                                .children(
                                        Text.make("Left"),
Spacer.make().modifiers(),
                                        Text.make("Right")),
                        ZStack.make() .modifiers(padding(4))
                                .children(
Rectangle.make().modifiers(frame(f -> f.width(180).height(80).alignment(Alignment.CENTER)))
                                                .modifiers(
                                                        background(Color.rgb(0xE3, 0xF2, 0xFD)),
                                                        cornerRadius(8)),
                                        Text.make("badge").modifiers(
                                                fontSize(12),
                                                padding(4),
                                                background(Color.rgb(0xFF, 0xC1, 0x07)),
                                                cornerRadius(4),
                                                offset(o -> o.x(0).y(28)))),
Divider.make().modifiers());
        return SectionCard.make("Layout · Grid / Lazy / HStack+Spacer / ZStack / Divider").children(content);
    }

    private static View cell(String label) {
        return Text.make(label).modifiers(
                padding(12),
                background(Color.rgb(0xF3, 0xF4, 0xF6)),
                cornerRadius(6));
    }
}