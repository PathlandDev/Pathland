package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/** A virtualized horizontal stack (windowed realization of children). */
public final class LazyHStack implements View {

    private final List<View> children;
    private final VerticalAlignment alignment;
    private final Float spacing;

    private LazyHStack(View... children) {
        this(null, null, List.of(children));
    }

    private LazyHStack(VerticalAlignment alignment, float spacing, View... children) {
        this(alignment, spacing, List.of(children));
    }

    private LazyHStack(VerticalAlignment alignment, Float spacing, List<View> children) {
        this.children = List.copyOf(children);
        this.alignment = alignment;
        this.spacing = spacing;
    }

    /** A virtualized horizontal stack. */
    public static LazyHStack of(View... children) {
        return new LazyHStack(children);
    }

    /** A virtualized horizontal stack with constructor layout properties. */
    public static LazyHStack of(VerticalAlignment alignment, float spacing, View... children) {
        return new LazyHStack(alignment, spacing, children);
    }

    /** A virtualized horizontal stack with constructor layout properties. */
    public static LazyHStack of(VerticalAlignment alignment, Float spacing, List<View> children) {
        return new LazyHStack(alignment, spacing, children);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.LAZY_HSTACK);
        if (alignment != null) {
            node.properties.put(Properties.ALIGNMENT, (float) alignment.wire());
        }
        if (spacing != null) {
            node.properties.put(Properties.SPACING, spacing);
        }
        for (View child : children) {
            node.children.add(child.render(env));
        }
        return node;
    }
}