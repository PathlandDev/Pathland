package com.pathland.view;

import java.util.Objects;

/**
 * A `SizeThatFits` candidate: a view plus the minimum container width at which
 * it is selected. The candidates' thresholds form the wire `FIT_QUERY` table
 * (ascending); the renderer reports a fit index against that order and the slot
 * shows the corresponding candidate.
 *
 * @param view     the candidate view (only the selected one is ever transmitted)
 * @param minWidth the minimum slot width (logical points) for this candidate
 */
public record Fit(View view, float minWidth) {

    /** A candidate with threshold 0 — always applicable (the fallback). */
    public static Fit of(View view) {
        return new Fit(Objects.requireNonNull(view, "view"), 0f);
    }

    /** A candidate shown when the slot's width is at least {@code minWidth}. */
    public static Fit of(View view, float minWidth) {
        return new Fit(Objects.requireNonNull(view, "view"), minWidth);
    }
}