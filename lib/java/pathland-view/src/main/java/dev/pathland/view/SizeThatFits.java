package dev.pathland.view;

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
 * structural reconcile (an unchanged selection re-emits **zero** opcodes).
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

    private static View slot(Fit[] fits) {
        // Ascending, stable order = the FIT_QUERY table and the fit-index mapping.
        List<Fit> sorted = new ArrayList<>(List.of(fits));
        sorted.sort(Comparator.comparingDouble(Fit::minWidth));
        WritableSignal<Integer> fitIndex = Signals.signal(0);
        return new FitSlot(sorted, fitIndex);
    }

    /** The structural slot: a `SIZE_THAT_FITS` node with `FIT_QUERY` + one child
     *  (the selected candidate), reconciled on `FIT_CHANGED`. */
    static final class FitSlot implements View {

        private final List<Fit> fits;
        private final WritableSignal<Integer> fitIndex;

        FitSlot(List<Fit> fits, WritableSignal<Integer> fitIndex) {
            this.fits = fits;
            this.fitIndex = fitIndex;
        }

        @Override
        public PathlandNode render(Environment env) {
            PathlandNode node = new PathlandNode(Components.SIZE_THAT_FITS);
            // Capture the incoming scope so the slot re-renders its candidate with
            // the same environment after a fit reconcile (Emitter.reconcileSlot).
            node.environmentForChildren = Environment.current();
            float[] thresholds = new float[fits.size()];
            for (int i = 0; i < fits.size(); i++) {
                thresholds[i] = fits.get(i).minWidth();
            }
            node.properties.put(Properties.FIT_QUERY, thresholds);
            // The selection reads the fit index inside the supplier, so the
            // emitter's structural effect tracks it and reconciles on change.
            node.structuralContent = () -> selectedView();
            // The host routes FIT_CHANGED here; a no-op index (already selected)
            // re-reads the same view and the reconcile emits zero opcodes. The
            // selection is recorded on the node (`fitSelection`) so a parent-subtree
            // re-render can carry it across the reconcile (a nested fit slot must
            // not reset to compact when an outer slot re-renders its candidate).
            // `fitReported` is set first: a full-snapshot slot (all candidates
            // present) stays full until the renderer reports, then the structural
            // reconcile collapses it to the single selected child.
            node.fitInput = index -> {
                if (index != null && index >= 0 && index < fits.size()) {
                    node.fitReported = true;
                    node.fitSelection = index;
                    fitIndex.set(index);
                }
            };
            node.fitSelection = fitIndex.get();
            // In a full/snapshot emission (web SSR, issue #141) the slot carries
            // EVERY candidate as a child, in FIT_QUERY order, so the SSR renders all
            // branches gated by CSS container queries (correct first paint). The
            // live form keeps the single `structuralContent` selection below (only
            // changes travel). The authored DSL is identical either way.
            if (PathlandNode.SNAPSHOT_EMISSION.get()) {
                for (Fit fit : fits) {
                    View candidate = fit.view();
                    if (candidate != null) {
                        node.children.add(candidate.render(env));
                    }
                }
            } else {
                View selected = selectedView();
                if (selected != null) {
                    node.children.add(selected.render(env));
                }
            }
            return node;
        }

        private View selectedView() {
            if (fits.isEmpty()) {
                return null;
            }
            int index = Math.min(Math.max(fitIndex.get(), 0), fits.size() - 1);
            return fits.get(index).view();
        }
    }
}