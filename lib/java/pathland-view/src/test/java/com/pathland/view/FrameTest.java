package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The SwiftUI-like {@code frame} surface: the convenience overloads
 * ({@code .frame(w, h)}, {@code .frame(w, h, alignment)}, {@code .frame(w, alignment)},
 * {@code .frameWidth(w)}, {@code .frameHeight(h)}) and the {@code .frame(c -> …)} lambda
 * configurator. The wire is unchanged — every form emits the same F32 properties
 * (WIDTH/HEIGHT, ALIGNMENT, MIN/IDEAL/MAX bounds) with the same sentinels the legacy
 * {@code FrameMod} produced; each convenience case below mirrors the old
 * {@code FrameModTest} assertion 1:1.
 */
class FrameTest {

    private static PathlandNode render(View view) {
        return view.render(Environment.DEFAULT);
    }

    // -- convenience overloads --------------------------------------------------

    @Test
    void frameWidthEmitsWidthAndNoAlignment() {
        PathlandNode node = render(Text.of("x").frameWidth(200f));
        assertEquals(200f, node.properties.get(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT), "alignment must not be forced");
    }

    @Test
    void frameHeightEmitsHeightOnly() {
        PathlandNode node = render(Text.of("x").frameHeight(24f));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT));
    }

    @Test
    void frameWidthHeightEmitsBothAxesAndNoAlignment() {
        PathlandNode node = render(Text.of("x").frame(100, 24));
        assertEquals(100f, node.properties.get(Properties.WIDTH));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT));
    }

    @Test
    void frameWithAlignmentStillEmitted() {
        PathlandNode node = render(Text.of("x").frame(100, 24, Alignment.CENTER));
        assertEquals(100f, node.properties.get(Properties.WIDTH));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
        assertEquals((float) Alignment.CENTER.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    @Test
    void frameWidthWithAlignmentStillEmitted() {
        PathlandNode node = render(Text.of("x").frame(200f, Alignment.TOP_LEADING));
        assertEquals(200f, node.properties.get(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT));
        assertEquals((float) Alignment.TOP_LEADING.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    @Test
    void infiniteWidthNormalizesToFill() {
        PathlandNode node = render(Text.of("x").frameWidth(Float.POSITIVE_INFINITY));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT));
    }

    @Test
    void infiniteAxesNormalizeToFillWithAlignment() {
        PathlandNode node = render(Text.of("x").frame(
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Alignment.TOP_LEADING));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.HEIGHT));
        assertEquals((float) Alignment.TOP_LEADING.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    @Test
    void negativeInfinityAlsoNormalizesToFill() {
        PathlandNode node = render(Text.of("x").frame(Float.NEGATIVE_INFINITY, 24f));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
    }

    @Test
    void nanAxisIsOmitted() {
        PathlandNode node = render(Text.of("x").frame(260f, Float.NaN, Alignment.CENTER));
        assertEquals(260f, node.properties.get(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT), "a NaN axis is not emitted");
        assertEquals((float) Alignment.CENTER.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    // -- the lambda configurator -------------------------------------------------

    @Test
    void builderEmitsMinIdealMaxOnRequestedAxes() {
        PathlandNode node = render(Text.of("x").frame(c -> c
                .minWidth(100)
                .idealWidth(200)
                .maxWidth(300)
                .alignment(Alignment.CENTER)));
        assertEquals(100f, node.properties.get(Properties.MIN_WIDTH));
        assertEquals(200f, node.properties.get(Properties.IDEAL_WIDTH));
        assertEquals(300f, node.properties.get(Properties.MAX_WIDTH));
        assertFalse(node.properties.containsKey(Properties.MIN_HEIGHT), "unset bounds are omitted");
        assertFalse(node.properties.containsKey(Properties.IDEAL_HEIGHT));
        assertFalse(node.properties.containsKey(Properties.MAX_HEIGHT));
        assertFalse(node.properties.containsKey(Properties.WIDTH), "no fixed axis implied");
        assertEquals((float) Alignment.CENTER.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    @Test
    void builderWidthHeightEmitFixedAxes() {
        PathlandNode node = render(Text.of("x").frame(c -> c.width(120).height(60)));
        assertEquals(120f, node.properties.get(Properties.WIDTH));
        assertEquals(60f, node.properties.get(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.MIN_WIDTH), "no bounds implied by fixed axes");
    }

    @Test
    void builderCombinesFixedAxesWithBounds() {
        PathlandNode node = render(Text.of("x").frame(c -> c
                .width(200)
                .minWidth(100)
                .maxWidth(300)
                .maxHeight(240)));
        assertEquals(200f, node.properties.get(Properties.WIDTH));
        assertEquals(100f, node.properties.get(Properties.MIN_WIDTH));
        assertEquals(300f, node.properties.get(Properties.MAX_WIDTH));
        assertEquals(240f, node.properties.get(Properties.MAX_HEIGHT));
        assertFalse(node.properties.containsKey(Properties.IDEAL_HEIGHT));
    }

    @Test
    void builderInfinityAxesNormalizeToFill() {
        PathlandNode node = render(Text.of("x").frame(c -> c.width(Float.POSITIVE_INFINITY)));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
    }

    @Test
    void builderInfiniteBoundsAreOmitted() {
        PathlandNode node = render(Text.of("x").frame(c -> c
                .maxWidth(Float.POSITIVE_INFINITY)
                .idealHeight(0f)
                .maxHeight(Float.NEGATIVE_INFINITY)));
        assertFalse(node.properties.containsKey(Properties.MAX_WIDTH), "∞ maxWidth = no limit");
        assertFalse(node.properties.containsKey(Properties.MAX_HEIGHT));
        assertEquals(0f, node.properties.get(Properties.IDEAL_HEIGHT));
    }

    @Test
    void emptyBuilderEmitsNoFrameProperties() {
        PathlandNode node = render(Text.of("x").frame(c -> {}));
        assertFalse(node.properties.containsKey(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.MIN_WIDTH));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT));
    }
}