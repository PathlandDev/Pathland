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

    @Test
    void infiniteWidthNormalizesToFill() {
        PathlandNode node = render(FrameMod.of(Float.POSITIVE_INFINITY));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT));
    }

    @Test
    void infiniteAxesNormalizeToFillWithAlignment() {
        PathlandNode node = render(FrameMod.of(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Alignment.LEADING));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.HEIGHT));
        assertEquals((float) Alignment.LEADING.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    @Test
    void negativeInfinityAlsoNormalizesToFill() {
        PathlandNode node = render(FrameMod.of(Float.NEGATIVE_INFINITY, 24f));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
    }

    @Test
    void infiniteMaxBoundsAreOmitted() {
        PathlandNode node = render(FrameMod.of(Float.NaN, Float.NaN, Float.POSITIVE_INFINITY,
                Float.NaN, 0f, Float.NEGATIVE_INFINITY));
        assertFalse(node.properties.containsKey(Properties.MAX_WIDTH));
        assertFalse(node.properties.containsKey(Properties.MAX_HEIGHT));
        assertTrue(node.properties.containsKey(Properties.IDEAL_HEIGHT));
    }
}