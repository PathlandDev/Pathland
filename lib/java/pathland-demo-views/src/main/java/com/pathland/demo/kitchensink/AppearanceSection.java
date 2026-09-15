package com.pathland.demo.kitchensink;

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
import com.pathland.view.FrameMod;
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
        return new SectionCard("Appearance · border / shadow / opacity / transform",
                VStack.of(
                        Text.of("Card with shadow").modifiers(
                                        Padding.of(20),
                                        Background.of(Color.WHITE),
                                        Border.of(BLUE_200, 2, 12),
                                        CornerRadius.of(12),
                                        Shadow.of(Color.rgb(0, 0, 0), 6, 2, 4)),
                        Text.of("Rotated 6°").modifiers(Rotation.of(6), Padding.of(8))
                                .modifiers(Background.of(RED_50), CornerRadius.of(6)),
                        Text.of("Scaled 1.2×").modifiers(ScaleEffect.of(1.2f), Padding.of(8))
                                .modifiers(Background.of(GREEN_50), CornerRadius.of(6)),
                        Text.of("This text is hidden").modifier(Hidden.of()),
                        HStack.of(
                                Rectangle.of().modifiers(
                                        FrameMod.of(90, 60, Alignment.CENTER),
                                        Background.of(Color.RED),
                                        ClipShape.of(ShapeKind.CIRCLE)),
                                Rectangle.of().modifiers(
                                        FrameMod.of(90, 60, Alignment.CENTER),
                                        Background.of(Color.BLUE),
                                        CornerRadius.of(12))
                        ).modifier(Padding.of(4)),
                        Text.of("zIndex above").modifier(ZIndex.of(3))
                ).modifier(Padding.of(4))
        );
    }
}