package dev.pathland.view;

import dev.pathland.view.emit.Emitter;
import dev.pathland.view.emit.ProtocolFrame;
import dev.pathland.view.emit.FrameOpcodeSink;
import dev.pathland.view.emit.Opcode;
import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.emit.RenderResult;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private static Opcode queryOpcode(ProtocolFrame frame) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == Categories.PARAMETER && o.command() == Commands.Parameter.SET_PROPERTY)
                .filter(o -> (o.b() & 0xFFFF) == Properties.FIT_QUERY)
                .findFirst().orElseThrow();
    }

    @Test
    void mountsSlotWithAscendingFitQueryAndOnlyTheSelectedChild() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        View root = VStack.children(SizeThatFits.make(Fit.of(Text.make(t -> t.text("wide")), 640f),
                Fit.of(Text.make(t -> t.text("compact")))));
        RenderResult result = emitter.mount(root, Environment.DEFAULT);

        ProtocolFrame initial = sink.frame();

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
    void snapshotEmissionMountsAllCandidatesThenDowngradesToOneOnFirstFitChanged() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        // The full-snapshot (web SSR) emission mode (issue #141): the slot carries
        // EVERY candidate so the SRR renders all branches gated by CSS container
        // queries. The authoring DSL is unchanged — only the emitter's mode flag.
        emitter.snapshotEmission(true);
        View root = VStack.children(SizeThatFits.make(Fit.of(Text.make(t -> t.text("wide")), 640f),
                Fit.of(Text.make(t -> t.text("compact")))));
        RenderResult result = emitter.mount(root, Environment.DEFAULT);

        ProtocolFrame initial = sink.frame();
        // Both candidates transmit in snapshot mode.
        assertTrue(containsText(initial, "wide"), "snapshot carries every candidate");
        assertTrue(containsText(initial, "compact"), "snapshot carries every candidate");
        // The slot's child list has both candidates + FIT_QUERY intact.
        assertTrue(initial.opcodes().stream().anyMatch(o ->
                o.category() == Categories.TREE && o.command() == Commands.Tree.CREATE_NODE
                        && o.b() == Components.SIZE_THAT_FITS), "slot created");
        // The first FIT_CHANGED (even an index equal to the fallback) collapses
        // N→1: the retained tree becomes the ordinary single-selected-child form.
        Consumer<Integer> fit = result.fitInputs().get(slotId(result));
        fit.accept(1);
        ProtocolFrame downgrade = sink.frame();
        assertTrue(countOps(downgrade, Categories.TREE, Commands.Tree.REMOVE_CHILD) >= 1, "extra candidate removed");
        // The SELECTED candidate's subtree is RETAINED (zero churn): the downgrade
        // removes the unselected candidate only and never re-creates the winner —
        // a positional morph would relocate the winner into the SSR's loser wrapper.
        assertFalse(containsText(downgrade, "wide"), "the selected candidate is retained, not re-emitted");
        assertFalse(containsText(downgrade, "compact"), "the other candidate is downgraded away");
        assertEquals(0, countOps(downgrade, Categories.TREE, Commands.Tree.CREATE_NODE),
                "the selected candidate is kept, not re-created");

        // A subsequent mount frame (resync) emits the single selected child only.
        emitter.renderFull();
        ProtocolFrame resync = sink.frame();
        assertTrue(containsText(resync, "wide"), "resync re-emits the selected child");
        assertFalse(containsText(resync, "compact"), "the dowgraded tree no longer carries the other candidate");
    }

    @Test
    void resyncBeforeFirstFitChangedReemitsTheSnapshotForm() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        emitter.snapshotEmission(true);
        View root = VStack.children(SizeThatFits.make(Fit.of(Text.make(t -> t.text("wide")), 640f),
                Fit.of(Text.make(t -> t.text("compact")))));
        emitter.mount(root, Environment.DEFAULT);

        // No FIT_CHANGED yet: the retained tree is still the snapshot form, so a
        // resync re-emits BOTH candidates. The client reuses its SSR gate (the
        // candidate wrappers + `@container` band rules) and keeps showing the
        // width-correct candidate — a resync must NOT collapse the slot.
        emitter.renderFull();
        ProtocolFrame resync = sink.frame();
        assertTrue(containsText(resync, "compact"), "resync carries the fallback candidate");
        assertTrue(containsText(resync, "wide"), "resync carries the other candidate too (snapshot form)");
    }

    @Test
    void fitChangeSwapsTheChildAndAnUnchangedIndexEmitsZeroOpcodes() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        // Different candidate components: a fallback TEXT and a wide BUTTON — the swap
        // must be STRUCTURAL (TREE deltas), not a same-component SET_TEXT.
        View root = VStack.children(SizeThatFits.make(
                Fit.of(Button.make(b -> b.title("wide").action(() -> { })), 640f),
                Fit.of(Text.make(t -> t.text("compact")))));
        RenderResult result = emitter.mount(root, Environment.DEFAULT);

        Consumer<Integer> fit = result.fitInputs().get(slotId(result));
        assertNotNull(fit, "fit sink for the slot");

        // A fit change to index 1 (wide) swaps the slot child: remove/delete the old
        // TEXT, create/insert the BUTTON subtree (shell + label child).
        fit.accept(1);
        ProtocolFrame swap = sink.frame();
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
        View nested = SizeThatFits.make(Fit.of(Text.make(t -> t.text("wide")), 600f),
                Fit.of(Text.make(t -> t.text("compact"))));
        View root = SizeThatFits.make(Fit.of(Text.make(t -> t.text("fallback")), 1024f),
                Fit.of(VStack.children(nested)));
        emitter.mount(root, Environment.DEFAULT);
        assertEquals(1, sink.framesProduced(), "mount emits exactly one frame (no spurious reconcile frame)");
    }

    /** A fit change to the nested slot swaps only that slot's child. */
    @Test
    void nestedSlotFitChangeSwapsOnlyTheNestedSelection() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        View nested = SizeThatFits.make(Fit.of(Text.make(t -> t.text("wide")), 600f),
                Fit.of(Text.make(t -> t.text("compact"))));
        View root = SizeThatFits.make(Fit.of(Text.make(t -> t.text("fallback")), 1024f),
                Fit.of(VStack.children(nested)));
        RenderResult result = emitter.mount(root, Environment.DEFAULT);
        assertEquals(2, result.fitInputs().size(), "both slots route a fit sink");

        int nestedId = slotByThreshold(result, sink.frame(), 600f);
        result.fitInputs().get(nestedId).accept(1);
        ProtocolFrame swap = sink.frame();
        assertTrue(containsText(swap, "wide"), "the nested slot swapped to its wide candidate");
        assertFalse(containsText(swap, "fallback"), "the outer slot's candidate is untouched");
    }

    /** An outer slot's candidate swap must NOT reset a nested slot's selection:
     *  re-rendering the parent subtree re-instantiates the inner slot with a default
     *  index — a reset would drop the nested fit's chosen candidate (e.g. the player
     *  bar's volume controls vanish when the root row shows the now-playing sidebar)
     *  and the DOM client never re-reports an unchanged width, so it stays lost.
     *  The nested slot is deliberately re-built FRESH on every render, mirroring a
     *  control body (PlayerControls.body) re-run on a subtree re-render. */
    @Test
    void outerSwapPreservesNestedSlotSelection() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        // Both root candidates re-build the nested slot each render (a fresh fit
        // signal defaulting to 0) — like the shared player bar behind the music
        // player's compact/wide root rows.
        View root = SizeThatFits.make(
                Fit.of(rebuiltNested("more"), 1024f),
                Fit.of(rebuiltNested("less")));
        RenderResult result = emitter.mount(root, Environment.DEFAULT);
        ProtocolFrame mountFrame = sink.frame();
        int nestedId = slotByThreshold(result, mountFrame, 600f);
        int rootId = slotByThreshold(result, mountFrame, 1024f);

        // 1. The nested slot picks its wide candidate (volume controls shown).
        result.fitInputs().get(nestedId).accept(1);
        assertTrue(containsText(sink.frame(), "wide"), "nested slot goes wide");

        // 2. The root row switches to the OTHER candidate (now-playing sidebar) —
        //    the whole subtree re-renders, rebuilding the nested slot.
        result.fitInputs().get(rootId).accept(1);
        ProtocolFrame swap = sink.frame();
        // The regression: without carrying the selection across the reconcile, the
        // rebuilt nested slot resets to compact and re-emits its fallback text.
        assertFalse(containsText(swap, "compact"), "nested fit keeps its wide selection across the outer swap");
        assertTrue(containsText(swap, "more"), "the outer candidate change is the only swap delta");

        // 3. The nested slot's fit sink still routes (a later resize can move it).
        result.fitInputs().get(nestedId).accept(0);
        assertTrue(containsText(sink.frame(), "compact"), "the nested slot can still swap back");
    }

    /**
     * A full-snapshot emission whose candidates share a subtree (the music player's
     * "main area" composed into both rows) materializes it once per candidate. Every
     * copy must get a **distinct** node id — the frame's unique-node-id invariant
     * (spec/OPCODE.md §Frame Lifecycle, spec/PRIMITIVES.md §SizeThatFits).
     */
    @Test
    void snapshotWithASharedSubtreeEmitsUniqueNodeIds() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        emitter.snapshotEmission(true);
        // The shared subtree is reused *inside* two distinct candidate roots — the
        // allowed form (each candidate renders its own fresh copy).
        View shared = VStack.children(
                Text.make(t -> t.text("shared")),
                Text.make(t -> t.text("detail")));
        View wide = HStack.children(shared, Text.make(t -> t.text("wide")));
        View compact = HStack.children(shared, Text.make(t -> t.text("compact")));
        View root = VStack.children(SizeThatFits.make(Fit.of(wide, 640f), Fit.of(compact)));
        emitter.mount(root, Environment.DEFAULT);

        ProtocolFrame frame = sink.frame();
        assertUniqueNodeIds(frame);
        assertTrue(containsText(frame, "shared"), "the shared subtree rides the snapshot");
        assertTrue(containsText(frame, "wide") && containsText(frame, "compact"),
                "both candidates are materialized");
    }

    /**
     * A custom view that returns the **same retained node** on every render (a cached
     * node — the media controller's pinned id is the real-world instance) places one
     * id at two positions. The emitter must fail loudly instead of emitting a frame
     * that collapses in the renderer's id-keyed map (spec/OPCODE.md §Frame Lifecycle).
     */
    @Test
    void aRetainedNodeReusedAcrossCandidatesTripsTheUniqueIdGuard() {
        PathlandNode cached = new PathlandNode(Components.TEXT);
        View shared = new View() {
            @Override
            public PathlandNode renderNode(Environment env) {
                return cached;
            }
        };
        View wide = VStack.children(shared, Text.make(t -> t.text("wide")));
        View compact = VStack.children(shared, Text.make(t -> t.text("compact")));

        Emitter emitter = new Emitter(new FrameOpcodeSink());
        emitter.snapshotEmission(true);
        View root = VStack.children(SizeThatFits.make(Fit.of(wide, 640f), Fit.of(compact)));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> emitter.mount(root, Environment.DEFAULT));
        assertTrue(error.getMessage().contains("duplicate node id"), error.getMessage());
    }

    /** The duplicate-id scan helper detects a hand-built violating tree. */
    @Test
    void duplicateIdsDetectsARepeatedNodeId() {
        PathlandNode root = new PathlandNode(Components.VSTACK);
        root.id = 1;
        PathlandNode a = new PathlandNode(Components.TEXT);
        a.id = 2;
        PathlandNode b = new PathlandNode(Components.TEXT);
        b.id = 2;
        root.children.add(a);
        root.children.add(b);

        assertEquals(List.of(2), Emitter.duplicateIds(root));
        b.id = 3;
        assertTrue(Emitter.duplicateIds(root).isEmpty(), "a clean tree reports no duplicates");
    }

    /**
     * The same `View` instance may not be the candidate of two `Fit`s: it would be
     * materialized twice with shared mutable state and could hand out the same id.
     * Sharing a subtree inside distinct candidate roots stays allowed (above).
     */
    @Test
    void rejectsTheSameViewInstanceAsTwoCandidates() {
        View shared = Text.make(t -> t.text("shared"));
        IllegalStateException fits = assertThrows(IllegalStateException.class,
                () -> SizeThatFits.make(Fit.of(shared, 640f), Fit.of(shared)));
        assertTrue(fits.getMessage().contains("at most one"), fits.getMessage());
        IllegalStateException candidates = assertThrows(IllegalStateException.class,
                () -> SizeThatFits.make(shared, shared));
        assertTrue(candidates.getMessage().contains("at most one"), candidates.getMessage());
    }

    /** Every `CREATE_NODE` id in the frame is distinct. */
    private static void assertUniqueNodeIds(ProtocolFrame frame) {
        Set<Integer> seen = new HashSet<>();
        for (Opcode op : frame.opcodes()) {
            if (op.category() == Categories.TREE && op.command() == Commands.Tree.CREATE_NODE) {
                assertTrue(seen.add(op.a()), "duplicate node id " + op.a() + " in the frame");
            }
        }
    }

    /**
     * The music player's shape: a `MediaController` + a `Video` surface inside a
     * fit-slot candidate, with the controller marker outside the swapping branch
     * (spec/MEDIA.md). Re-selecting the **same** candidate must emit nothing — an
     * identical recompute stays zero-op even with a pinned controller node.
     */
    @Test
    void mediaControllerMountOutsideTheSlotEmitsZeroOnAnUnchangedSelection() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        MediaController player = new MediaController("/assets/track1.mp3");

        View root = VStack.children(
                player.attach(),
                SizeThatFits.make(
                        Fit.of(VStack.children(Text.make(t -> t.text("wide")), Video.make(player)), 640f),
                        Fit.of(VStack.children(Text.make(t -> t.text("compact")), Video.make(player)))));
        RenderResult result = emitter.mount(root, Environment.DEFAULT);
        int slot = slotId(result);

        // The controller's pinned node id (its playback key).
        long controllerId = sink.frame().opcodes().stream()
                .filter(o -> o.category() == Categories.TREE && o.command() == Commands.Tree.CREATE_NODE)
                .filter(o -> o.b() == Components.MEDIA_CONTROLLER)
                .mapToLong(Opcode::a).findFirst().orElseThrow();

        // Swap to the wide candidate (a real delta)…
        result.fitInputs().get(slot).accept(1);
        assertTrue(containsText(sink.frame(), "wide"), "the wide candidate swapped in");
        assertNoIdCollision(sink.frame(), controllerId, "after the first swap");

        // …then re-select the SAME index: an identical recompute emits nothing.
        long frames = sink.framesProduced();
        result.fitInputs().get(slot).accept(1);
        assertEquals(frames, sink.framesProduced(), "an identical recompute emits no frame");

        // …and swap back and forth: the pinned controller id is never handed to
        // another node (a collision would make the renderer drop/restart playback).
        result.fitInputs().get(slot).accept(0);
        assertNoIdCollision(sink.frame(), controllerId, "after swapping back");
        result.fitInputs().get(slot).accept(1);
        assertNoIdCollision(sink.frame(), controllerId, "after swapping again");
    }

    /** The pinned controller id must never be *created* as a different component. */
    private static void assertNoIdCollision(ProtocolFrame frame, long controllerId, String when) {
        frame.opcodes().stream()
                .filter(o -> o.category() == Categories.TREE && o.command() == Commands.Tree.CREATE_NODE)
                .filter(o -> o.a() == controllerId && o.b() != Components.MEDIA_CONTROLLER)
                .findFirst()
                .ifPresent(o -> { throw new AssertionError(
                        "a node re-created the pinned controller id " + controllerId
                                + " as component 0x" + Integer.toHexString((int) o.b()) + " " + when); });
    }

    /** Same, with the attach marker INSIDE the swapping candidate (the fragile
     *  shape): the controller node is re-created with the candidate, and the
     *  surface rebinds — but an unchanged selection must still emit nothing. */
    @Test
    void mediaControllerInsideTheSlotEmitsZeroOnAnUnchangedSelection() {
        FrameOpcodeSink sink = new FrameOpcodeSink();
        Emitter emitter = new Emitter(sink);
        MediaController player = new MediaController("/assets/track1.mp3");

        View root = SizeThatFits.make(
                Fit.of(VStack.children(player.attach(), Video.make(player), Text.make(t -> t.text("wide"))), 640f),
                Fit.of(VStack.children(player.attach(), Video.make(player), Text.make(t -> t.text("compact")))));
        RenderResult result = emitter.mount(root, Environment.DEFAULT);
        int slot = slotId(result);

        result.fitInputs().get(slot).accept(1);
        assertTrue(containsText(sink.frame(), "wide"), "the wide candidate swapped in");

        long frames = sink.framesProduced();
        result.fitInputs().get(slot).accept(1);
        assertEquals(frames, sink.framesProduced(), "an identical recompute emits no frame");
    }

    /**
     * A bound control materialized by a structural reconcile must carry
     * {@code BINDING_ID} (the desktop renderer's raw-input gate), or its
     * {@code VALUE_CHANGED} is dropped — the "volume slider moves but the audio
     * never changes" bug: the player bar's wide volume slider is created when its
     * nested {@code SIZE_THAT_FITS} swaps to the wide controls cluster.
     */
    @Test
    void reconcileCreatedBoundControlsCarryTheBindingGate() {
        GatingFrameSink sink = new GatingFrameSink();
        Emitter emitter = new Emitter(sink);
        var volume = dev.pathland.view.signal.Signals.signal(0.7f);
        View root = SizeThatFits.make(
                Fit.of(VStack.children(Text.make(t -> t.text("compact")))),
                Fit.of(VStack.children(Slider.make(s -> s.value(volume).in(0f, 1f))), 600f));
        RenderResult result = emitter.mount(root, Environment.DEFAULT);
        int slot = slotId(result);

        // The mount shows the fallback, so the slider is created by the fit swap.
        assertFalse(sink.frame().opcodes().stream().anyMatch(o ->
                        o.category() == Categories.TREE && o.command() == Commands.Tree.CREATE_NODE
                                && (o.b() & 0xffff) == Components.SLIDER),
                "the compact candidate has no slider at mount");

        result.fitInputs().get(slot).accept(1);
        ProtocolFrame swap = sink.frame();
        int slider = swap.opcodes().stream()
                .filter(o -> o.category() == Categories.TREE && o.command() == Commands.Tree.CREATE_NODE
                        && (o.b() & 0xffff) == Components.SLIDER)
                .mapToInt(Opcode::a)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the swap creates the wide slider"));
        assertTrue(swap.opcodes().stream().anyMatch(o ->
                        o.category() == Categories.PARAMETER && o.command() == Commands.Parameter.SET_PROPERTY
                                && o.a() == slider && (o.b() & 0xffff) == Properties.BINDING_ID),
                "the reconcile-created slider carries BINDING_ID so VALUE_CHANGED round-trips");
    }

    /** A frame sink for the desktop-style path (gated raw inputs). */
    private static final class GatingFrameSink extends FrameOpcodeSink {
        @Override
        public boolean requiresBindingId() {
            return true;
        }
    }

    /** A custom-style body: re-builds the nested fit slot (fresh signal, default 0) on
     *  every render, like `AudioStyle.makeBody` does when its parent subtree re-renders. */
    private static View rebuiltNested(String suffix) {
        return new View() {
            @Override
            public PathlandNode renderNode(Environment env) {
                View nested = SizeThatFits.make(Fit.of(Text.make(t -> t.text("wide")), 600f),
                        Fit.of(Text.make(t -> t.text("compact"))));
                return VStack.children(nested, Text.make(t -> t.text(suffix))).renderNode(env);
            }
        };
    }

    private static int slotByThreshold(RenderResult result, ProtocolFrame frame, float threshold) {
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

    private static long count(ProtocolFrame frame, int command) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == Categories.PARAMETER && o.command() == command)
                .count();
    }

    private static long countOps(ProtocolFrame frame, int category, int command) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == category && o.command() == command)
                .count();
    }

    private static boolean containsText(ProtocolFrame frame, String text) {
        return frame.opcodes().stream()
                .filter(o -> o.category() == Categories.PARAMETER && o.command() == Commands.Parameter.SET_TEXT)
                .anyMatch(o -> frame.stringAt(o.b()).contains(text));
    }
}