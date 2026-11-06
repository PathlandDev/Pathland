package dev.pathland.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pathland.view.core.AttributedString;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MarkdownTest {

    @Test
    void parsesInlineMarkdown() {
        Assumptions.assumeTrue(Markdown.isAvailable(), "native markdown parser not built");
        AttributedString value = Markdown.parse("**hi** [x](y)");
        assertEquals("hi x", value.getText());
        assertEquals(1, value.getLinks().size());
        assertEquals(1, value.getRuns().size());
    }

    @Test
    void parsesUnderscoreEmphasis() {
        Assumptions.assumeTrue(Markdown.isAvailable(), "native markdown parser not built");
        AttributedString value = Markdown.parse("_italic_ and __bold__");
        assertEquals("italic and bold", value.getText());
        assertEquals(2, value.getRuns().size());
    }

    @Test
    void roundTripsThroughTheWirePayload() {
        Assumptions.assumeTrue(Markdown.isAvailable(), "native markdown parser not built");
        AttributedString value = Markdown.parse("`code`");
        AttributedString decoded = AttributedString.decode(value.encode());
        assertEquals("code", decoded.getText());
        assertTrue(decoded.getRuns().get(0).getAttributes().stream()
                .anyMatch(a -> a.getProperty() == 0x1018));
    }
}
