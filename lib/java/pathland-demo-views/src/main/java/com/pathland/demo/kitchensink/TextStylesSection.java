package com.pathland.demo.kitchensink;

import com.pathland.view.Frame;
import com.pathland.view.Alignment;
import com.pathland.view.FontDesign;
import com.pathland.view.FontWeight;
import com.pathland.view.Text;
import com.pathland.view.TextAlignment;
import com.pathland.view.TextCase;
import com.pathland.view.Truncation;
import com.pathland.view.VStack;
import com.pathland.view.View;
import com.pathland.view.Italic;
import com.pathland.view.Kerning;
import com.pathland.view.LineLimit;
import com.pathland.view.Padding;
import com.pathland.view.Strikethrough;
import com.pathland.view.Tracking;
import com.pathland.view.Underline;


/**
 * Text-styles section: one row per text-formatting modifier — weight, italic,
 * underline, strikethrough, case, design, kerning, tracking, and line-limit +
 * truncation.
 */
public final class TextStylesSection implements View {

    @Override
    public View body() {
        View content = VStack
                .modifiers(Padding.with(4))
                .children(
                        Text.with("Regular").modifiers(FontWeight.REGULAR),
                        Text.with("Bold").modifiers(FontWeight.BOLD),
                        Text.with("Italic").modifiers(Italic.with()),
                        Text.with("Underline").modifiers(Underline.with()),
                        Text.with("Strikethrough").modifiers(Strikethrough.with()),
                        Text.with("UPPERCASE").modifiers(TextCase.UPPERCASE),
                        Text.with("Monospaced").modifiers(FontDesign.MONOSPACED),
                        Text.with("Serif").modifiers(FontDesign.SERIF),
                        Text.with("K e r n e d").modifiers(Kerning.with(2)),
                        Text.with("Tracking").modifiers(Tracking.with(3)),
                        Text.with("A very long line of text that must be truncated to a single line.").modifiers(
                                LineLimit.with(1),
                                Truncation.TAIL,
                                Frame.with(f -> f.width(280).alignment(Alignment.CENTER))),
                        Text.with("Centered").modifiers(
                                TextAlignment.CENTER,
                                Frame.with(f -> f.width(180).alignment(Alignment.CENTER))));
        return SectionCard.with("Text styles · text modifiers").children(content);
    }
}