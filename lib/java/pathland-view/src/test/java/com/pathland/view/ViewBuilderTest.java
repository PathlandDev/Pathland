package com.pathland.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pathland.view.emit.PathlandNode;
import org.junit.jupiter.api.Test;
import static com.pathland.view.Modifiers.*;

/** The v2 authoring surface: {@code make} / {@code modifiers} / {@code children}. */
public class ViewBuilderTest {

    @Test
    void makeConfiguresText() {
        PathlandNode node = Text.make(t -> t.text("hi")).render(Environment.DEFAULT);
        assertEquals("hi", node.text);
    }

    @Test
    void modifiersAndChildrenMayBeUsedInAnyOrder() {
        View a = VStack.make(v -> v.spacing(8)).children(Text.make(t -> t.text("a")))
                .modifiers(padding(p -> p.uniform(4)));
        View b = VStack.make(v -> v.spacing(8))
                .modifiers(padding(p -> p.uniform(4))).children(Text.make(t -> t.text("a")));

        PathlandNode na = a.render(Environment.DEFAULT);
        PathlandNode nb = b.render(Environment.DEFAULT);

        assertEquals(8f, na.properties.get(Properties.SPACING));
        assertEquals(na.properties.get(Properties.SPACING), nb.properties.get(Properties.SPACING));
        assertEquals(4f, na.properties.get(Properties.PADDING));
        assertEquals(1, na.children.size());
        assertEquals(1, nb.children.size());
    }

    @Test
    void makeStartsTheChain() {
        PathlandNode node =
                VStack.make().modifiers(padding(p -> p.uniform(16)))
                        .children(Text.make(t -> t.text("x"))).render(Environment.DEFAULT);
        assertEquals(16f, node.properties.get(Properties.PADDING));
        assertTrue(node.children.size() == 1);
    }
}
