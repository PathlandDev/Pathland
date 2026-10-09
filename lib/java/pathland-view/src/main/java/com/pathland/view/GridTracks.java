package com.pathland.view;

import com.pathland.view.signal.ConstantSignal;
import com.pathland.view.signal.Signal;
import com.pathland.view.signal.Signals;

import java.util.List;

/** Shared {@code GRID_TRACKS} wiring for {@code Grid}/{@code LazyVGrid}/{@code LazyHGrid}. */
final class GridTracks {

    private GridTracks() {}

    /** Join per-track sizes into the {@code GRID_TRACKS} STRING spec. */
    static String join(List<GridItem> tracks) {
        StringBuilder sb = new StringBuilder();
        for (GridItem t : tracks) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(t.token());
        }
        return sb.toString();
    }

    /** A (reactive) {@code GRID_TRACKS} string signal over the track list. */
    static Signal<String> signal(Signal<List<GridItem>> tracks) {
        if (tracks instanceof ConstantSignal) {
            return Signals.constant(join(tracks.get()));
        }
        return Signals.computed(() -> join(tracks.get()));
    }
}
