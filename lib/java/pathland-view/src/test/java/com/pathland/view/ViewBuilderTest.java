package com.pathland.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pathland.view.emit.PathlandNode;
import org.junit.jupiter.api.Test;

/** The v2 authoring surface: {@code with} / {@code modifiers} / {@code children}. */
public class ViewBuilderTest {

    @Test
    void withConfiguresText() {
        PathlandNode node = Text.with(t -> t.text("hi")).render(Environment.DEFAULT);
        assertEquals("hi", node.text);
    }

    @Test
    void operationsMayBeUsedInAnyOrder() {
        View a = VStack.with(v -> v.spacing(8)).children(Text.of("a")).modifiers(Padding.of(4));
        View b = VStack.children(Text.of("a")).modifiers(Padding.of(4)).with(v -> v.spacing(8));

        PathlandNode na = a.render(Environment.DEFAULT);
        PathlandNode nb = b.render(Environment.DEFAULT);

        assertEquals(8f, na.properties.get(Properties.SPACING));
        assertEquals(na.properties.get(Properties.SPACING), nb.properties.get(Properties.SPACING));
        assertEquals(4f, na.properties.get(Properties.PADDING));
        assertEquals(1, na.children.size());
        assertEquals(1, nb.children.size());
    }

    @Test
    void staticModifiersEntryStartsTheChain() {
        PathlandNode node =
                VStack.modifiers(Padding.of(16)).children(Text.of("x")).render(Environment.DEFAULT);
        assertEquals(16f, node.properties.get(Properties.PADDING));
        assertTrue(node.children.size() == 1);
    }
}
