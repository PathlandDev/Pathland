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
        return new SectionCard("Text styles · text modifiers",
                VStack.children(
                        Text.with(t -> t.text("Regular")).modifiers(FontWeight.REGULAR),
                        Text.with(t -> t.text("Bold")).modifiers(FontWeight.BOLD),
                        Text.with(t -> t.text("Italic")).modifiers(Italic.with()),
                        Text.with(t -> t.text("Underline")).modifiers(Underline.with()),
                        Text.with(t -> t.text("Strikethrough")).modifiers(Strikethrough.with()),
                        Text.with(t -> t.text("UPPERCASE")).modifiers(TextCase.UPPERCASE),
                        Text.with(t -> t.text("Monospaced")).modifiers(FontDesign.MONOSPACED),
                        Text.with(t -> t.text("Serif")).modifiers(FontDesign.SERIF),
                        Text.with(t -> t.text("K e r n e d")).modifiers(Kerning.with(k -> k.value(2))),
                        Text.with(t -> t.text("Tracking")).modifiers(Tracking.with(tr -> tr.value(3))),
                        Text.with(t -> t.text("A very long line of text that must be truncated to a single line."))
                                .modifiers(
                                        LineLimit.with(l -> l.value(1)),
                                        Truncation.TAIL)
                                .modifiers(Frame.with(f -> f.width(280).alignment(Alignment.CENTER))),
                        Text.with(t -> t.text("Centered")).modifiers(
                                TextAlignment.CENTER)
                                .modifiers(Frame.with(f -> f.width(180).alignment(Alignment.CENTER)))
                ).modifiers(Padding.with(p -> p.uniform(4)))
        );
    }
}