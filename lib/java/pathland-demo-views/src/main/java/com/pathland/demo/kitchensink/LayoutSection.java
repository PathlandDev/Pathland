package com.pathland.demo.kitchensink;

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
import com.pathland.view.FrameMod;
import com.pathland.view.Offset;
import com.pathland.view.Padding;


/**
 * Layout section: the container primitives — {@code Grid}, {@code LazyVStack},
 * {@code HStack} + {@code Spacer}, {@code ZStack}, and {@code Divider}.
 */
public final class LayoutSection implements View {

    @Override
    public View body() {
        return new SectionCard("Layout · Grid / Lazy / HStack+Spacer / ZStack / Divider",
                VStack.of(
                        Grid.of(
                                cell("1"), cell("2"), cell("3"),
                                cell("4"), cell("5"), cell("6")),
                        LazyVStack.of(
                                Text.of("Lazy row A"),
                                Text.of("Lazy row B"),
                                Text.of("Lazy row C")).modifier(Padding.of(4)),
                        HStack.of(
                                Text.of("Left"),
                                Spacer.of(),
                                Text.of("Right"))
                                .modifier(FrameMod.of(260, Float.NaN, Alignment.CENTER)),
                        ZStack.of(
                                Rectangle.of().modifiers(
                                        FrameMod.of(180, 80, Alignment.CENTER),
                                        Background.of(Color.rgb(0xE3, 0xF2, 0xFD)),
                                        CornerRadius.of(8)),
                                Text.of("badge").modifiers(
                                        FontSize.of(12),
                                        Padding.of(4),
                                        Background.of(Color.rgb(0xFF, 0xC1, 0x07)),
                                        CornerRadius.of(4),
                                        Offset.of(0, 28))
                        ).modifier(Padding.of(4)),
                        Divider.of()
                ).modifier(Padding.of(4))
        );
    }

    private static View cell(String label) {
        return Text.of(label).modifiers(
                Padding.of(12),
                Background.of(Color.rgb(0xF3, 0xF4, 0xF6)),
                CornerRadius.of(6));
    }
}