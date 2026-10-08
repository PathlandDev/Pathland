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
                        Text.of("Regular").with(FontWeightMod.of(FontWeight.REGULAR)),
                        Text.of("Bold").with(FontWeightMod.of(FontWeight.BOLD)),
                        Text.of("Italic").with(Italic.of()),
                        Text.of("Underline").with(Underline.of()),
                        Text.of("Strikethrough").with(Strikethrough.of()),
                        Text.of("UPPERCASE").with(TextCaseMod.of(TextCase.UPPERCASE)),
                        Text.of("Monospaced").with(FontDesignMod.of(FontDesign.MONOSPACED)),
                        Text.of("Serif").with(FontDesignMod.of(FontDesign.SERIF)),
                        Text.of("K e r n e d").with(Kerning.of(2)),
                        Text.of("Tracking").with(Tracking.of(3)),
                        Text.of("A very long line of text that must be truncated to a single line.")
                                .with(
                                        LineLimit.of(1),
                                        TruncationMod.of(Truncation.TAIL))
                                .frame(280, Alignment.CENTER),
                        Text.of("Centered").with(
                                TextAlignmentMod.of(TextAlignment.CENTER))
                                .frame(180, Alignment.CENTER)
                ).with(Padding.of(4))
        );
    }
}