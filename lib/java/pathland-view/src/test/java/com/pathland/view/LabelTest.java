package com.pathland.view;

import com.pathland.view.emit.Emitter;
import com.pathland.view.emit.Frame;
import com.pathland.view.emit.FrameOpcodeSink;
import com.pathland.view.emit.Opcode;
import com.pathland.view.emit.OpcodeSink;
import com.pathland.view.emit.PathlandNode;
import com.pathland.view.emit.ValueEncoder;
import com.pathland.view.router.Navigation;
import com.pathland.view.router.NavigationContainer;
import com.pathland.view.router.Router;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Label} (spec DSL.md §4.1 composite + §5.5 {@code labelStyle}): a title +
 * optional icon composed as an {@link HStack} of {@link Image} and {@link Text},
 * with the title always driving the accessibility label and the scoped
 * {@link LabelStyle} deciding which parts render.
 */
class LabelTest {

    private static FrameOpcodeSink sink() {
        return new FrameOpcodeSink();
    }

    /** The id of the first created node of a component type, or -1. */
    private static int nodeId(Frame frame, int component) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == Categories.TREE
                        && o.command() == Commands.Tree.CREATE_NODE
                        && o.b() == component)
                .map(Opcode::a)
                .findFirst()
                .orElse(-1);
    }

    private static boolean hasComponent(Frame frame, int component) {
        return nodeId(frame, component) != -1;
    }

    /** The SET_PROPERTY ops on a node for a property. */
    private static List<Opcode> props(Frame frame, int nodeId, int property) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == Categories.STYLE
                        && o.command() == Commands.Style.SET_PROPERTY
                        && o.a() == nodeId
                        && (o.b() & 0xFFFF) == property)
                .toList();
    }

    /** The F32 value of a node property (last one wins), or null. */
    private static Float floatProp(Frame frame, int nodeId, int property) {
        return props(frame, nodeId, property).stream()
                .reduce((a, b) -> b)
                .map(o -> Float.intBitsToFloat(o.c()))
                .orElse(null);
    }

    /** The U32 value of a node property (last one wins), or null. */
    private static Integer uintProp(Frame frame, int nodeId, int property) {
        return props(frame, nodeId, property).stream()
                .reduce((a, b) -> b)
                .map(Opcode::c)
                .orElse(null);
    }

    /** The STRING value of a node property (last one wins), or null. */
    private static String stringProp(Frame frame, int nodeId, int property) {
        return props(frame, nodeId, property).stream()
                .reduce((a, b) -> b)
                .map(o -> frame.stringAt(o.c()))
                .orElse(null);
    }

    /** The text last SET_TEXT on a node, or null. */
    private static String textOf(Frame frame, int nodeId) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == Categories.STYLE
                        && o.command() == Commands.Style.SET_TEXT
                        && o.a() == nodeId)
                .reduce((a, b) -> b)
                .map(o -> frame.stringAt(o.b()))
                .orElse(null);
    }

    /** Whether any frame SET_TEXTs {@code text} on {@code nodeId}. */
    private static boolean anyText(List<Frame> frames, int nodeId, String text) {
        return frames.stream().anyMatch(f -> text.equals(textOf(f, nodeId)));
    }

    /** Whether any frame sets a STRING property to {@code value} on {@code nodeId}. */
    private static boolean anyStringProp(List<Frame> frames, int nodeId, int property, String value) {
        return frames.stream().anyMatch(f -> value.equals(stringProp(f, nodeId, property)));
    }

    @Test
    void defaultLabelRendersTitleAndIcon() {
        FrameOpcodeSink sink = sink();
        new Emitter(sink).mount(Label.of("Save", "save.svg"), Environment.DEFAULT);
        Frame frame = sink.frame();

        int stack = nodeId(frame, Components.HSTACK);
        assertTrue(stack != -1, "the label is an HStack of image + text");
        assertEquals(2f, floatProp(frame, stack, Properties.SPACING));
        assertEquals(1f, floatProp(frame, stack, Properties.ALIGNMENT), "Alignment.CENTER");
        assertEquals("Save", stringProp(frame, stack, Properties.LABEL));

        assertTrue(hasComponent(frame, Components.IMAGE), "the icon renders");
        assertTrue(hasComponent(frame, Components.TEXT), "the title renders");
        assertEquals("save.svg", stringProp(frame, nodeId(frame, Components.IMAGE), Properties.IMAGE_SOURCE));
        assertEquals("Save", textOf(frame, nodeId(frame, Components.TEXT)));
        assertEquals(1, uintProp(frame, nodeId(frame, Components.TEXT), Properties.LINE_LIMIT));
    }

    @Test
    void titleOnlyStyleDropsTheIcon() {
        FrameOpcodeSink sink = sink();
        new Emitter(sink).mount(
                Label.of("Save", "save.svg").modifier(LabelStyleMod.of(LabelStyle.TITLE_ONLY)),
                Environment.DEFAULT);
        Frame frame = sink.frame();

        assertTrue(hasComponent(frame, Components.TEXT), "the title renders");
        assertFalse(hasComponent(frame, Components.IMAGE), "the icon is dropped");
        assertEquals("Save", stringProp(frame, nodeId(frame, Components.HSTACK), Properties.LABEL));
    }

    @Test
    void iconOnlyStyleKeepsTheTitleAsA11yLabel() {
        FrameOpcodeSink sink = sink();
        new Emitter(sink).mount(
                Label.of("Save", "save.svg").modifier(LabelStyleMod.of(LabelStyle.ICON_ONLY)),
                Environment.DEFAULT);
        Frame frame = sink.frame();

        assertTrue(hasComponent(frame, Components.IMAGE), "the icon renders");
        assertFalse(hasComponent(frame, Components.TEXT), "the title text is dropped");
        assertEquals("Save", stringProp(frame, nodeId(frame, Components.HSTACK), Properties.LABEL),
                "the title still drives the accessibility label");
    }

    @Test
    void blankTitleOrIconIsSuppressed() {
        FrameOpcodeSink sink = sink();
        new Emitter(sink).mount(Label.of("", ""), Environment.DEFAULT);
        Frame frame = sink.frame();

        assertFalse(hasComponent(frame, Components.TEXT), "a blank title renders no text");
        assertFalse(hasComponent(frame, Components.IMAGE), "a blank icon renders no image");
        assertNull(stringProp(frame, nodeId(frame, Components.HSTACK), Properties.LABEL),
                "a blank title sets no accessibility label");

        FrameOpcodeSink sink2 = sink();
        new Emitter(sink2).mount(Label.of("", "save.svg"), Environment.DEFAULT);
        Frame frame2 = sink2.frame();
        assertFalse(hasComponent(frame2, Components.TEXT));
        assertTrue(hasComponent(frame2, Components.IMAGE), "a blank title still renders the icon");
        assertNull(stringProp(frame2, nodeId(frame2, Components.HSTACK), Properties.LABEL));
    }

    @Test
    void labelStyleIsAKeyedEnvironmentValue() {
        java.util.List<LabelStyle> seen = new java.util.ArrayList<>();
        View probe = new View() {
            @Override
            public PathlandNode render(Environment env) {
                seen.add(Environment.value(Environment.LABEL_STYLE).get());
                return new PathlandNode(Components.TEXT);
            }
        };

        new Emitter(sink()).mount(
                probe.modifier(LabelStyleMod.of(LabelStyle.ICON_ONLY)),
                Environment.DEFAULT);
        assertEquals(LabelStyle.ICON_ONLY, seen.get(0), "the scoped style is readable via Environment.value");

        seen.clear();
        new Emitter(sink()).mount(probe, Environment.DEFAULT);
        assertNull(seen.get(0), "no scope → Environment.value yields null (Label falls back to the default)");
    }

    @Test
    void reactiveTitleReEmitsTextAndLabel() {
        WritableSignal<String> title = Signals.signal("Save");
        AccumulatingSink sink = new AccumulatingSink();
        new Emitter(sink).mount(Label.of(title, "save.svg"), Environment.DEFAULT);

        Frame first = sink.frames().get(0);
        int text = nodeId(first, Components.TEXT);
        int stack = nodeId(first, Components.HSTACK);
        assertEquals("Save", textOf(first, text));

        title.set("Open");
        List<Frame> deltas = sink.frames().subList(1, sink.frames().size());
        assertTrue(anyText(deltas, text, "Open"), "the text node re-emits SET_TEXT");
        assertTrue(anyStringProp(deltas, stack, Properties.LABEL, "Open"),
                "the a11y label follows the title (a LABEL delta is emitted)");
    }

    @Test
    void reactiveIconReEmitsImageSource() {
        WritableSignal<String> icon = Signals.signal("save.svg");
        AccumulatingSink sink = new AccumulatingSink();
        new Emitter(sink).mount(Label.of("Save", icon), Environment.DEFAULT);

        Frame first = sink.frames().get(0);
        int image = nodeId(first, Components.IMAGE);
        assertEquals("save.svg", stringProp(first, image, Properties.IMAGE_SOURCE));

        icon.set("open.svg");
        assertTrue(anyStringProp(sink.frames().subList(1, sink.frames().size()), image,
                        Properties.IMAGE_SOURCE, "open.svg"),
                "the image source re-emits");
    }

    @Test
    void labelStyleScopedAboveAStructuralSlotSurvivesReRender() {
        java.util.List<LabelStyle> seen = new java.util.ArrayList<>();
        Router router = Navigation.navigator()
                .route("/", p -> probeStyle(seen))
                .route("/two", p -> probeStyle(seen))
                .build();
        new Emitter(sink()).mount(
                NavigationContainer.of(router).modifier(LabelStyleMod.of(LabelStyle.TITLE_ONLY)),
                Environment.DEFAULT);

        seen.clear();
        router.navigate("/two"); // structural swap re-renders the destination via reconcileSlot
        assertEquals(List.of(LabelStyle.TITLE_ONLY), seen,
                "a destination re-rendered via the slot keeps the scoped label style");
    }

    /** A destination that records the label style it renders with. */
    private static View probeStyle(java.util.List<LabelStyle> seen) {
        return new View() {
            @Override
            public PathlandNode render(Environment env) {
                seen.add(Environment.value(Environment.LABEL_STYLE).get());
                return new PathlandNode(Components.TEXT);
            }
        };
    }

    /**
     * A test sink that keeps every completed frame (the built-in {@link FrameOpcodeSink}
     * keeps only the last one — a reactive node with several bindings emits one delta
     * frame per binding).
     */
    private static final class AccumulatingSink implements OpcodeSink {

        private final List<Frame> frames = new ArrayList<>();
        private final List<Opcode> ops = new ArrayList<>();
        private final ByteArrayOutputStream strings = new ByteArrayOutputStream();

        List<Frame> frames() {
            return frames;
        }

        @Override
        public void beginFrame() {
            ops.clear();
            strings.reset();
        }

        @Override
        public void endFrame() {
            frames.add(new Frame(List.copyOf(ops), strings.toByteArray()));
        }

        private void push(int category, int command, int flags, int a, int b, int c) {
            ops.add(new Opcode(category, command, flags, a, b, c));
        }

        private void writeString(String text) {
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            strings.write(utf8.length & 0xFF);
            strings.write((utf8.length >>> 8) & 0xFF);
            strings.write((utf8.length >>> 16) & 0xFF);
            strings.write((utf8.length >>> 24) & 0xFF);
            strings.writeBytes(utf8);
        }

        @Override
        public void createNode(int id, int component) {
            push(Categories.TREE, Commands.Tree.CREATE_NODE, 0, id, component, 0);
        }

        @Override
        public void deleteNode(int id) {
            push(Categories.TREE, Commands.Tree.DELETE_NODE, 0, id, 0, 0);
        }

        @Override
        public void insertChild(int parent, int child, int index) {
            push(Categories.TREE, Commands.Tree.INSERT_CHILD, 0, parent, child, index);
        }

        @Override
        public void removeChild(int parent, int child) {
            push(Categories.TREE, Commands.Tree.REMOVE_CHILD, 0, parent, child, 0);
        }

        @Override
        public void moveChild(int parent, int child, int newIndex) {
            push(Categories.TREE, Commands.Tree.MOVE_CHILD, 0, parent, child, newIndex);
        }

        @Override
        public void setText(int nodeId, String text) {
            int offset = strings.size();
            writeString(text);
            push(Categories.STYLE, Commands.Style.SET_TEXT, 0, nodeId, offset, 0);
        }

        @Override
        public void setProperty(int nodeId, int property, int valueType, Object value) {
            int b = (valueType << 16) | (property & 0xFFFF);
            if (valueType == ValueTypes.STRING) {
                int offset = strings.size();
                writeString((String) value);
                push(Categories.STYLE, Commands.Style.SET_PROPERTY, 0, nodeId, b, offset);
            } else if (valueType == ValueTypes.DESIGN_TOKEN) {
                int offset = strings.size();
                writeString(((Color) value).token());
                push(Categories.STYLE, Commands.Style.SET_PROPERTY, 0, nodeId, b, offset);
            } else {
                push(Categories.STYLE, Commands.Style.SET_PROPERTY, 0, nodeId, b,
                        ValueEncoder.encodeBits(valueType, value));
            }
        }

        @Override
        public void setDate(int nodeId, int days, int millisOfDay) {
            push(Categories.STYLE, Commands.Style.SET_DATE, 0, nodeId, days, millisOfDay);
        }

        @Override
        public void setDesignToken(String path, int valueType, Object value) {
            int pathOffset = strings.size();
            writeString(path);
            int c;
            if (valueType == ValueTypes.STRING) {
                c = strings.size();
                writeString((String) value);
            } else {
                c = ValueEncoder.encodeBits(valueType, value);
            }
            push(Categories.STYLE, Commands.Style.SET_DESIGN_TOKEN, 0, pathOffset, valueType, c);
        }
    }
}