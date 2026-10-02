package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/** An explicit row grouping for a {@code Grid} (SwiftUI {@code GridRow}): the
 *  children are the cells of one row, placed left-to-right starting at column 0
 *  (spec/PRIMITIVES.md §GridRow). Structural only — renders nothing outside a
 *  {@code Grid}. */
public final class GridRow implements View {

    private final List<View> children;

    private GridRow(View... children) {
        this.children = List.of(children);
    }

    /** A grid row; children are the row's cells. */
    public static GridRow of(View... children) {
        return new GridRow(children);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.GRID_ROW);
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}