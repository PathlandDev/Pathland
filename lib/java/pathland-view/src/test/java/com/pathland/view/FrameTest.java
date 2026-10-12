package com.pathland.view;

import com.pathland.view.emit.PathlandNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The {@code Frame} view-modifier surface: the {@code Frame.with(f -> …)} lambda
 * configurator ({@code width}/{@code height}/{@code alignment} + min/ideal/max
 * bounds), applied via {@code .modifiers(...)}. The wire is unchanged — every form
 * emits the same F32 properties (WIDTH/HEIGHT, ALIGNMENT, MIN/IDEAL/MAX bounds)
 * with the same sentinels the legacy {@code FrameMod} produced.
 */
class FrameTest {

    private static PathlandNode render(Frame frame) {
        return Text.make(t -> t.text("x")).modifiers(frame).render(Environment.DEFAULT);
    }

    // -- fixed axes --------------------------------------------------------------

    @Test
    void widthEmitsWidthAndNoAlignment() {
        PathlandNode node = render(Frame.with(f -> f.width(200f)));
        assertEquals(200f, node.properties.get(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT), "alignment must not be forced");
    }

    @Test
    void heightEmitsHeightOnly() {
        PathlandNode node = render(Frame.with(f -> f.height(24f)));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT));
    }

    @Test
    void widthHeightEmitsBothAxesAndNoAlignment() {
        PathlandNode node = render(Frame.with(f -> f.width(100).height(24)));
        assertEquals(100f, node.properties.get(Properties.WIDTH));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT));
    }

    @Test
    void withAlignmentStillEmitted() {
        PathlandNode node = render(Frame.with(f -> f.width(100).height(24).alignment(Alignment.CENTER)));
        assertEquals(100f, node.properties.get(Properties.WIDTH));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
        assertEquals((float) Alignment.CENTER.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    @Test
    void widthWithAlignmentStillEmitted() {
        PathlandNode node = render(Frame.with(f -> f.width(200f).alignment(Alignment.TOP_LEADING)));
        assertEquals(200f, node.properties.get(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT));
        assertEquals((float) Alignment.TOP_LEADING.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    @Test
    void infiniteWidthNormalizesToFill() {
        PathlandNode node = render(Frame.with(f -> f.width(Float.POSITIVE_INFINITY)));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT));
    }

    @Test
    void infiniteAxesNormalizeToFillWithAlignment() {
        PathlandNode node = render(Frame.with(f -> f
                .width(Float.POSITIVE_INFINITY)
                .height(Float.POSITIVE_INFINITY)
                .alignment(Alignment.TOP_LEADING)));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.HEIGHT));
        assertEquals((float) Alignment.TOP_LEADING.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    @Test
    void negativeInfinityAlsoNormalizesToFill() {
        PathlandNode node = render(Frame.with(f -> f.width(Float.NEGATIVE_INFINITY).height(24f)));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
        assertEquals(24f, node.properties.get(Properties.HEIGHT));
    }

    @Test
    void nanAxisIsOmitted() {
        PathlandNode node = render(Frame.with(f -> f.width(260f).height(Float.NaN).alignment(Alignment.CENTER)));
        assertEquals(260f, node.properties.get(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT), "a NaN axis is not emitted");
        assertEquals((float) Alignment.CENTER.wire(), node.properties.get(Properties.ALIGNMENT));
    }

    // -- min/ideal/max bounds ----------------------------------------------------

    @Test
    void builderEmitsMinIdealMaxOnRequestedAxes() {
        PathlandNode node = render(Frame.with(f -> f
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
    void builderCombinesFixedAxesWithBounds() {
        PathlandNode node = render(Frame.with(f -> f
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
        PathlandNode node = render(Frame.with(f -> f.width(Float.POSITIVE_INFINITY)));
        assertEquals(Commands.Size.FILL, node.properties.get(Properties.WIDTH));
    }

    @Test
    void builderInfiniteBoundsAreOmitted() {
        PathlandNode node = render(Frame.with(f -> f
                .maxWidth(Float.POSITIVE_INFINITY)
                .idealHeight(0f)
                .maxHeight(Float.NEGATIVE_INFINITY)));
        assertFalse(node.properties.containsKey(Properties.MAX_WIDTH), "∞ maxWidth = no limit");
        assertFalse(node.properties.containsKey(Properties.MAX_HEIGHT));
        assertEquals(0f, node.properties.get(Properties.IDEAL_HEIGHT));
    }

    @Test
    void emptyBuilderEmitsNoFrameProperties() {
        PathlandNode node = render(Frame.with(f -> {}));
        assertFalse(node.properties.containsKey(Properties.WIDTH));
        assertFalse(node.properties.containsKey(Properties.HEIGHT));
        assertFalse(node.properties.containsKey(Properties.MIN_WIDTH));
        assertFalse(node.properties.containsKey(Properties.ALIGNMENT));
    }
}