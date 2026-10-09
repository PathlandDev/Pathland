package com.pathland.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pathland.view.emit.PathlandNode;
import org.junit.jupiter.api.Test;

/** The v2 modifier surface: {@code Modifier.with(...)} and value-is-modifier. */
public class ModifierWithTest {

    @Test
    void parameterizedModifierUsesWith() {
        PathlandNode node = Text.with(t -> t.text("hi"))
                .modifiers(Padding.with(p -> p.uniform(12)))
                .render(Environment.DEFAULT);
        assertEquals(12f, node.properties.get(Properties.PADDING));
    }

    @Test
    void perEdgePaddingUsesWith() {
        PathlandNode node = Text.with(t -> t.text("hi"))
                .modifiers(Padding.with(p -> p.edges(1, 2, 3, 4)))
                .render(Environment.DEFAULT);
        assertEquals(1f, node.properties.get(Properties.PADDING_TOP));
        assertEquals(2f, node.properties.get(Properties.PADDING_RIGHT));
        assertEquals(3f, node.properties.get(Properties.PADDING_BOTTOM));
        assertEquals(4f, node.properties.get(Properties.PADDING_LEFT));
    }

    @Test
    void foregroundStyleWith() {
        PathlandNode node = Text.with(t -> t.text("hi"))
                .modifiers(ForegroundStyle.with(f -> f.color(Color.WHITE)))
                .render(Environment.DEFAULT);
        assertEquals(Color.WHITE, node.properties.get(Properties.COLOR));
    }

    @Test
    void enumIsItsOwnModifier() {
        PathlandNode node = Text.with(t -> t.text("hi"))
                .modifiers(FontWeight.BOLD)
                .render(Environment.DEFAULT);
        assertEquals(700f, node.properties.get(Properties.FONT_WEIGHT));
    }

    @Test
    void fontValueIsItsOwnModifier() {
        PathlandNode node = Text.with(t -> t.text("hi"))
                .modifiers(Font.system(18f))
                .render(Environment.DEFAULT);
        assertEquals(18f, node.properties.get(Properties.FONT_SIZE));
    }

    @Test
    void frameWithBuilder() {
        PathlandNode node = Text.with(t -> t.text("hi"))
                .modifiers(Frame.with(f -> f.width(100).height(24)))
                .render(Environment.DEFAULT);
        assertEquals(100f, node.properties.get(Properties.WIDTH));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
    }
}
