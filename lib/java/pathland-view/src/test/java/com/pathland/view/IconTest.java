package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code Icon} primitive + semantic {@link IconName} vocabulary. */
class IconTest {

    @Test
    void rendersIconComponentWithCanonicalName() {
        PathlandNode node = Icon.with(i -> i.name(IconName.PLAY)).render(Environment.DEFAULT);
        assertEquals(Components.ICON, node.component);
        assertEquals("play", node.properties.get(Properties.ICON_NAME));
    }

    @Test
    void labeledIconCarriesAccessibilityLabel() {
        PathlandNode node = Icon.with(i -> i.name(IconName.MUSIC).label("Playlist")).render(Environment.DEFAULT);
        assertEquals("Playlist", node.properties.get(Properties.LABEL));
    }

    @Test
    void labelWithIconComposesTheIconView() {
        View label = Label.with(l -> l.title("Home").icon(Icon.with(i -> i.name(IconName.HOME))));
        PathlandNode node = label.render(Environment.DEFAULT);
        assertTrue(containsComponent(node, Components.ICON), "the label composes the Icon view");
    }

    private static boolean containsComponent(PathlandNode node, int component) {
        if (node.component == component) {
            return true;
        }
        for (PathlandNode child : node.children) {
            if (containsComponent(child, component)) {
                return true;
            }
        }
        return false;
    }
}