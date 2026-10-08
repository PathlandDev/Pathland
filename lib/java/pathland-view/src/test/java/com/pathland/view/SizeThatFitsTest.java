package com.pathland.view;

import com.pathland.view.emit.Emitter;
import com.pathland.view.emit.Frame;
import com.pathland.view.emit.FrameOpcodeSink;
import com.pathland.view.emit.Opcode;
import com.pathland.view.emit.PathlandNode;
import com.pathland.view.emit.RenderResult;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The `SIZE_THAT_FITS` fit slot (spec DSL.md / PRIMITIVES.md): mounts as the
 * slot + `FIT_QUERY` (LIST) + the single selected child; a `FIT_CHANGED` routed
 * into the fit sink swaps the child via structural reconcile (zero opcodes for
 * an unchanged selection); unselected candidates are never transmitted.
 */
class SizeThatFitsTest {

    private static final long FIT_QUERY_LIST_FLAG = ((long) ValueTypes.LIST) << 16;

    private static int le32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
            | ((bytes[offset + 1] & 0xFF) << 8)
            | ((bytes[offset + 2] & 0xFF) << 16)
            | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static Opcode queryOpcode(Frame frame) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == Categories.PARAMETER && o.command() == Commands.Parameter.SET_PROPERTY)
                .filter(o -> (o.b() & 0xFFFF) == Properties.FIT_QUERY)
                .findFirst().orElseThrow();
    }

    @Test
    void mountsSlotWithAscendingFitQueryAndOnlyTheSelectedChild() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        View root = VStack.of(SizeThatFits.of(Fit.of(Text.of("wide"), 640f), Fit.of(Text.of("compact"))));
        RenderResult result = emitter.mount(root, Environment.DEFAULT);

        Frame initial = sink.frame();

        // The slot is a SIZE_THAT_FITS node; its child is the SELECTED (fallback) only.
        assertTrue(initial.opcodes().stream().anyMatch(o ->
                o.category() == Categories.TREE
                        && o.command() == Commands.Tree.CREATE_NODE
                        && o.b() == Components.SIZE_THAT_FITS), "slot created");
        // FIT_QUERY rides the LIST value type with the ascending threshold table
        // [0 (compact) < 640 (wide)] — the arena order = the fit-index mapping.
        Opcode query = queryOpcode(initial);
        assertEquals(FIT_QUERY_LIST_FLAG, query.b() & 0xFFFF_0000L, "valueType=LIST on FIT_QUERY");
        byte[] strings = initial.strings();
        int offset = query.c();
        assertEquals(2, le32(strings, offset), "two candidates");
        assertEquals(0f, Float.intBitsToFloat(le32(strings, offset + 4)), "fallback threshold 0");
        assertEquals(640f, Float.intBitsToFloat(le32(strings, offset + 8)), "wide threshold");
        // Only the selected (threshold-0) candidate is transmitted.
        assertEquals(1, count(initial, Commands.Parameter.SET_TEXT));
        assertTrue(containsText(initial, "compact"), "fallback candidate transmitted");
        assertFalse(containsText(initial, "wide"), "unselected candidate never transmitted");
        // The fit sink is registered for the slot's node id.
        assertFalse(result.fitInputs().isEmpty(), "fit sink routed");
    }

    @Test
    void fitChangeSwapsTheChildAndAnUnchangedIndexEmitsZeroOpcodes() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        // Different candidate components: a fallback TEXT and a wide BUTTON — the swap
        // must be STRUCTURAL (TREE deltas), not a same-component SET_TEXT.
        View root = VStack.of(SizeThatFits.of(Fit.of(Button.of("wide", () -> { }), 640f), Fit.of(Text.of("compact"))));
        RenderResult result = emitter.mount(root, Environment.DEFAULT);

        Consumer<Integer> fit = result.fitInputs().get(slotId(result));
        assertNotNull(fit, "fit sink for the slot");

        // A fit change to index 1 (wide) swaps the slot child: remove/delete the old
        // TEXT, create/insert the BUTTON subtree (shell + label child).
        fit.accept(1);
        Frame swap = sink.frame();
        assertTrue(countOps(swap, Categories.TREE, Commands.Tree.REMOVE_CHILD) >= 1, "old child removed");
        assertTrue(countOps(swap, Categories.TREE, Commands.Tree.DELETE_NODE) >= 1, "old node deleted");
        assertTrue(countOps(swap, Categories.TREE, Commands.Tree.CREATE_NODE) >= 1, "new subtree created");
        assertTrue(containsText(swap, "wide"), "the wide candidate is emitted after the fit change");

        // An unchanged index re-reads the same selection — zero opcodes.
        int frames = sink.framesProduced();
        fit.accept(1);
        assertEquals(frames, sink.framesProduced(), "identical fit index emits no frame");

        // Back to the fallback.
        fit.accept(0);
        assertTrue(containsText(sink.frame(), "compact"), "back to the fallback candidate");
    }

    @Test
    void nestedSlotMountEmitsASingleMountFrame() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        // An outer slot whose selected candidate nests another slot (e.g. the music
        // player's root row wrapping a player-bar controls SizeThatFits). The outer
        // structural effect's first merge must NOT re-emit the nested slot's
        // unchanged FIT_QUERY — the threshold `float[]` is recreated per render, so
        // a reference comparison would emit a spurious post-mount frame.
        View nested = SizeThatFits.of(Fit.of(Text.of("wide"), 600f), Fit.of(Text.of("compact")));
        View root = SizeThatFits.of(Fit.of(Text.of("fallback"), 1024f), Fit.of(VStack.of(nested)));
        emitter.mount(root, Environment.DEFAULT);
        assertEquals(1, sink.framesProduced(), "mount emits exactly one frame (no spurious reconcile frame)");
    }

    /** A fit change to the nested slot swaps only that slot's child. */
    @Test
    void nestedSlotFitChangeSwapsOnlyTheNestedSelection() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        View nested = SizeThatFits.of(Fit.of(Text.of("wide"), 600f), Fit.of(Text.of("compact")));
        View root = SizeThatFits.of(Fit.of(Text.of("fallback"), 1024f), Fit.of(VStack.of(nested)));
        RenderResult result = emitter.mount(root, Environment.DEFAULT);
        assertEquals(2, result.fitInputs().size(), "both slots route a fit sink");

        int nestedId = slotByThreshold(result, sink.frame(), 600f);
        result.fitInputs().get(nestedId).accept(1);
        Frame swap = sink.frame();
        assertTrue(containsText(swap, "wide"), "the nested slot swapped to its wide candidate");
        assertFalse(containsText(swap, "fallback"), "the outer slot's candidate is untouched");
    }

    /** An outer slot's candidate swap must NOT reset a nested slot's selection:
     *  re-rendering the parent subtree re-instantiates the inner slot with a default
     *  index — a reset would drop the nested fit's chosen candidate (e.g. the player
     *  bar's volume controls vanish when the root row shows the now-playing sidebar)
     *  and the DOM client never re-reports an unchanged width, so it stays lost.
     *  The nested slot is deliberately re-built FRESH on every render, mirroring a
     *  custom style body (PlayerControlsStyle.makeBody) re-run on a subtree re-render. */
    @Test
    void outerSwapPreservesNestedSlotSelection() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        // Both root candidates re-build the nested slot each render (a fresh fit
        // signal defaulting to 0) — like the shared player bar behind the music
        // player's compact/wide root rows.
        View root = SizeThatFits.of(
                Fit.of(rebuiltNested("more"), 1024f),
                Fit.of(rebuiltNested("less")));
        RenderResult result = emitter.mount(root, Environment.DEFAULT);
        Frame mountFrame = sink.frame();
        int nestedId = slotByThreshold(result, mountFrame, 600f);
        int rootId = slotByThreshold(result, mountFrame, 1024f);

        // 1. The nested slot picks its wide candidate (volume controls shown).
        result.fitInputs().get(nestedId).accept(1);
        assertTrue(containsText(sink.frame(), "wide"), "nested slot goes wide");

        // 2. The root row switches to the OTHER candidate (now-playing sidebar) —
        //    the whole subtree re-renders, rebuilding the nested slot.
        result.fitInputs().get(rootId).accept(1);
        Frame swap = sink.frame();
        // The regression: without carrying the selection across the reconcile, the
        // rebuilt nested slot resets to compact and re-emits its fallback text.
        assertFalse(containsText(swap, "compact"), "nested fit keeps its wide selection across the outer swap");
        assertTrue(containsText(swap, "more"), "the outer candidate change is the only swap delta");

        // 3. The nested slot's fit sink still routes (a later resize can move it).
        result.fitInputs().get(nestedId).accept(0);
        assertTrue(containsText(sink.frame(), "compact"), "the nested slot can still swap back");
    }

    /** A custom-style body: re-builds the nested fit slot (fresh signal, default 0) on
     *  every render, like `AudioStyle.makeBody` does when its parent subtree re-renders. */
    private static View rebuiltNested(String suffix) {
        return new View() {
            @Override
            public PathlandNode render(Environment env) {
                View nested = SizeThatFits.of(Fit.of(Text.of("wide"), 600f), Fit.of(Text.of("compact")));
                return VStack.of(nested, Text.of(suffix)).render(env);
            }
        };
    }

    private static int slotByThreshold(RenderResult result, Frame frame, float threshold) {
        for (int id : result.fitInputs().keySet()) {
            Opcode query = frame.opcodes().stream()
                    .filter(o -> o.category() == Categories.PARAMETER
                            && o.command() == Commands.Parameter.SET_PROPERTY
                            && o.a() == id && (o.b() & 0xFFFF) == Properties.FIT_QUERY)
                    .findFirst().orElseThrow();
            byte[] strings = frame.strings();
            int offset = query.c();
            int count = le32(strings, offset);
            for (int i = 0; i < count; i++) {
                if (Float.intBitsToFloat(le32(strings, offset + 4 + i * 4)) == threshold) {
                    return id;
                }
            }
        }
        throw new AssertionError("no slot with threshold " + threshold);
    }

    private static int slotId(RenderResult result) {
        return result.fitInputs().keySet().iterator().next();
    }

    private static long count(Frame frame, int command) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == Categories.PARAMETER && o.command() == command)
                .count();
    }

    private static long countOps(Frame frame, int category, int command) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == category && o.command() == command)
                .count();
    }

    private static boolean containsText(Frame frame, String text) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == Categories.PARAMETER && o.command() == Commands.Parameter.SET_TEXT)
                .anyMatch(o -> frame.stringAt(o.b()).contains(text));
    }
}