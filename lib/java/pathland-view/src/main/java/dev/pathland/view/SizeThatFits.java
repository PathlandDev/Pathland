package dev.pathland.view;

import dev.pathland.view.emit.Emitter;
import dev.pathland.view.emit.PathlandNode;
import dev.pathland.view.signal.Signals;
import dev.pathland.view.signal.WritableSignal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A `SIZE_THAT_FITS` **fit slot** (a SwiftUI `ViewThatFits`-style container):
 * it shows **one child at a time — the currently selected candidate — so only
 * the selected child is ever transmitted**. The candidates live here, in the
 * DSL; the wire carries the `FIT_QUERY` threshold table (the candidates'
 * `minWidth`s, ascending) plus the selected child. The renderer measures its
 * own allocated width, derives the fit locally, and reports `FIT_CHANGED`; the
 * host routes it into this slot's fit sink, which swaps the child through the
 * component re-render (an unchanged selection re-emits **zero** opcodes).
 *
 * <p>Candidates are ordered by their `minWidth` in the emitted `FIT_QUERY`
 * (ascending, stable); the fit index is the item position, so with a
 * {@code Fit.of(compact)} (minWidth 0) + {@code Fit.of(wide, 640)} the slot
 * shows <em>compact</em> until the renderer reports index 1 (width ≥ 640).
 *
 * <pre>{@code
 * import static dev.pathland.view.SizeThatFits.fit;
 *
 * SizeThatFits.make(
 *     Fit.of(wideLayout, 640f),   // shown when the slot is ≥ 640 wide
 *     Fit.of(compactLayout));     // minWidth 0 — the always-valid fallback
 * }</pre>
 */
public final class SizeThatFits {

    private SizeThatFits() {}

    /** A slot over one-layout-per-candidate `View`s (each implicitly minWidth 0 —
     *  order in the argument list is the fit order, index 0 first). */
    public static View make(View... candidates) {
        Objects.requireNonNull(candidates, "candidates");
        Fit[] fits = new Fit[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            fits[i] = Fit.of(candidates[i]);
        }
        return slot(fits);
    }

    /** A slot over width-keyed candidates. */
    public static View make(Fit... fits) {
        Objects.requireNonNull(fits, "fits");
        return slot(fits);
    }

    /**
     * Reject the same {@link View} instance used as the candidate of more than one
     * {@code Fit}. A reused instance is materialized once per candidate, so it
     * occupies two independent positions in the retained tree; that leaks the
     * view's mutable state across branches and lets an identity-stable node (a media
     * controller, a stateful custom view) hand out the same node id twice. The frame's
     * unique-node-id invariant (spec/OPCODE.md §Frame Lifecycle) forbids that, so fail
     * loudly at the authoring surface instead of corrupting id-keyed consumers.
     *
     * <p>Sharing a subtree <em>inside</em> two distinct candidate roots (e.g. a
     * "main area" composed into both rows) is allowed: each candidate renders its own
     * fresh nodes, which get distinct ids.
     */
    private static void rejectReusedCandidates(Fit[] fits) {
        for (int i = 0; i < fits.length; i++) {
            View candidate = fits[i].view();
            for (int j = i + 1; j < fits.length; j++) {
                if (candidate == fits[j].view()) {
                    throw new IllegalStateException(
                            "a View instance can be used as the candidate of at most one "
                                    + "SizeThatFits Fit: " + candidate.getClass().getName()
                                    + " is passed to candidates " + i + " and " + j
                                    + ". Create a fresh view instance per candidate.");
                }
            }
        }
    }

    private static View slot(Fit[] fits) {
        rejectReusedCandidates(fits);
        // Ascending, stable order = the FIT_QUERY table and the fit-index mapping.
        List<Fit> sorted = new ArrayList<>(List.of(fits));
        sorted.sort(Comparator.comparingDouble(Fit::minWidth));
        WritableSignal<Integer> fitIndex = Signals.signal(0);
        WritableSignal<Boolean> fitReported = Signals.signal(false);
        return new FitSlot(sorted, fitIndex, fitReported);
    }

    /** The component slot: a `SIZE_THAT_FITS` node with `FIT_QUERY` + one child
     *  (the selected candidate). The fit index and the first-report flag are signals
     *  read by the slot's tracked render, so a `FIT_CHANGED` re-renders only this
     *  component (an unchanged selection emits zero opcodes). */
    static final class FitSlot implements View {

        private final List<Fit> fits;
        private final WritableSignal<Integer> fitIndex;
        private final WritableSignal<Boolean> fitReported;

        FitSlot(
                List<Fit> fits,
                WritableSignal<Integer> fitIndex,
                WritableSignal<Boolean> fitReported) {
            this.fits = fits;
            this.fitIndex = fitIndex;
            this.fitReported = fitReported;
        }

        @Override
        public PathlandNode renderNode(Environment env) {
            PathlandNode node = new PathlandNode(Components.SIZE_THAT_FITS);
            float[] thresholds = new float[fits.size()];
            for (int i = 0; i < fits.size(); i++) {
                thresholds[i] = fits.get(i).minWidth();
            }
            node.properties.put(Properties.FIT_QUERY, thresholds);
            // A parent re-render creates a fresh FitSlot (default index 0). Carry the
            // retained selection across that re-render from the node this slot replaces.
            Emitter emitter = Emitter.current();
            Integer hint = emitter == null ? null : emitter.currentSlotOldFitSelection();
            if (hint != null && hint != fitIndex.get()) {
                Signals.untracked(() -> {
                    fitIndex.set(hint);
                    return null;
                });
            }
            int index = Math.min(Math.max(fitIndex.get(), 0), Math.max(fits.size() - 1, 0));
            boolean reported = fitReported.get();
            node.fitSelection = index;
            node.fitReported = reported;
            // The host routes FIT_CHANGED here; the flag makes even a no-op index
            // (already selected) collapse the SSR full-snapshot form to one child.
            node.fitInput = incoming -> {
                if (incoming != null && incoming >= 0 && incoming < fits.size()) {
                    node.fitReported = true;
                    node.fitSelection = incoming;
                    fitIndex.set(incoming);
                    fitReported.set(true);
                }
            };
            // In a full/snapshot emission (web SSR, issue #141) the slot carries
            // EVERY candidate as a child, in FIT_QUERY order, so the SSR renders all
            // branches gated by CSS container queries (correct first paint). The
            // live form keeps the single selected candidate.
            if (PathlandNode.SNAPSHOT_EMISSION.get() && !reported) {
                for (Fit fit : fits) {
                    View candidate = fit.view();
                    if (candidate != null) {
                        node.children.add(candidate.render(env));
                    }
                }
            } else {
                View selected = fits.isEmpty() ? null : fits.get(index).view();
                if (selected != null) {
                    node.children.add(selected.render(env));
                }
            }
            return node;
        }
    }
}