package com.pathland.demo.kitchensink;

import com.pathland.view.Alignment;
import com.pathland.view.Color;
import com.pathland.view.Commands;
import com.pathland.view.Divider;
import com.pathland.view.FontWeight;
import com.pathland.view.Text;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.Background;
import com.pathland.view.Border;
import com.pathland.view.CornerRadius;
import com.pathland.view.FontSize;
import com.pathland.view.FontWeightMod;
import com.pathland.view.ForegroundStyle;
import com.pathland.view.FrameMod;
import com.pathland.view.Padding;


/**
 * A reusable kitchen-sink section card: a titled, bordered, rounded container that
 * hosts one section's controls. Demonstrates composition — a custom {@code View}
 * wrapping arbitrary content with style modifiers.
 */
public final class SectionCard implements View {

    private static final Color TITLE_COLOR = Color.rgb(0x55, 0x64, 0x6D);
    private static final Color BORDER = Color.rgb(0xE2, 0xE8, 0xF0);

    private final String title;
    private final View content;

    public SectionCard(String title, View content) {
        this.title = title;
        this.content = content;
    }

    @Override
    public View body() {
        return VStack.of(
                Text.of(title).modifiers(FontSize.of(14), FontWeightMod.of(FontWeight.SEMIBOLD), ForegroundStyle.of(TITLE_COLOR)),
                Divider.of().modifier(Padding.of(0, 0, 8, 0)),
                content
        ).modifiers(
                Padding.of(16),
                Background.of(Color.WHITE),
                Border.of(BORDER, 1, 10),
                CornerRadius.of(10),
                FrameMod.of(Commands.Size.FILL, Float.NaN, Alignment.FILL));
    }
}