package com.pathland.demo.kitchensink;

import com.pathland.view.Frame;
import com.pathland.view.Alignment;
import com.pathland.view.Color;
import com.pathland.view.Divider;
import com.pathland.view.Grid;
import com.pathland.view.HStack;
import com.pathland.view.LazyVStack;
import com.pathland.view.Rectangle;
import com.pathland.view.Spacer;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.ZStack;
import com.pathland.view.Background;
import com.pathland.view.CornerRadius;
import com.pathland.view.FontSize;
import com.pathland.view.Offset;
import com.pathland.view.Padding;


/**
 * Layout section: the container primitives — {@code Grid}, {@code LazyVStack},
 * {@code HStack} + {@code Spacer}, {@code ZStack}, and {@code Divider}.
 */
public final class LayoutSection implements View {

    @Override
    public View body() {
        View content = VStack.make()
                .modifiers(Padding.with(4))
                .children(
                        Grid.children(
                                cell("1"), cell("2"), cell("3"),
                                cell("4"), cell("5"), cell("6")),
                        LazyVStack.make() .modifiers(Padding.with(4))
                                .children(
                                        Text.make("Lazy row A"),
                                        Text.make("Lazy row B"),
                                        Text.make("Lazy row C")),
                        HStack.make() .modifiers(Frame.with(f -> f.width(260).alignment(Alignment.CENTER)))
                                .children(
                                        Text.make("Left"),
Spacer.make().modifiers(),
                                        Text.make("Right")),
                        ZStack.make() .modifiers(Padding.with(4))
                                .children(
Rectangle.make().modifiers(Frame.with(f -> f.width(180).height(80).alignment(Alignment.CENTER)))
                                                .modifiers(
                                                        Background.with(Color.rgb(0xE3, 0xF2, 0xFD)),
                                                        CornerRadius.with(8)),
                                        Text.make("badge").modifiers(
                                                FontSize.with(12),
                                                Padding.with(4),
                                                Background.with(Color.rgb(0xFF, 0xC1, 0x07)),
                                                CornerRadius.with(4),
                                                Offset.with(o -> o.x(0).y(28)))),
Divider.make().modifiers());
        return SectionCard.make("Layout · Grid / Lazy / HStack+Spacer / ZStack / Divider").children(content);
    }

    private static View cell(String label) {
        return Text.make(label).modifiers(
                Padding.with(12),
                Background.with(Color.rgb(0xF3, 0xF4, 0xF6)),
                CornerRadius.with(6));
    }
}