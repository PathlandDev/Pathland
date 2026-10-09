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
        return new SectionCard("Layout · Grid / Lazy / HStack+Spacer / ZStack / Divider",
                VStack.children(
                        Grid.children(
                                cell("1"), cell("2"), cell("3"),
                                cell("4"), cell("5"), cell("6")),
                        LazyVStack.children(
                                Text.with(t -> t.text("Lazy row A")),
                                Text.with(t -> t.text("Lazy row B")),
                                Text.with(t -> t.text("Lazy row C"))).modifiers(Padding.with(p -> p.uniform(4))),
                        HStack.children(
                                Text.with(t -> t.text("Left")),
                                Spacer.modifiers(),
                                Text.with(t -> t.text("Right")))
                                .modifiers(Frame.with(f -> f.width(260).alignment(Alignment.CENTER))),
                        ZStack.children(
                                Rectangle.modifiers(Frame.with(f -> f.width(180).height(80).alignment(Alignment.CENTER))).modifiers(
                                        Background.with(b -> b.color(Color.rgb(0xE3, 0xF2, 0xFD))),
                                        CornerRadius.with(c -> c.radius(8))),
                                Text.with(t -> t.text("badge")).modifiers(
                                        FontSize.with(s -> s.size(12)),
                                        Padding.with(p -> p.uniform(4)),
                                        Background.with(b -> b.color(Color.rgb(0xFF, 0xC1, 0x07))),
                                        CornerRadius.with(c -> c.radius(4)),
                                        Offset.with(o -> o.x(0).y(28)))
                        ).modifiers(Padding.with(p -> p.uniform(4))),
                        Divider.modifiers()
                ).modifiers(Padding.with(p -> p.uniform(4)))
        );
    }

    private static View cell(String label) {
        return Text.with(t -> t.text(label)).modifiers(
                Padding.with(p -> p.uniform(12)),
                Background.with(b -> b.color(Color.rgb(0xF3, 0xF4, 0xF6))),
                CornerRadius.with(c -> c.radius(6)));
    }
}