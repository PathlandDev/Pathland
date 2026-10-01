package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/** A virtualized vertical grid (windowed realization of cells). */
public final class LazyVGrid implements View {

    private final List<View> children;
    private final Alignment alignment;
    private final Float spacing;
    private final Integer columns;
    private final List<GridItem> tracks;

    private LazyVGrid(View... children) {
        this(null, null, null, null, List.of(children));
    }

    private LazyVGrid(int columns, View... children) {
        this(null, null, columns, null, List.of(children));
    }

    private LazyVGrid(Alignment alignment, float spacing, View... children) {
        this(alignment, spacing, null, null, List.of(children));
    }

    private LazyVGrid(int columns, Alignment alignment, float spacing, View... children) {
        this(alignment, spacing, columns, null, List.of(children));
    }

    private LazyVGrid(List<GridItem> tracks, View... children) {
        this(null, null, null, tracks, List.of(children));
    }

    private LazyVGrid(List<GridItem> tracks, Alignment alignment, float spacing, View... children) {
        this(alignment, spacing, null, tracks, List.of(children));
    }

    private LazyVGrid(Alignment alignment, Float spacing, Integer columns, List<GridItem> tracks, List<View> children) {
        this.children = List.copyOf(children);
        this.alignment = alignment;
        this.spacing = spacing;
        this.columns = columns;
        this.tracks = tracks == null ? null : List.copyOf(tracks);
    }

    /** A virtualized vertical grid; children are cells. */
    public static LazyVGrid of(View... children) {
        return new LazyVGrid(children);
    }

    /** A virtualized vertical grid with a fixed column count (equal `1fr` tracks). */
    public static LazyVGrid of(int columns, View... children) {
        return new LazyVGrid(columns, children);
    }

    /** A virtualized vertical grid with constructor layout properties. */
    public static LazyVGrid of(Alignment alignment, float spacing, View... children) {
        return new LazyVGrid(alignment, spacing, children);
    }

    /** A virtualized vertical grid with a fixed column count + constructor layout properties. */
    public static LazyVGrid of(int columns, Alignment alignment, float spacing, View... children) {
        return new LazyVGrid(columns, alignment, spacing, children);
    }

    /** A virtualized vertical grid with constructor layout properties. */
    public static LazyVGrid of(Alignment alignment, Float spacing, List<View> children) {
        return new LazyVGrid(alignment, spacing, null, null, children);
    }

    /** A virtualized vertical grid with per-track sizes (SwiftUI {@code GridItem}). */
    public static LazyVGrid of(List<GridItem> tracks, View... children) {
        return new LazyVGrid(tracks, children);
    }

    /** A virtualized vertical grid with per-track sizes + constructor layout properties. */
    public static LazyVGrid of(List<GridItem> tracks, Alignment alignment, float spacing, View... children) {
        return new LazyVGrid(tracks, alignment, spacing, children);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.LAZY_VGRID);
        if (alignment != null) {
            node.properties.put(Properties.ALIGNMENT, (float) alignment.wire());
        }
        if (spacing != null) {
            node.properties.put(Properties.SPACING, spacing);
        }
        if (tracks != null) {
            node.properties.put(Properties.GRID_TRACKS, joinTracks(tracks));
        }
        if (columns != null) {
            node.properties.put(Properties.GRID_COLUMNS, (float) columns);
        }
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }

    private static String joinTracks(List<GridItem> tracks) {
        StringBuilder sb = new StringBuilder();
        for (GridItem t : tracks) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(t.token());
        }
        return sb.toString();
    }
}