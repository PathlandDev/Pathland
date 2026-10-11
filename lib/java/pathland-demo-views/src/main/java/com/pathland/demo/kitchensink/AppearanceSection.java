package com.pathland.demo.kitchensink;

import com.pathland.view.Frame;
import com.pathland.view.Alignment;
import com.pathland.view.Color;
import com.pathland.view.HStack;
import com.pathland.view.Rectangle;
import com.pathland.view.ShapeKind;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.Background;
import com.pathland.view.Border;
import com.pathland.view.ClipShape;
import com.pathland.view.CornerRadius;
import com.pathland.view.Hidden;
import com.pathland.view.Padding;
import com.pathland.view.Rotation;
import com.pathland.view.ScaleEffect;
import com.pathland.view.Shadow;
import com.pathland.view.ZIndex;


/**
 * Appearance section: the appearance and transform modifiers — border, shadow,
 * opacity, rotation, scale, hidden, clip shape, corner radius, and z-index.
 */
public final class AppearanceSection implements View {

    private static final Color BLUE_200 = Color.rgb(0x90, 0xCA, 0xF9);
    private static final Color RED_50 = Color.rgb(0xFD, 0xE2, 0xE4);
    private static final Color GREEN_50 = Color.rgb(0xD1, 0xE7, 0xDD);

    @Override
    public View body() {
        View content = VStack.make()
                .modifiers(Padding.with(4))
                .children(
                        Text.make("Card with shadow").modifiers(
                                Padding.with(20),
                                Background.with(Color.WHITE),
                                Border.with(b -> b.color(BLUE_200).width(2).radius(12)),
                                CornerRadius.with(12),
                                Shadow.with(s -> s.color(Color.rgb(0, 0, 0)).radius(6).x(2).y(4))),
                        Text.make("Rotated 6°").modifiers(
                                Rotation.with(6),
                                Padding.with(8),
                                Background.with(RED_50),
                                CornerRadius.with(6)),
                        Text.make("Scaled 1.2×").modifiers(
                                ScaleEffect.with(1.2f),
                                Padding.with(8),
                                Background.with(GREEN_50),
                                CornerRadius.with(6)),
                        Text.make("This text is hidden").modifiers(Hidden.with()),
                        HStack.make() .modifiers(Padding.with(4))
                                .children(
Rectangle.make().modifiers(Frame.with(f -> f.width(90).height(60).alignment(Alignment.CENTER)))
                                                .modifiers(Background.with(Color.RED),
                                                        ClipShape.with(ShapeKind.CIRCLE)),
Rectangle.make().modifiers(Frame.with(f -> f.width(90).height(60).alignment(Alignment.CENTER)))
                                                .modifiers(
                                                        Background.with(Color.BLUE),
                                                        CornerRadius.with(12))),
                        Text.make("zIndex above").modifiers(ZIndex.with(3)));
        return SectionCard.make("Appearance · border / shadow / opacity / transform").children(content);
    }
}