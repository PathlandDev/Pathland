package dev.pathland.demo.kitchensink;

import dev.pathland.view.Frame;
import dev.pathland.view.Alignment;
import dev.pathland.view.FontDesign;
import dev.pathland.view.FontWeight;
import dev.pathland.view.Text;
import dev.pathland.view.TextAlignment;
import dev.pathland.view.TextCase;
import dev.pathland.view.Truncation;
import dev.pathland.view.VStack;
import dev.pathland.view.View;
import dev.pathland.view.Italic;
import dev.pathland.view.Kerning;
import dev.pathland.view.LineLimit;
import dev.pathland.view.Padding;
import dev.pathland.view.Strikethrough;
import dev.pathland.view.Tracking;
import dev.pathland.view.Underline;
import static dev.pathland.view.Modifiers.*;


/**
 * Text-styles section: one row per text-formatting modifier — weight, italic,
 * underline, strikethrough, case, design, kerning, tracking, and line-limit +
 * truncation.
 */
public final class TextStylesSection implements View {

    @Override
    public View body() {
        View content = VStack.make()
                .modifiers(padding(4))
                .children(
                        Text.make("Regular").modifiers(FontWeight.REGULAR),
                        Text.make("Bold").modifiers(FontWeight.BOLD),
                        Text.make("Italic").modifiers(italic()),
                        Text.make("Underline").modifiers(underline()),
                        Text.make("Strikethrough").modifiers(strikethrough()),
                        Text.make("UPPERCASE").modifiers(TextCase.UPPERCASE),
                        Text.make("Monospaced").modifiers(FontDesign.MONOSPACED),
                        Text.make("Serif").modifiers(FontDesign.SERIF),
                        Text.make("K e r n e d").modifiers(kerning(2)),
                        Text.make("Tracking").modifiers(tracking(3)),
                        Text.make("A very long line of text that must be truncated to a single line.").modifiers(
                                lineLimit(1),
                                Truncation.TAIL,
                                frame(f -> f.width(280).alignment(Alignment.CENTER))),
                        Text.make("Centered").modifiers(
                                TextAlignment.CENTER,
                                frame(f -> f.width(180).alignment(Alignment.CENTER))));
        return SectionCard.make("Text styles · text modifiers").children(content);
    }
}