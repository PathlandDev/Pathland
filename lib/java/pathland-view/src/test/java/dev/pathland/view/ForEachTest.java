package dev.pathland.view;

import dev.pathland.view.emit.Emitter;
import dev.pathland.view.emit.FrameOpcodeSink;
import dev.pathland.view.emit.ProtocolFrame;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.WritableSignal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reactive list component (spec DSL.md §3.4 / §9.3.1). */
class ForEachTest {

    private static long countOps(ProtocolFrame frame, int category, int command) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == category && o.command() == command)
                .count();
    }

    private static List<String> texts(ProtocolFrame frame) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == Categories.PARAMETER
                        && o.command() == Commands.Parameter.SET_TEXT)
                .map(o -> frame.stringAt(o.b()))
                .toList();
    }

    private static boolean containsText(ProtocolFrame frame, String text) {
        return texts(frame).contains(text);
    }

    @Test
    void staticListRendersInOrder() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);

        View root = VStack.children(
                ForEach.of(List.of("a", "b", "c"), (item, index) -> Text.make(item)));
        emitter.mount(root, Environment.DEFAULT);

        assertEquals(List.of("a", "b", "c"), texts(sink.frame()));
    }

    @Test
    void signalListAddsAndRemovesRows() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<List<String>> items = Signals.signal(List.of("a", "b"));

        View root = VStack.children(
                ForEach.of(items, (item, index) -> Text.make(item)));
        emitter.mount(root, Environment.DEFAULT);

        items.set(List.of("a", "b", "c"));
        ProtocolFrame added = sink.frame();
        assertEquals(1, countOps(added, Categories.TREE, Commands.Tree.CREATE_NODE));
        assertEquals(1, countOps(added, Categories.TREE, Commands.Tree.INSERT_CHILD));
        assertTrue(containsText(added, "c"));

        items.set(List.of("a"));
        ProtocolFrame removed = sink.frame();
        assertEquals(2, countOps(removed, Categories.TREE, Commands.Tree.REMOVE_CHILD));
        assertEquals(2, countOps(removed, Categories.TREE, Commands.Tree.DELETE_NODE));
        assertFalse(containsText(removed, "b"));
    }

    @Test
    void identicalRecomputeEmitsZeroOpcodes() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        // Force the signal to propagate even for an equal list, so ForEach re-renders.
        WritableSignal<List<String>> items =
                Signals.signal(List.of("a", "b"), (left, right) -> false);

        emitter.mount(
                VStack.children(ForEach.of(items, (item, index) -> Text.make(item))),
                Environment.DEFAULT);
        int frames = sink.framesProduced();

        items.set(List.of("a", "b"));
        assertEquals(frames, sink.framesProduced(), "identical rows emit no frame");
    }

    @Test
    void nullChildrenAreSkipped() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);

        View root = VStack.children(ForEach.of(List.of("a", "skip", "b"), (item, index) ->
                "skip".equals(item) ? null : Text.make(item)));
        emitter.mount(root, Environment.DEFAULT);

        assertEquals(List.of("a", "b"), texts(sink.frame()));
    }

    @Test
    void nestedForEachRenders() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);

        View root = VStack.children(ForEach.of(List.of("x", "y"), (outer, outerIndex) ->
                VStack.make().children(
                        Text.make(outer),
                        ForEach.of(List.of(1, 2), (n, nIndex) ->
                                Text.make(outer + n)))));
        emitter.mount(root, Environment.DEFAULT);

        assertEquals(List.of("x", "x1", "x2", "y", "y1", "y2"), texts(sink.frame()));
    }

    @Test
    void boundSignalInsideARowStaysFineGrained() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<String> label = Signals.signal("one");

        View root = VStack.children(ForEach.of(List.of("row"), (item, index) ->
                Text.make(t -> t.text(label))));
        emitter.mount(root, Environment.DEFAULT);

        label.set("two");
        ProtocolFrame delta = sink.frame();
        assertEquals(1, delta.opcodes().size(), "a bound row text emits one SET_TEXT");
        assertEquals(Commands.Parameter.SET_TEXT, delta.opcodes().get(0).command());
        assertEquals("two", delta.stringAt(delta.opcodes().get(0).b()));
    }

    @Test
    void environmentScopeReachesRows() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        EnvironmentKey<String> key = EnvironmentKey.of("test.foreach.scope");

        View row = new View() {
            @Override
            public View body() {
                return Text.make(Environment.value(key).get() + "row");
            }
        };
        View root = VStack.children(ForEach.of(List.of("x"), (item, index) -> row))
                .modifiers(Modifiers.environmentBinding(e -> e.key(key).value("scoped-")));
        emitter.mount(root, Environment.DEFAULT);

        assertTrue(containsText(sink.frame(), "scoped-row"));
    }
}
