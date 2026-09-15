package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code frame} modifier's alignment-optional overloads. */
class FrameModTest {

    private static PathlandNode render(FrameMod frame) {
        return frame.body(Text.of("x")).render(Environment.DEFAULT);
    }

    @Test
    void widthOnlyEmitsWidthAndNoAlignment() {
        PathlandNode node = render(FrameMod.of(200f));
        assertTrue(node.properties.containsKey(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT), "alignment must not be forced");
    }

    @Test
    void widthHeightEmitsBothAxesAndNoAlignment() {
        PathlandNode node = render(FrameMod.of(100, 24));
        assertTrue(node.properties.containsKey(Properties.WIDTH));
        assertTrue(node.properties.containsKey(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT));
    }

    @Test
    void explicitAlignmentStillEmitted() {
        PathlandNode node = render(FrameMod.of(100, 24, Alignment.CENTER));
        assertTrue(node.properties.containsKey(Properties.WIDTH));
        assertTrue(node.properties.containsKey(Properties.HEIGHT));
        assertEquals((float) Alignment.CENTER.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    @Test
    void widthWithAlignmentStillEmitted() {
        PathlandNode node = render(FrameMod.of(200f, Alignment.LEADING));
        assertTrue(node.properties.containsKey(Properties.WIDTH));
        assertEquals((float) Alignment.LEADING.wire(), node.properties.get(Properties.ALIGNMENT));
    }
}