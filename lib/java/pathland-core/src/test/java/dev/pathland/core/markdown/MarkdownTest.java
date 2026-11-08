package dev.pathland.core.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pathland.core.protocol.Properties;
import dev.pathland.core.text.AttributedString;
import dev.pathland.core.text.LinkRun;
import dev.pathland.core.text.StyleRun;
import org.junit.jupiter.api.Test;

/**
 * The pure-Java parser must match the Rust {@code pathland-markdown} parser
 * exactly (the Rust test suite is mirrored here, including UTF-8 byte offsets).
 */
class MarkdownTest {

    private static AttributedString decoded(String markdown) {
        return Markdown.parse(markdown);
    }

    private static boolean hasProperty(StyleRun run, int property) {
        return run.getAttributes().stream().anyMatch(a -> a.getProperty() == property);
    }

    private static int valueOf(StyleRun run, int property) {
        return run.getAttributes().stream()
                .filter(a -> a.getProperty() == property)
                .findFirst()
                .orElseThrow()
                .getValue();
    }

    @Test
    void isAlwaysAvailable() {
        assertTrue(Markdown.isAvailable());
    }

    @Test
    void plainTextHasNoRuns() {
        AttributedString value = decoded("hello");
        assertEquals("hello", value.getText());
        assertTrue(value.getRuns().isEmpty());
        assertTrue(value.getLinks().isEmpty());
    }

    @Test
    void mapsEachInlineMarker() {
        AttributedString value = decoded("**b** *i* `c` ~~s~~");
        assertEquals("b i c s", value.getText());
        assertEquals(4, value.getRuns().size());

        StyleRun bold = value.getRuns().get(0);
        assertEquals(0, bold.getStart());
        assertEquals(1, bold.getEnd());
        assertEquals(Float.floatToRawIntBits(700.0f), valueOf(bold, Properties.FONT_WEIGHT));

        StyleRun italic = value.getRuns().get(1);
        assertEquals(2, italic.getStart());
        assertEquals(3, italic.getEnd());
        assertEquals(Float.floatToRawIntBits(1.0f), valueOf(italic, Properties.FONT_STYLE));

        StyleRun code = value.getRuns().get(2);
        assertEquals(4, code.getStart());
        assertEquals(5, code.getEnd());
        assertEquals(Float.floatToRawIntBits(3.0f), valueOf(code, Properties.FONT_DESIGN));

        StyleRun strike = value.getRuns().get(3);
        assertEquals(6, strike.getStart());
        assertEquals(7, strike.getEnd());
        assertTrue(hasProperty(strike, Properties.STRIKETHROUGH));
    }

    @Test
    void nestsFormats() {
        AttributedString value = decoded("**bold *and italic***");
        assertEquals("bold and italic", value.getText());
        assertEquals(2, value.getRuns().size());
        assertEquals(5, value.getRuns().get(0).getStart());
        assertEquals(15, value.getRuns().get(0).getEnd());
        assertEquals(0, value.getRuns().get(1).getStart());
        assertEquals(15, value.getRuns().get(1).getEnd());
    }

    @Test
    void linksCarryLabelRunsAndUrl() {
        AttributedString value = decoded("[**hi**](https://example.com)");
        assertEquals("hi", value.getText());
        assertEquals(1, value.getLinks().size());
        LinkRun link = value.getLinks().get(0);
        assertEquals("https://example.com", link.getUrl());
        assertEquals(0, link.getStart());
        assertEquals(2, link.getEnd());
        assertEquals(1, value.getRuns().size());
    }

    @Test
    void codeSpansDoNotParseInnerMarkup() {
        AttributedString value = decoded("`**not bold**`");
        assertEquals("**not bold**", value.getText());
        assertEquals(1, value.getRuns().size());
        assertTrue(hasProperty(value.getRuns().get(0), Properties.FONT_DESIGN));
    }

    @Test
    void underscoreEmphasisMatchesAsteriskForms() {
        AttributedString value = decoded("_italic_ and __bold__");
        assertEquals("italic and bold", value.getText());
        assertEquals(2, value.getRuns().size());
        assertTrue(hasProperty(value.getRuns().get(0), Properties.FONT_STYLE));
        assertTrue(hasProperty(value.getRuns().get(1), Properties.FONT_WEIGHT));
    }

    @Test
    void intrawordUnderscoresStayLiteral() {
        AttributedString value = decoded("snake_case_name");
        assertEquals("snake_case_name", value.getText());
        assertTrue(value.getRuns().isEmpty());
    }

    @Test
    void escapedUnderscoresAreLiteral() {
        AttributedString value = decoded("\\_not italic\\_");
        assertEquals("_not italic_", value.getText());
        assertTrue(value.getRuns().isEmpty());
    }

    @Test
    void escapesAreLiteral() {
        AttributedString value = decoded("\\*not italic\\*");
        assertEquals("*not italic*", value.getText());
        assertTrue(value.getRuns().isEmpty());
    }

    @Test
    void unmatchedMarkersStayLiteral() {
        AttributedString value = decoded("a * b ** c");
        assertEquals("a * b ** c", value.getText());
        assertTrue(value.getRuns().isEmpty());
    }

    @Test
    void utf8OffsetsAreBytes() {
        AttributedString value = decoded("\u00e9**\u00fc**");
        assertEquals("\u00e9\u00fc", value.getText());
        assertEquals(2, value.getRuns().get(0).getStart());
        assertEquals(4, value.getRuns().get(0).getEnd());
    }

    @Test
    void roundTripsThroughTheWirePayload() {
        AttributedString value = Markdown.parse("`code`");
        AttributedString decoded = AttributedString.decode(value.encode());
        assertEquals("code", decoded.getText());
        assertTrue(hasProperty(decoded.getRuns().get(0), Properties.FONT_DESIGN));
    }

    @Test
    void parseOrPlainSkipsPlainText() {
        assertTrue(Markdown.containsMarkdownSyntax("**hi**"));
        assertFalse(Markdown.containsMarkdownSyntax("plain text"));
        AttributedString plain = Markdown.parseOrPlain("plain text");
        assertEquals("plain text", plain.getText());
        assertTrue(plain.getRuns().isEmpty());
        AttributedString rich = Markdown.parseOrPlain("**hi**");
        assertFalse(rich.getRuns().isEmpty());
    }
}
