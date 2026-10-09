package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The {@code Frame} view-modifier surface: the convenience factories
 * ({@code Frame.of(w, h)}, {@code Frame.of(w, h, alignment)},
 * {@code Frame.of(w, alignment)}, {@code Frame.ofWidth(w)}, {@code Frame.ofHeight(h)})
 * and the {@code Frame.of(c -> …)} lambda configurator, applied via {@code .with(...)}.
 * The wire is unchanged — every form emits the same F32 properties
 * (WIDTH/HEIGHT, ALIGNMENT, MIN/IDEAL/MAX bounds) with the same sentinels the legacy
 * {@code FrameMod} produced; each convenience case below mirrors the old
 * {@code FrameModTest} assertion 1:1.
 */
class FrameTest {

    private static PathlandNode render(Frame frame) {
        return Text.with(t -> t.text("x")).modifiers(frame).render(Environment.DEFAULT);
    }

    // -- convenience factories --------------------------------------------------

    @Test
    void ofWidthEmitsWidthAndNoAlignment() {
        PathlandNode node = render(Frame.ofWidth(200f));
        assertEquals(200f, node.properties.get(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT), "alignment must not be forced");
    }

    @Test
    void ofHeightEmitsHeightOnly() {
        PathlandNode node = render(Frame.ofHeight(24f));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT));
    }

    @Test
    void ofWidthHeightEmitsBothAxesAndNoAlignment() {
        PathlandNode node = render(Frame.of(100, 24));
        assertEquals(100f, node.properties.get(Properties.WIDTH));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT));
    }

    @Test
    void ofWithAlignmentStillEmitted() {
        PathlandNode node = render(Frame.of(100, 24, Alignment.CENTER));
        assertEquals(100f, node.properties.get(Properties.WIDTH));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
        assertEquals((float) Alignment.CENTER.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    @Test
    void ofWidthWithAlignmentStillEmitted() {
        PathlandNode node = render(Frame.of(200f, Alignment.TOP_LEADING));
        assertEquals(200f, node.properties.get(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT));
        assertEquals((float) Alignment.TOP_LEADING.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    @Test
    void infiniteWidthNormalizesToFill() {
        PathlandNode node = render(Frame.ofWidth(Float.POSITIVE_INFINITY));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT));
    }

    @Test
    void infiniteAxesNormalizeToFillWithAlignment() {
        PathlandNode node = render(Frame.of(
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Alignment.TOP_LEADING));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.HEIGHT));
        assertEquals((float) Alignment.TOP_LEADING.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    @Test
    void negativeInfinityAlsoNormalizesToFill() {
        PathlandNode node = render(Frame.of(Float.NEGATIVE_INFINITY, 24f));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
    }

    @Test
    void nanAxisIsOmitted() {
        PathlandNode node = render(Frame.of(260f, Float.NaN, Alignment.CENTER));
        assertEquals(260f, node.properties.get(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT), "a NaN axis is not emitted");
        assertEquals((float) Alignment.CENTER.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    // -- the lambda configurator -------------------------------------------------

    @Test
    void builderEmitsMinIdealMaxOnRequestedAxes() {
        PathlandNode node = render(Frame.of(c -> c
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
        PathlandNode node = render(Frame.of(c -> c.width(120).height(60)));
        assertEquals(120f, node.properties.get(Properties.WIDTH));
        assertEquals(60f, node.properties.get(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.MIN_WIDTH), "no bounds implied by fixed axes");
    }

    @Test
    void builderCombinesFixedAxesWithBounds() {
        PathlandNode node = render(Frame.of(c -> c
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
        PathlandNode node = render(Frame.of(c -> c.width(Float.POSITIVE_INFINITY)));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
    }

    @Test
    void builderInfiniteBoundsAreOmitted() {
        PathlandNode node = render(Frame.of(c -> c
                .maxWidth(Float.POSITIVE_INFINITY)
                .idealHeight(0f)
                .maxHeight(Float.NEGATIVE_INFINITY)));
        assertFalse(node.properties.containsKey(Properties.MAX_WIDTH), "∞ maxWidth = no limit");
        assertFalse(node.properties.containsKey(Properties.MAX_HEIGHT));
        assertEquals(0f, node.properties.get(Properties.IDEAL_HEIGHT));
    }

    @Test
    void emptyBuilderEmitsNoFrameProperties() {
        PathlandNode node = render(Frame.of(c -> {}));
        assertFalse(node.properties.containsKey(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.MIN_WIDTH));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT));
    }
}