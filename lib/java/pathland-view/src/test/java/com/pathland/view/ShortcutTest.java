package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The single-value {@code with(value)} shortcuts (spec DSL.md §2) are pure sugar
 * for the config-builder lambda — byte-identical to {@code with(c -> …)}.
 */
class ShortcutTest {

    @Test
    void textShortcutEqualsLambda() {
        PathlandNode shortcut = Text.with("hi").render(Environment.DEFAULT);
        PathlandNode lambda = Text.with(t -> t.text("hi")).render(Environment.DEFAULT);
        assertEquals(lambda.text, shortcut.text);
        assertEquals("hi", shortcut.text);
    }

    @Test
    void paddingShortcutEqualsLambda() {
        PathlandNode shortcut = Text.with("x").modifiers(Padding.with(16)).render(Environment.DEFAULT);
        PathlandNode lambda = Text.with("x").modifiers(Padding.with(p -> p.uniform(16))).render(Environment.DEFAULT);
        assertEquals(lambda.properties.get(Properties.PADDING), shortcut.properties.get(Properties.PADDING));
        assertEquals(16f, shortcut.properties.get(Properties.PADDING));
    }

    @Test
    void fontSizeShortcutEqualsLambda() {
        PathlandNode shortcut = Text.with("x").modifiers(FontSize.with(18f)).render(Environment.DEFAULT);
        PathlandNode lambda = Text.with("x").modifiers(FontSize.with(s -> s.size(18f))).render(Environment.DEFAULT);
        assertEquals(lambda.properties.get(Properties.FONT_SIZE), shortcut.properties.get(Properties.FONT_SIZE));
    }

    @Test
    void reactiveShortcutBindsSignal() {
        WritableSignal<String> text = Signals.signal("a");
        PathlandNode node = Text.with(text).render(Environment.DEFAULT);
        assertEquals("a", node.text);
        assertNotNull(node.textBinding, "a reactive shortcut binds the node's text");
    }

    @Test
    void accessibilityRoleShortcutValidates() {
        // A non-semantic code is rejected on the shortcut path too.
        assertThrows(IllegalArgumentException.class, () -> AccessibilityRole.with(20));
        assertNotNull(AccessibilityRole.with(Roles.HEADER));
    }

    @Test
    void imageSourceShortcutEqualsLambda() {
        PathlandNode shortcut = Image.with("cover.png").render(Environment.DEFAULT);
        PathlandNode lambda = Image.with(i -> i.source("cover.png")).render(Environment.DEFAULT);
        assertEquals(lambda.properties.get(Properties.IMAGE_SOURCE), shortcut.properties.get(Properties.IMAGE_SOURCE));
    }
}