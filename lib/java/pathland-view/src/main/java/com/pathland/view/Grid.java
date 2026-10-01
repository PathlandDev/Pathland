package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/** A static 2D matrix grid (SwiftUI {@code Grid}); children are cells, row-major. */
public final class Grid implements View {

    private final List<View> children;
    private final Alignment alignment;
    private final Float spacing;
    private final Integer columns;
    private final Integer rows;

    private Grid(View... children) {
        this(null, null, null, null, List.of(children));
    }

    private Grid(int columns, View... children) {
        this(null, null, columns, null, List.of(children));
    }

    private Grid(Alignment alignment, float spacing, View... children) {
        this(alignment, spacing, null, null, List.of(children));
    }

    private Grid(int columns, Alignment alignment, float spacing, View... children) {
        this(alignment, spacing, columns, null, List.of(children));
    }

    private Grid(Alignment alignment, Float spacing, Integer columns, Integer rows, List<View> children) {
        this.children = List.copyOf(children);
        this.alignment = alignment;
        this.spacing = spacing;
        this.columns = columns;
        this.rows = rows;
    }

    /** A static 2D matrix grid; children are cells, row-major. */
    public static Grid of(View... children) {
        return new Grid(children);
    }

    /** A static 2D matrix grid with a fixed column count (equal `1fr` tracks). */
    public static Grid of(int columns, View... children) {
        return new Grid(columns, children);
    }

    /** A static 2D matrix grid with constructor layout properties. */
    public static Grid of(Alignment alignment, float spacing, View... children) {
        return new Grid(alignment, spacing, children);
    }

    /** A static 2D matrix grid with a fixed column count + constructor layout properties. */
    public static Grid of(int columns, Alignment alignment, float spacing, View... children) {
        return new Grid(columns, alignment, spacing, children);
    }

    /** A static 2D matrix grid with constructor layout properties. */
    public static Grid of(Alignment alignment, Float spacing, List<View> children) {
        return new Grid(alignment, spacing, null, null, children);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.GRID);
        if (alignment != null) {
            node.properties.put(Properties.ALIGNMENT, (float) alignment.wire());
        }
        if (spacing != null) {
            node.properties.put(Properties.SPACING, spacing);
        }
        if (columns != null) {
            node.properties.put(Properties.GRID_COLUMNS, (float) columns);
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