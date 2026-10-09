package com.pathland.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pathland.view.emit.Emitter;
import com.pathland.view.emit.FrameOpcodeSink;
import com.pathland.view.emit.Opcode;
import com.pathland.view.emit.PathlandNode;
import com.pathland.view.emit.ProtocolFrame;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Signals-first configs: a non-constant value member re-emits only its property. */
public class ReactiveConfigTest {

    private static List<Opcode> setProps(ProtocolFrame frame) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == Categories.PARAMETER
                        && o.command() == Commands.Parameter.SET_PROPERTY)
                .toList();
    }

    private static int property(Opcode o) {
        return o.b() & 0xffff;
    }

    @Test
    void reactiveVisibleEmitsOnlyItsProperty() {
        WritableSignal<Boolean> visible = Signals.signal(true);
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        emitter.mount(
                Text.with(t -> t.text("hi")).modifiers(Visible.with(v -> v.visible(visible))),
                Environment.DEFAULT);

        visible.set(false);

        List<Opcode> props = setProps(sink.frame());
        assertEquals(1, props.size());
        assertEquals(Properties.VISIBLE, property(props.get(0)));
        assertEquals(0, props.get(0).c());
    }

    @Test
    void reactiveOpacityEmitsItsFloat() {
        WritableSignal<Float> opacity = Signals.signal(1f);
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        emitter.mount(
                Text.with(t -> t.text("hi")).modifiers(Opacity.with(o -> o.value(opacity))),
                Environment.DEFAULT);

        opacity.set(0.5f);

        List<Opcode> props = setProps(sink.frame());
        assertEquals(1, props.size());
        assertEquals(Properties.OPACITY, property(props.get(0)));
        assertEquals(Float.floatToRawIntBits(0.5f), props.get(0).c());
    }

    @Test
    void reactiveEnumEmitsItsWireCode() {
        WritableSignal<ShapeKind> shape = Signals.signal(ShapeKind.CIRCLE);
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        emitter.mount(
                Text.with(t -> t.text("hi")).modifiers(ClipShape.with(c -> c.shape(shape))),
                Environment.DEFAULT);

        shape.set(ShapeKind.RECTANGLE);

        List<Opcode> props = setProps(sink.frame());
        assertEquals(1, props.size());
        assertEquals(Properties.SHAPE_KIND, property(props.get(0)));
        assertEquals(Float.floatToRawIntBits((float) ShapeKind.RECTANGLE.wire()), props.get(0).c());
    }

    @Test
    void reactiveContainerSpacingEmitsDelta() {
        WritableSignal<Float> spacing = Signals.signal(4f);
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        emitter.mount(
                VStack.with(v -> v.spacing(spacing)).children(Text.with(t -> t.text("a"))),
                Environment.DEFAULT);

        spacing.set(12f);

        List<Opcode> props = setProps(sink.frame());
        assertEquals(1, props.size());
        assertEquals(Properties.SPACING, property(props.get(0)));
        assertEquals(Float.floatToRawIntBits(12f), props.get(0).c());
    }

    @Test
    void reactiveStringPropertyEmitsDelta() {
        WritableSignal<String> label = Signals.signal("a");
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        emitter.mount(
                Text.with(t -> t.text("x"))
                        .modifiers(AccessibilityLabel.with(a -> a.text(label))),
                Environment.DEFAULT);

        label.set("b");

        List<Opcode> props = setProps(sink.frame());
        assertEquals(1, props.size());
        assertEquals(Properties.LABEL, property(props.get(0)));
    }

    @Test
    void constantPropertyRegistersNoBinding() {
        PathlandNode node = new PathlandNode(Components.TEXT);
        node.property(Properties.VISIBLE, Signals.constant(true));
        assertTrue(node.propertyBindings.isEmpty(), "a constant carries no binding");

        node.property(Properties.OPACITY, Signals.signal(1f));
        assertEquals(1, node.propertyBindings.size(), "a writable signal binds");
    }

    @Test
    void unchangedSignalEmitsNoFrame() {
        WritableSignal<Float> opacity = Signals.signal(1f);
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        emitter.mount(
                Text.with(t -> t.text("hi")).modifiers(Opacity.with(o -> o.value(opacity))),
                Environment.DEFAULT);
        int frames = sink.framesProduced();

        opacity.set(1f); // equal → no flush, no frame

        assertEquals(frames, sink.framesProduced(), "an unchanged signal emits zero opcodes");
    }
}
