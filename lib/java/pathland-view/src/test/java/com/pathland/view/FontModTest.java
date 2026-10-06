package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The {@code font} modifier: predefined typographies and custom fonts. */
class FontModTest {

    private static PathlandNode render(Font font) {
        return FontMod.of(font).body(Text.of("x")).render(Environment.DEFAULT);
    }

    @Test
    void predefinedTypographyEmitsTextStyle() {
        PathlandNode node = render(Font.headline());
        assertEquals((float) TextStyle.HEADLINE.code(), node.properties.get(Properties.TEXT_STYLE));
    }

    @Test
    void customFamilyAndSizeEmitsFamilyAndSize() {
        PathlandNode node = render(Font.custom("Georgia", 20f));
        assertEquals("Georgia", node.properties.get(Properties.FONT_FAMILY));
        assertEquals(20f, node.properties.get(Properties.FONT_SIZE));
    }

    @Test
    void combinedCustomEmitsEveryAxis() {
        PathlandNode node = render(Font.custom("Georgia", 20f, FontWeight.BOLD, FontDesign.SERIF));
        assertEquals("Georgia", node.properties.get(Properties.FONT_FAMILY));
        assertEquals(20f, node.properties.get(Properties.FONT_SIZE));
        assertEquals((float) FontWeight.BOLD.wire(), node.properties.get(Properties.FONT_WEIGHT));
        assertEquals((float) FontDesign.SERIF.wire(), node.properties.get(Properties.FONT_DESIGN));
    }

    @Test
    void systemSizeWeightDesignEmitsNumericAxes() {
        PathlandNode node = render(Font.system(18f, FontWeight.SEMIBOLD, FontDesign.MONOSPACED));
        assertEquals(18f, node.properties.get(Properties.FONT_SIZE));
        assertEquals((float) FontWeight.SEMIBOLD.wire(), node.properties.get(Properties.FONT_WEIGHT));
        assertEquals((float) FontDesign.MONOSPACED.wire(), node.properties.get(Properties.FONT_DESIGN));
    }
}
