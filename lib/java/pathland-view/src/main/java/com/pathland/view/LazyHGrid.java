package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/** A virtualized horizontal grid (windowed realization of cells). */
public final class LazyHGrid implements View {

    private final List<View> children;
    private final Alignment alignment;
    private final Float spacing;
    private final Integer rows;
    private final List<GridItem> tracks;

    private LazyHGrid(View... children) {
        this(null, null, null, null, List.of(children));
    }

    private LazyHGrid(int rows, View... children) {
        this(null, null, rows, null, List.of(children));
    }

    private LazyHGrid(Alignment alignment, float spacing, View... children) {
        this(alignment, spacing, null, null, List.of(children));
    }

    private LazyHGrid(int rows, Alignment alignment, float spacing, View... children) {
        this(alignment, spacing, rows, null, List.of(children));
    }

    private LazyHGrid(List<GridItem> tracks, View... children) {
        this(null, null, null, tracks, List.of(children));
    }

    private LazyHGrid(List<GridItem> tracks, Alignment alignment, float spacing, View... children) {
        this(alignment, spacing, null, tracks, List.of(children));
    }

    private LazyHGrid(Alignment alignment, Float spacing, Integer rows, List<GridItem> tracks, List<View> children) {
        this.children = List.copyOf(children);
        this.alignment = alignment;
        this.spacing = spacing;
        this.rows = rows;
        this.tracks = tracks == null ? null : List.copyOf(tracks);
    }

    /** A virtualized horizontal grid; children are cells. */
    public static LazyHGrid of(View... children) {
        return new LazyHGrid(children);
    }

    /** A virtualized horizontal grid with a fixed row count (equal `1fr` rows). */
    public static LazyHGrid of(int rows, View... children) {
        return new LazyHGrid(rows, children);
    }

    /** A virtualized horizontal grid with constructor layout properties. */
    public static LazyHGrid of(Alignment alignment, float spacing, View... children) {
        return new LazyHGrid(alignment, spacing, children);
    }

    /** A virtualized horizontal grid with a fixed row count + constructor layout properties. */
    public static LazyHGrid of(int rows, Alignment alignment, float spacing, View... children) {
        return new LazyHGrid(rows, alignment, spacing, children);
    }

    /** A virtualized horizontal grid with constructor layout properties. */
    public static LazyHGrid of(Alignment alignment, Float spacing, List<View> children) {
        return new LazyHGrid(alignment, spacing, null, null, children);
    }

    /** A virtualized horizontal grid with per-track sizes ({@code GridItem}). */
    public static LazyHGrid of(List<GridItem> tracks, View... children) {
        return new LazyHGrid(tracks, children);
    }

    /** A virtualized horizontal grid with per-track sizes + constructor layout properties. */
    public static LazyHGrid of(List<GridItem> tracks, Alignment alignment, float spacing, View... children) {
        return new LazyHGrid(tracks, alignment, spacing, children);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.LAZY_HGRID);
        if (alignment != null) {
            node.properties.put(Properties.ALIGNMENT, (float) alignment.wire());
        }
        if (spacing != null) {
            node.properties.put(Properties.SPACING, spacing);
        }
        if (tracks != null) {
            node.properties.put(Properties.GRID_TRACKS, joinTracks(tracks));
        }
        if (rows != null) {
            node.properties.put(Properties.GRID_ROWS, (float) rows);
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