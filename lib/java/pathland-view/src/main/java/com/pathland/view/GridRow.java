package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/** An explicit row grouping for a {@code Grid} ({@code GridRow}): the
 *  children are the cells of one row, placed left-to-right starting at column 0
 *  (spec/PRIMITIVES.md §GridRow). Structural only — renders nothing outside a
 *  {@code Grid}. */
public final class GridRow implements View, Configurable<GridRow.Config>, ChildrenView {

    /** {@link GridRow} has no values. */
    public static final class Config implements View.Config {
    }

    private final Config config = new Config();
    private List<View> children = List.of();

    private GridRow(List<View> children) {
        this.children = children;
    }

    /** A grid row; children are the row's cells. */
    public static GridRow of(View... children) {
        return new GridRow(List.of(children));
    }

    /** Supply the row's cells. */
    public static ViewBuilder<GridRow, Config> children(View... children) {
        return new ViewBuilder<>(new GridRow(List.of())).children(children);
    }

    /** Apply modifiers to an empty row. */
    public static ViewBuilder<GridRow, Config> modifiers(ViewModifier... modifiers) {
        return new ViewBuilder<>(new GridRow(List.of())).modifiers(modifiers);
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public void setChildren(List<View> children) {
        this.children = List.copyOf(children);
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
