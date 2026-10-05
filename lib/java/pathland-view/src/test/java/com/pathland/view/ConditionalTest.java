package com.pathland.view;

import com.pathland.view.emit.Emitter;
import com.pathland.view.emit.Frame;
import com.pathland.view.emit.FrameOpcodeSink;
import com.pathland.view.emit.Opcode;
import com.pathland.view.emit.RenderResult;
import com.pathland.view.signal.Signals;
import com.pathland.view.signal.WritableSignal;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static com.pathland.view.Conditional.Case;
import static com.pathland.view.Conditional.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural reactivity (spec DSL.md §3.4): {@code Conditional.when} over a selector
 * signal — branch swaps emit only {@code TREE} deltas, identical recomputes emit zero
 * opcodes, nested containers reconcile independently, and reactive content stays
 * fine-grained.
 */
class ConditionalTest {

    private static long countOps(Frame frame, int category, int command) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == category && o.command() == command)
                .count();
    }

    @Test
    void branchChangeWithDifferentComponentsEmitsTreeDeltas() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Boolean> show = Signals.signal(true);
        AtomicInteger taps = new AtomicInteger();
        View root = VStack.of(when(show, Text.of("A"), Button.of("B", taps::incrementAndGet)));
        RenderResult result = emitter.mount(root, Environment.DEFAULT);

        assertEquals(0, result.tapActions().size(), "the text branch has no tap action");

        show.set(false);
        Frame delta = sink.frame();

        // The slot's single child changed component type (TEXT -> BUTTON): remove + delete the
        // old, create + insert the new. Under the wrapper model the button is a BUTTON shell
        // with its label as a child, so the new subtree is two nodes (BUTTON + TEXT).
        assertEquals(1, countOps(delta, Categories.TREE, Commands.Tree.REMOVE_CHILD), "old child removed");
        assertEquals(1, countOps(delta, Categories.TREE, Commands.Tree.DELETE_NODE), "old node deleted");
        assertEquals(2, countOps(delta, Categories.TREE, Commands.Tree.CREATE_NODE), "button shell + label node");
        assertEquals(2, countOps(delta, Categories.TREE, Commands.Tree.INSERT_CHILD), "button inserted + label child");
        assertEquals(1, countOps(delta, Categories.PARAMETER, Commands.Parameter.SET_TEXT), "new button label");
        assertTrue(countOps(delta, Categories.PARAMETER, Commands.Parameter.SET_PROPERTY) >= 1,
                "button declares EVENT_LISTENERS");

        // The live routing map now routes the button's tap (structural reconcile updates it).
        assertEquals(1, result.tapActions().size(), "tap routing is live after the swap");
        result.tapActions().values().iterator().next().run();
        assertEquals(1, taps.get(), "the swapped-in button's action fires");
    }

    @Test
    void identicalStructureAcrossRecomputeEmitsZeroOpcodes() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Integer> n = Signals.signal(1);
        View root = when(n,
                Case.of(1, Text.of("same")),
                Case.of(2, Text.of("same")),
                Case.otherwise(Text.of("other")));
        emitter.mount(root, Environment.DEFAULT);
        int frames = sink.framesProduced();

        // 1 -> 2: both branches are the same structure/text; the reconcile emits zero opcodes
        // and therefore no new frame at all.
        n.set(2);
        assertEquals(frames, sink.framesProduced(), "identical recompute emits no frame");

        // 2 -> 3: otherwise branch, same TEXT component at the same position -> a SET_TEXT delta.
        n.set(3);
        Frame delta = sink.frame();
        assertEquals(1, delta.opcodes().size(), "same component, changed text -> one SET_TEXT");
        Opcode only = delta.opcodes().get(0);
        assertEquals(Categories.PARAMETER, only.category());
        assertEquals(Commands.Parameter.SET_TEXT, only.command());
        assertEquals("other", delta.stringAt(only.b()));
    }

    @Test
    void switchSelectsBranchByKey() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Integer> mode = Signals.signal(2);
        View root = when(mode,
                Case.of(1, Text.of("Home")),
                Case.of(2, Text.of("Users")),
                Case.otherwise(Text.of("NotFound")));
        emitter.mount(root, Environment.DEFAULT);

        Frame initial = sink.frame();
        assertEquals(1, countOps(initial, Categories.PARAMETER, Commands.Parameter.SET_TEXT));
        Opcode text = initial.opcodes().stream()
                .filter(o -> o.category() == Categories.PARAMETER && o.command() == Commands.Parameter.SET_TEXT)
                .findFirst().orElseThrow();
        assertEquals("Users", initial.stringAt(text.b()));

        mode.set(9);
        assertEquals("NotFound", lastText(sink.frame()));
        mode.set(1);
        assertEquals("Home", lastText(sink.frame()));
    }

    @Test
    void noMatchWithoutOtherwiseEmptiesTheSlot() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Integer> mode = Signals.signal(1);
        View root = when(mode, Case.of(1, Text.of("A")));
        emitter.mount(root, Environment.DEFAULT);

        mode.set(5); // no matching branch and no otherwise -> the slot empties
        Frame delta = sink.frame();
        assertEquals(1, countOps(delta, Categories.TREE, Commands.Tree.REMOVE_CHILD));
        assertEquals(1, countOps(delta, Categories.TREE, Commands.Tree.DELETE_NODE));

        mode.set(1); // matching branch again -> the slot re-fills
        Frame refill = sink.frame();
        assertEquals(1, countOps(refill, Categories.TREE, Commands.Tree.CREATE_NODE));
        assertEquals(1, countOps(refill, Categories.TREE, Commands.Tree.INSERT_CHILD));
        assertEquals(1, countOps(refill, Categories.PARAMETER, Commands.Parameter.SET_TEXT));
    }

    @Test
    void nestedWhenReconcilesIndependently() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Boolean> show = Signals.signal(true);
        WritableSignal<Boolean> flag = Signals.signal(true);
        View root = when(show, when(flag, Text.of("a"), Text.of("b")), Text.of("flat"));
        emitter.mount(root, Environment.DEFAULT);

        // Inner swap only: same TEXT component at the same position -> one SET_TEXT.
        flag.set(false);
        Frame inner = sink.frame();
        assertEquals(1, inner.opcodes().size());
        assertEquals(Commands.Parameter.SET_TEXT, inner.opcodes().get(0).command());

        // Outer swap replaces the whole inner slot subtree (VSTACK -> TEXT): the inner slot's
        // structural effect and bindings must be destroyed, not leaked. DELETE_NODE covers the
        // whole removed subtree (spec OPCODE.md), so only the subtree root is deleted.
        show.set(false);
        Frame outer = sink.frame();
        assertEquals(1, countOps(outer, Categories.TREE, Commands.Tree.REMOVE_CHILD));
        assertEquals(1, countOps(outer, Categories.TREE, Commands.Tree.DELETE_NODE),
                "the inner slot node is deleted (DELETE_NODE covers its subtree)");
        assertEquals(1, countOps(outer, Categories.TREE, Commands.Tree.CREATE_NODE));
        assertEquals(1, countOps(outer, Categories.PARAMETER, Commands.Parameter.SET_TEXT));

        // If the inner structural effect leaked, mutating `flag` would now emit against a
        // deleted node. It must not.
        int frames = sink.framesProduced();
        flag.set(true);
        assertEquals(frames, sink.framesProduced(), "destroyed inner effect must not fire");
    }

    @Test
    void reactiveContentStaysFineGrained() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Boolean> show = Signals.signal(true);
        WritableSignal<String> label = Signals.signal("Hi");
        View root = when(show, Text.of(label), Text.of("else"));
        emitter.mount(root, Environment.DEFAULT);

        // The structural effect tracks only the selector; the content's reactive text is owned
        // by its own node-level binding effect.
        label.set("Hello");
        Frame delta = sink.frame();
        assertEquals(1, delta.opcodes().size(), "reactive text inside a branch is one SET_TEXT");
        Opcode only = delta.opcodes().get(0);
        assertEquals(Categories.PARAMETER, only.category());
        assertEquals(Commands.Parameter.SET_TEXT, only.command());
        assertEquals("Hello", delta.stringAt(only.b()));
    }

    @Test
    void branchSwapKeepsReactiveBindingAcrossFrames() {
        // A reactive text bound into the swapped-in branch keeps its own delta on later changes.
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        WritableSignal<Boolean> show = Signals.signal(true);
        WritableSignal<String> label = Signals.signal("one");
        View root = when(show, Text.of(label), Text.of("other"));
        emitter.mount(root, Environment.DEFAULT);

        show.set(false); // swap to the else branch (static text)
        Frame swap = sink.frame();
        assertEquals("other", lastText(swap));

        show.set(true); // swap back to the reactive branch
        Frame back = sink.frame();
        assertEquals("one", lastText(back));

        label.set("two"); // the re-registered binding is live again
        Frame delta = sink.frame();
        assertEquals(1, delta.opcodes().size());
        assertEquals("two", delta.stringAt(delta.opcodes().get(0).b()));
    }

    private static String lastText(Frame frame) {
        Opcode text = frame.opcodes().stream()
                .filter(o -> o.category() == Categories.PARAMETER && o.command() == Commands.Parameter.SET_TEXT)
                .reduce((a, b) -> b)
                .orElseThrow();
        return frame.stringAt(text.b());
    }
}