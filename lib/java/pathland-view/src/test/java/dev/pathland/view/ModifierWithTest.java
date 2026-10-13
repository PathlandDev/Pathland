package dev.pathland.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.pathland.view.emit.PathlandNode;
import org.junit.jupiter.api.Test;
import static dev.pathland.view.Modifiers.*;

/** The modifier surface: the {@code Modifiers.*} lowercase functions and value-is-modifier. */
public class ModifierWithTest {

    @Test
    void parameterizedModifierUsesWith() {
        PathlandNode node = Text.make(t -> t.text("hi"))
                .modifiers(padding(p -> p.uniform(12)))
                .render(Environment.DEFAULT);
        assertEquals(12f, node.properties.get(Properties.PADDING));
    }

    @Test
    void perEdgePaddingUsesWith() {
        PathlandNode node = Text.make(t -> t.text("hi"))
                .modifiers(padding(p -> p.edges(1, 2, 3, 4)))
                .render(Environment.DEFAULT);
        assertEquals(1f, node.properties.get(Properties.PADDING_TOP));
        assertEquals(2f, node.properties.get(Properties.PADDING_RIGHT));
        assertEquals(3f, node.properties.get(Properties.PADDING_BOTTOM));
        assertEquals(4f, node.properties.get(Properties.PADDING_LEFT));
    }

    @Test
    void foregroundStyleWith() {
        PathlandNode node = Text.make(t -> t.text("hi"))
                .modifiers(foregroundStyle(f -> f.color(Color.WHITE)))
                .render(Environment.DEFAULT);
        assertEquals(Color.WHITE, node.properties.get(Properties.COLOR));
    }

    @Test
    void enumIsItsOwnModifier() {
        PathlandNode node = Text.make(t -> t.text("hi"))
                .modifiers(FontWeight.BOLD)
                .render(Environment.DEFAULT);
        assertEquals(700f, node.properties.get(Properties.FONT_WEIGHT));
    }

    @Test
    void fontValueIsItsOwnModifier() {
        PathlandNode node = Text.make(t -> t.text("hi"))
                .modifiers(Font.system(18f))
                .render(Environment.DEFAULT);
        assertEquals(18f, node.properties.get(Properties.FONT_SIZE));
    }

    @Test
    void frameWithBuilder() {
        PathlandNode node = Text.make(t -> t.text("hi"))
                .modifiers(frame(f -> f.width(100).height(24)))
                .render(Environment.DEFAULT);
        assertEquals(100f, node.properties.get(Properties.WIDTH));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
    }
}
