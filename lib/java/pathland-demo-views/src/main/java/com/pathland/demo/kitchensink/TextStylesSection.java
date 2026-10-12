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
        View content = VStack.make()
                .modifiers(Padding.with(4))
                .children(
                        Text.make("Regular").modifiers(FontWeight.REGULAR),
                        Text.make("Bold").modifiers(FontWeight.BOLD),
                        Text.make("Italic").modifiers(Italic.with()),
                        Text.make("Underline").modifiers(Underline.with()),
                        Text.make("Strikethrough").modifiers(Strikethrough.with()),
                        Text.make("UPPERCASE").modifiers(TextCase.UPPERCASE),
                        Text.make("Monospaced").modifiers(FontDesign.MONOSPACED),
                        Text.make("Serif").modifiers(FontDesign.SERIF),
                        Text.make("K e r n e d").modifiers(Kerning.with(2)),
                        Text.make("Tracking").modifiers(Tracking.with(3)),
                        Text.make("A very long line of text that must be truncated to a single line.").modifiers(
                                LineLimit.with(1),
                                Truncation.TAIL,
                                Frame.with(f -> f.width(280).alignment(Alignment.CENTER))),
                        Text.make("Centered").modifiers(
                                TextAlignment.CENTER,
                                Frame.with(f -> f.width(180).alignment(Alignment.CENTER))));
        return SectionCard.make("Text styles · text modifiers").children(content);
    }
}