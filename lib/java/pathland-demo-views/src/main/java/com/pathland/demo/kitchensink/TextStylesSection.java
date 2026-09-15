package com.pathland.demo.kitchensink;

import com.pathland.view.Alignment;
import com.pathland.view.FontDesign;
import com.pathland.view.FontWeight;
import com.pathland.view.Text;
import com.pathland.view.TextAlignment;
import com.pathland.view.TextCase;
import com.pathland.view.Truncation;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.FontDesignMod;
import com.pathland.view.FontWeightMod;
import com.pathland.view.FrameMod;
import com.pathland.view.Italic;
import com.pathland.view.Kerning;
import com.pathland.view.LineLimit;
import com.pathland.view.Padding;
import com.pathland.view.Strikethrough;
import com.pathland.view.TextAlignmentMod;
import com.pathland.view.TextCaseMod;
import com.pathland.view.Tracking;
import com.pathland.view.TruncationMod;
import com.pathland.view.Underline;


/**
 * Text-styles section: one row per text-formatting modifier — weight, italic,
 * underline, strikethrough, case, design, kerning, tracking, and line-limit +
 * truncation.
 */
public final class TextStylesSection implements View {

    @Override
    public View body() {
        return new SectionCard("Text styles · text modifiers",
                VStack.of(
                        Text.of("Regular").modifier(FontWeightMod.of(FontWeight.REGULAR)),
                        Text.of("Bold").modifier(FontWeightMod.of(FontWeight.BOLD)),
                        Text.of("Italic").modifier(Italic.of()),
                        Text.of("Underline").modifier(Underline.of()),
                        Text.of("Strikethrough").modifier(Strikethrough.of()),
                        Text.of("UPPERCASE").modifier(TextCaseMod.of(TextCase.UPPERCASE)),
                        Text.of("Monospaced").modifier(FontDesignMod.of(FontDesign.MONOSPACED)),
                        Text.of("Serif").modifier(FontDesignMod.of(FontDesign.SERIF)),
                        Text.of("K e r n e d").modifier(Kerning.of(2)),
                        Text.of("Tracking").modifier(Tracking.of(3)),
                        Text.of("A very long line of text that must be truncated to a single line.")
                                .modifiers(
                                        LineLimit.of(1),
                                        TruncationMod.of(Truncation.TAIL),
                                        FrameMod.of(280, Float.NaN, Alignment.CENTER)),
                        Text.of("Centered").modifiers(
                                TextAlignmentMod.of(TextAlignment.CENTER),
                                FrameMod.of(180, Float.NaN, Alignment.CENTER))
                ).modifier(Padding.of(4))
        );
    }
}