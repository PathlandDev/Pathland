package com.pathland.view;

import com.pathland.view.emit.PathlandNode;

import java.util.List;

/** A virtualized vertical stack (windowed realization of children). */
public final class LazyVStack implements View {

    private final List<View> children;
    private final HorizontalAlignment alignment;
    private final Float spacing;

    private LazyVStack(View... children) {
        this(null, null, List.of(children));
    }

    private LazyVStack(HorizontalAlignment alignment, float spacing, View... children) {
        this(alignment, spacing, List.of(children));
    }

    private LazyVStack(HorizontalAlignment alignment, Float spacing, List<View> children) {
        this.children = List.copyOf(children);
        this.alignment = alignment;
        this.spacing = spacing;
    }

    /** A virtualized vertical stack. */
    public static LazyVStack of(View... children) {
        return new LazyVStack(children);
    }

    /** A virtualized vertical stack with constructor layout properties. */
    public static LazyVStack of(HorizontalAlignment alignment, float spacing, View... children) {
        return new LazyVStack(alignment, spacing, children);
    }

    /** A virtualized vertical stack with constructor layout properties. */
    public static LazyVStack of(HorizontalAlignment alignment, Float spacing, List<View> children) {
        return new LazyVStack(alignment, spacing, children);
    }

    @Override
    public PathlandNode render(Environment env) {
        PathlandNode node = new PathlandNode(Components.LAZY_VSTACK);
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