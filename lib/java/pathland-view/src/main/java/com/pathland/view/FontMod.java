package com.pathland.view;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code font} modifier (SwiftUI {@code .font(_:)}): applies a {@link Font}
 * — a predefined typography ({@code Font.headline()}, … → {@code TEXT_STYLE}), a
 * custom family + size (→ {@code FONT_FAMILY} + {@code FONT_SIZE}), or a system
 * size/weight/design (→ {@code FONT_SIZE} + {@code FONT_WEIGHT} +
 * {@code FONT_DESIGN}).
 */
public final class FontMod implements ViewModifier {

    private final Font font;

    private FontMod(Font font) {
        this.font = font;
    }

    public static FontMod of(Font font) {
        return new FontMod(font);
    }

    @Override
    public View body(View content) {
        if (font.style() != null) {
            // Predefined typography: the heading styles imply a heading element.
            return Modified.props(content, Modified.prop(Properties.TEXT_STYLE, (float) font.style().code()));
        }
        List<Modified.Prop> props = new ArrayList<>(4);
        if (font.family() != null) {
            props.add(Modified.prop(Properties.FONT_FAMILY, font.family()));
        }
        if (font.size() != null) {
            props.add(Modified.prop(Properties.FONT_SIZE, font.size()));
        }
        if (font.weight() != null) {
            props.add(Modified.prop(Properties.FONT_WEIGHT, (float) font.weight().wire()));
        }
        if (font.design() != null) {
            props.add(Modified.prop(Properties.FONT_DESIGN, (float) font.design().wire()));
        }
        return Modified.props(content, props.toArray(new Modified.Prop[0]));
    }
}