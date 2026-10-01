package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/** A virtualized horizontal grid (windowed realization of cells). */
public final class LazyHGrid implements View {

    private final List<View> children;
    private final Alignment alignment;
    private final Float spacing;
    private final Integer rows;

    private LazyHGrid(View... children) {
        this(null, null, null, List.of(children));
    }

    private LazyHGrid(int rows, View... children) {
        this(null, null, rows, List.of(children));
    }

    private LazyHGrid(Alignment alignment, float spacing, View... children) {
        this(alignment, spacing, null, List.of(children));
    }

    private LazyHGrid(int rows, Alignment alignment, float spacing, View... children) {
        this(alignment, spacing, rows, List.of(children));
    }

    private LazyHGrid(Alignment alignment, Float spacing, Integer rows, List<View> children) {
        this.children = List.copyOf(children);
        this.alignment = alignment;
        this.spacing = spacing;
        this.rows = rows;
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
        return new LazyHGrid(alignment, spacing, null, children);
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
        if (rows != null) {
            node.properties.put(Properties.GRID_ROWS, (float) rows);
        }
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}