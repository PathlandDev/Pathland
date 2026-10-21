package dev.pathland.view;

import dev.pathland.view.emit.Emitter;
import dev.pathland.view.emit.FrameOpcodeSink;
import dev.pathland.view.emit.Opcode;
import dev.pathland.view.emit.ProtocolFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code SCROLLVIEW} scroll-axis value (spec/PRIMITIVES.md §ScrollView). */
class ScrollViewTest {

    private static int scrollNodeId(ProtocolFrame frame) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == Categories.TREE && o.command() == Commands.Tree.CREATE_NODE)
                .filter(o -> (o.b() & 0xffff) == Components.SCROLLVIEW)
                .mapToInt(Opcode::a)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no SCROLLVIEW node emitted"));
    }

    @Test
    void horizontalAxisEmitsScrollAxis() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        new Emitter(sink).mount(
                ScrollView.make(s -> s.axis(ScrollAxis.HORIZONTAL))
                        .children(Text.make("wide")),
                Environment.DEFAULT);
        ProtocolFrame frame = sink.frame();
        int id = scrollNodeId(frame);

        Opcode op = frame.opcodes().stream()
                .filter(o -> o.category() == Categories.PARAMETER
                        && o.command() == Commands.Parameter.SET_PROPERTY
                        && o.a() == id && (o.b() & 0xffff) == Properties.SCROLL_AXIS)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no SCROLL_AXIS property emitted"));
        assertEquals(1.0f, Float.intBitsToFloat(op.c()), "Horizontal = 1");
    }

    @Test
    void verticalAxisIsTheDefaultAndIsNotEmitted() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        new Emitter(sink).mount(ScrollView.children(Text.make("tall")), Environment.DEFAULT);
        ProtocolFrame frame = sink.frame();
        int id = scrollNodeId(frame);

        assertFalse(frame.opcodes().stream()
                        .anyMatch(o -> o.category() == Categories.PARAMETER
                                && o.command() == Commands.Parameter.SET_PROPERTY
                                && o.a() == id && (o.b() & 0xffff) == Properties.SCROLL_AXIS),
                "the default (vertical) axis emits no SCROLL_AXIS property");
        // The content still attaches as the single child.
        assertTrue(frame.opcodes().stream()
                        .anyMatch(o -> o.category() == Categories.TREE
                                && o.command() == Commands.Tree.INSERT_CHILD && o.a() == id),
                "the content child is attached");
    }
}
