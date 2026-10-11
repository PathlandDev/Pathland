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
        View content = VStack
                .modifiers(Padding.with(4))
                .children(
                        Grid.children(
                                cell("1"), cell("2"), cell("3"),
                                cell("4"), cell("5"), cell("6")),
                        LazyVStack
                                .modifiers(Padding.with(4))
                                .children(
                                        Text.with("Lazy row A"),
                                        Text.with("Lazy row B"),
                                        Text.with("Lazy row C")),
                        HStack
                                .modifiers(Frame.with(f -> f.width(260).alignment(Alignment.CENTER)))
                                .children(
                                        Text.with("Left"),
                                        Spacer.modifiers(),
                                        Text.with("Right")),
                        ZStack
                                .modifiers(Padding.with(4))
                                .children(
                                        Rectangle.modifiers(Frame.with(f -> f.width(180).height(80).alignment(Alignment.CENTER)))
                                                .modifiers(
                                                        Background.with(Color.rgb(0xE3, 0xF2, 0xFD)),
                                                        CornerRadius.with(8)),
                                        Text.with("badge").modifiers(
                                                FontSize.with(12),
                                                Padding.with(4),
                                                Background.with(Color.rgb(0xFF, 0xC1, 0x07)),
                                                CornerRadius.with(4),
                                                Offset.with(o -> o.x(0).y(28)))),
                        Divider.modifiers());
        return SectionCard.with("Layout · Grid / Lazy / HStack+Spacer / ZStack / Divider").children(content);
    }

    private static View cell(String label) {
        return Text.with(label).modifiers(
                Padding.with(12),
                Background.with(Color.rgb(0xF3, 0xF4, 0xF6)),
                CornerRadius.with(6));
    }
}